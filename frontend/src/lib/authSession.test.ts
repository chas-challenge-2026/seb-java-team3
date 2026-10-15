import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { logoutSession, onUserChanged, refreshSession } from "./authSession";
import { clearToken, getToken, getTokenUserId, setToken } from "./authToken";

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

const sessionBody = {
  token: "new-access-token",
  id: 1,
  name: "Ida Initiator",
  email: "ida@example.com",
  role: "INITIATOR",
};

describe("authSession", () => {
  beforeEach(() => {
    clearToken();
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(navigator, "locks");
  });

  describe("refreshSession", () => {
    it("calls /api/auth/refresh with the cookie and the required header, and stores the new token", async () => {
      vi.mocked(fetch).mockResolvedValue(jsonResponse(200, sessionBody));

      const result = await refreshSession();

      expect(result).toEqual({
        status: "renewed",
        user: { id: 1, name: "Ida Initiator", email: "ida@example.com", role: "INITIATOR" },
      });
      expect(getToken()).toBe("new-access-token");
      expect(getTokenUserId()).toBe(1);

      const [url, init] = vi.mocked(fetch).mock.calls[0];
      expect(url).toBe("/api/auth/refresh");
      expect(init?.method).toBe("POST");
      expect(init?.credentials).toBe("same-origin");
      expect((init?.headers as Record<string, string>)["X-Requested-With"]).toBe("fetch");
    });

    it("never puts the token in the returned user", async () => {
      vi.mocked(fetch).mockResolvedValue(jsonResponse(200, sessionBody));

      const result = await refreshSession();

      expect(result.status).toBe("renewed");
      expect(result.status === "renewed" && result.user).not.toHaveProperty("token");
    });

    it("returns null and clears the token when the server has no valid session", async () => {
      setToken("old-token");
      vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { error: "Session expired" }));

      await expect(refreshSession()).resolves.toEqual({ status: "no-session" });

      expect(getToken()).toBeNull();
    });

    it("returns null and clears the token when the response has an unexpected shape", async () => {
      setToken("old-token");
      vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { unexpected: true }));

      await expect(refreshSession()).resolves.toEqual({ status: "no-session" });

      expect(getToken()).toBeNull();
    });

    it("reports unreachable but keeps an existing token when the server cannot be reached", async () => {
      setToken("still-maybe-valid");
      vi.mocked(fetch).mockRejectedValue(new TypeError("Failed to fetch"));

      await expect(refreshSession()).resolves.toEqual({ status: "unreachable" });

      expect(getToken()).toBe("still-maybe-valid");
    });

    it.each([500, 502, 503, 504])(
      "reports unreachable and keeps the token when the server answers %i (restart or proxy error)",
      async (status) => {
        setToken("still-maybe-valid");
        vi.mocked(fetch).mockResolvedValue(new Response("Bad Gateway", { status }));

        await expect(refreshSession()).resolves.toEqual({ status: "unreachable" });

        expect(getToken()).toBe("still-maybe-valid");
      },
    );

    it("gives up after a time limit, so a hanging server cannot hold the cross-tab lock", async () => {
      vi.mocked(fetch).mockResolvedValue(jsonResponse(200, sessionBody));

      await refreshSession();

      const [, init] = vi.mocked(fetch).mock.calls[0];
      expect(init?.signal).toBeInstanceOf(AbortSignal);
    });

    // Cookien delas mellan flikarna: har någon loggat in som en annan användare i en annan flik, svarar
    // förnyelsen med den användaren. Fliken får då inte fortsätta som den nya användaren.
    describe("when the session now belongs to another user", () => {
      it("reports user-changed, keeps the old token and does not store the new one", async () => {
        setToken("lisas-expired-token", 2);
        vi.mocked(fetch).mockResolvedValue(jsonResponse(200, sessionBody));

        await expect(refreshSession()).resolves.toEqual({ status: "user-changed" });

        expect(getToken()).toBe("lisas-expired-token");
        expect(getTokenUserId()).toBe(2);
      });

      it("tells the listeners, so that the app can reload", async () => {
        const listener = vi.fn();
        const unsubscribe = onUserChanged(listener);
        setToken("lisas-expired-token", 2);
        vi.mocked(fetch).mockResolvedValue(jsonResponse(200, sessionBody));

        await refreshSession();
        unsubscribe();

        expect(listener).toHaveBeenCalledTimes(1);
      });

      it("renews as usual when the session still belongs to the same user", async () => {
        const listener = vi.fn();
        const unsubscribe = onUserChanged(listener);
        setToken("idas-expired-token", 1);
        vi.mocked(fetch).mockResolvedValue(jsonResponse(200, sessionBody));

        await expect(refreshSession()).resolves.toMatchObject({ status: "renewed" });
        unsubscribe();

        expect(listener).not.toHaveBeenCalled();
        expect(getToken()).toBe("new-access-token");
      });
    });

    it("shares one request between simultaneous refreshes", async () => {
      vi.mocked(fetch).mockResolvedValue(jsonResponse(200, sessionBody));

      const [first, second] = await Promise.all([refreshSession(), refreshSession()]);

      // Två förnyelser med samma token räknas som återanvändning av servern och loggar ut användaren
      expect(fetch).toHaveBeenCalledTimes(1);
      expect(first).toEqual(second);
    });

    it("makes a new request for a refresh that starts after the first one has finished", async () => {
      vi.mocked(fetch).mockImplementation(async () => jsonResponse(200, sessionBody));

      await refreshSession();
      await refreshSession();

      expect(fetch).toHaveBeenCalledTimes(2);
    });

    it("takes the cross-tab lock before it calls the server", async () => {
      const events: string[] = [];
      Object.defineProperty(navigator, "locks", {
        configurable: true,
        value: {
          request: vi.fn(async (name: string, task: () => Promise<unknown>) => {
            events.push(`lock:${name}`);
            return task();
          }),
        },
      });
      vi.mocked(fetch).mockImplementation(async () => {
        events.push("fetch");
        return jsonResponse(200, sessionBody);
      });

      await refreshSession();

      expect(events).toEqual(["lock:seb-refresh-session", "fetch"]);
    });

    it("still works when the browser has no Web Locks", async () => {
      expect(navigator.locks).toBeUndefined();
      vi.mocked(fetch).mockResolvedValue(jsonResponse(200, sessionBody));

      await expect(refreshSession()).resolves.toMatchObject({ status: "renewed", user: { id: 1 } });
    });
  });

  describe("logoutSession", () => {
    it("posts to /api/auth/logout with the cookie and the required header, and clears the token", async () => {
      setToken("access-token");
      vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));

      await logoutSession();

      const [url, init] = vi.mocked(fetch).mock.calls[0];
      expect(url).toBe("/api/auth/logout");
      expect(init?.method).toBe("POST");
      expect(init?.credentials).toBe("same-origin");
      expect((init?.headers as Record<string, string>)["X-Requested-With"]).toBe("fetch");
      expect(getToken()).toBeNull();
    });

    it("clears the token and does not throw when the server cannot be reached", async () => {
      setToken("access-token");
      vi.mocked(fetch).mockRejectedValue(new TypeError("Failed to fetch"));

      await expect(logoutSession()).resolves.toBeUndefined();

      expect(getToken()).toBeNull();
    });

    it("gives up after a time limit, so a hanging server cannot block logging out", async () => {
      vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));

      await logoutSession();

      const [, init] = vi.mocked(fetch).mock.calls[0];
      expect(init?.signal).toBeInstanceOf(AbortSignal);
    });

    it("logs out locally when the server does not answer in time", async () => {
      setToken("access-token");
      vi.mocked(fetch).mockRejectedValue(new DOMException("The operation timed out.", "TimeoutError"));

      await expect(logoutSession()).resolves.toBeUndefined();

      expect(getToken()).toBeNull();
    });
  });
});
