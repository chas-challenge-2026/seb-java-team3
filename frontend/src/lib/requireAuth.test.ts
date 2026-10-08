import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import { isRedirect } from "@tanstack/react-router";
import { requireAuth } from "./requireAuth";
import { getToken, setToken } from "./authToken";
import { fetchCurrentUser } from "../features/auth/api";
import { ApiError } from "../error/api.error";
import type { UserResponse } from "../features/auth/types";

vi.mock("../features/auth/api", () => ({
  fetchCurrentUser: vi.fn(),
}));

const mockedFetchCurrentUser = vi.mocked(fetchCurrentUser);

const user: UserResponse = { id: 1, name: "Ida Initiator", email: "ida@example.com", role: "INITIATOR" };

async function expectRedirectTo(promise: Promise<unknown>, to: string) {
  const thrown = await promise.then(
    () => expect.fail("expected a redirect"),
    (error: unknown) => error,
  );
  expect(isRedirect(thrown)).toBe(true);
  expect((thrown as { options: { to: string } }).options.to).toBe(to);
}

describe("requireAuth", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    localStorage.clear();
    mockedFetchCurrentUser.mockReset();
    queryClient = new QueryClient();
  });

  it("redirects to /login without asking the backend when there is no token", async () => {
    await expectRedirectTo(requireAuth(queryClient), "/login");

    expect(mockedFetchCurrentUser).not.toHaveBeenCalled();
  });

  it("redirects to /login and clears the token when the backend rejects it", async () => {
    setToken("expired-token");
    mockedFetchCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));

    await expectRedirectTo(requireAuth(queryClient), "/login");

    expect(getToken()).toBeNull();
  });

  it("lets a logged-in user through and returns the user", async () => {
    setToken("valid-token");
    mockedFetchCurrentUser.mockResolvedValue(user);

    await expect(requireAuth(queryClient)).resolves.toEqual(user);
  });
});
