import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useLogout } from "./useLogout";
import { logoutSession } from "../../lib/authSession";

const navigate = vi.fn();

vi.mock("@tanstack/react-router", () => ({
  useNavigate: () => navigate,
}));

vi.mock("../../lib/authSession", () => ({
  logoutSession: vi.fn(),
}));

const mockedLogoutSession = vi.mocked(logoutSession);

describe("useLogout", () => {
  let queryClient: QueryClient;

  function wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }

  beforeEach(() => {
    navigate.mockReset();
    mockedLogoutSession.mockReset();
    queryClient = new QueryClient();
  });

  it("ends the session on the server, clears the cache and goes to the login page", async () => {
    mockedLogoutSession.mockResolvedValue();
    queryClient.setQueryData(["auth", "me"], { id: 1 });
    const { result } = renderHook(() => useLogout(), { wrapper });

    await result.current();

    expect(mockedLogoutSession).toHaveBeenCalledTimes(1);
    expect(queryClient.getQueryData(["auth", "me"])).toBeUndefined();
    expect(navigate).toHaveBeenCalledWith({ to: "/login" });
  });

  it("navigates away before it clears the cache, so pages still shown do not refetch without a token", async () => {
    mockedLogoutSession.mockResolvedValue();
    queryClient.setQueryData(["auth", "me"], { id: 1 });
    let cachedUserWhenNavigating: unknown;
    navigate.mockImplementation(async () => {
      cachedUserWhenNavigating = queryClient.getQueryData(["auth", "me"]);
    });
    const { result } = renderHook(() => useLogout(), { wrapper });

    await result.current();

    expect(cachedUserWhenNavigating).toEqual({ id: 1 });
    expect(queryClient.getQueryData(["auth", "me"])).toBeUndefined();
  });

  it("waits for the server before it clears the cache and navigates", async () => {
    let finishLogout: () => void = () => {};
    mockedLogoutSession.mockReturnValue(
      new Promise<void>((resolve) => {
        finishLogout = resolve;
      }),
    );
    queryClient.setQueryData(["auth", "me"], { id: 1 });
    const { result } = renderHook(() => useLogout(), { wrapper });

    const pending = result.current();

    // Servern har inte svarat än: ingenting har hänt i gränssnittet
    expect(navigate).not.toHaveBeenCalled();
    expect(queryClient.getQueryData(["auth", "me"])).toEqual({ id: 1 });

    finishLogout();
    await pending;

    expect(navigate).toHaveBeenCalledWith({ to: "/login" });
    expect(queryClient.getQueryData(["auth", "me"])).toBeUndefined();
  });
});
