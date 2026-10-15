import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import { isRedirect } from "@tanstack/react-router";
import { requireAuth } from "./requireAuth";
import { clearToken, getToken, setToken } from "./authToken";
import { refreshSession } from "./authSession";
import { userQueryOptions } from "./userQueryOptions";
import { fetchCurrentUser } from "../features/auth/api";
import { ApiError, NETWORK_ERROR_STATUS } from "../error/api.error";
import type { UserResponse } from "../features/auth/types";

vi.mock("../features/auth/api", () => ({
  fetchCurrentUser: vi.fn(),
}));

vi.mock("./authSession", () => ({
  refreshSession: vi.fn(),
}));

const mockedFetchCurrentUser = vi.mocked(fetchCurrentUser);
const mockedRefreshSession = vi.mocked(refreshSession);

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
    clearToken();
    mockedFetchCurrentUser.mockReset();
    mockedRefreshSession.mockReset();
    queryClient = new QueryClient();
  });

  // ADR 0012: efter en omladdning finns ingen access-token i minnet, men refresh-cookien kan ge en ny.
  describe("without an access token in memory (for example after a reload)", () => {
    it("redirects to /login without asking /me when the session cannot be renewed", async () => {
      mockedRefreshSession.mockResolvedValue({ status: "no-session" });

      await expectRedirectTo(requireAuth(queryClient), "/login");

      expect(mockedRefreshSession).toHaveBeenCalledTimes(1);
      expect(mockedFetchCurrentUser).not.toHaveBeenCalled();
    });

    it("renews the session silently and lets the user through", async () => {
      mockedRefreshSession.mockResolvedValue({ status: "renewed", user });

      await expect(requireAuth(queryClient)).resolves.toEqual(user);
    });

    it("seeds the user in the query cache, so /me is not requested again", async () => {
      mockedRefreshSession.mockResolvedValue({ status: "renewed", user });

      await requireAuth(queryClient);

      expect(queryClient.getQueryData(userQueryOptions.queryKey)).toEqual(user);
      expect(mockedFetchCurrentUser).not.toHaveBeenCalled();
    });

    it("clears the cached user when the session cannot be renewed", async () => {
      queryClient.setQueryData(userQueryOptions.queryKey, user);
      mockedRefreshSession.mockResolvedValue({ status: "no-session" });

      await expectRedirectTo(requireAuth(queryClient), "/login");

      expect(queryClient.getQueryData(userQueryOptions.queryKey)).toBeUndefined();
    });
  });

  describe("when the server cannot be reached", () => {
    it("does not send the user to /login when the session could not be checked", async () => {
      queryClient.setQueryData(userQueryOptions.queryKey, user);
      mockedRefreshSession.mockResolvedValue({ status: "unreachable" });

      const thrown = await requireAuth(queryClient).then(
        () => expect.fail("expected an error"),
        (error: unknown) => error,
      );

      expect(isRedirect(thrown)).toBe(false);
      expect(thrown).toMatchObject({ name: "ApiError", status: NETWORK_ERROR_STATUS });
      expect(queryClient.getQueryData(userQueryOptions.queryKey)).toEqual(user);
    });

    it("keeps the token when /me fails because the server is down", async () => {
      setToken("valid-token");
      mockedFetchCurrentUser.mockRejectedValue(new ApiError(503, "Service Unavailable"));

      await expect(requireAuth(queryClient)).rejects.toMatchObject({ status: NETWORK_ERROR_STATUS });

      expect(getToken()).toBe("valid-token");
    });
  });

  describe("with an access token in memory", () => {
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

    it("does not try to renew the session when a token already exists", async () => {
      setToken("valid-token");
      mockedFetchCurrentUser.mockResolvedValue(user);

      await requireAuth(queryClient);

      expect(mockedRefreshSession).not.toHaveBeenCalled();
    });
  });
});
