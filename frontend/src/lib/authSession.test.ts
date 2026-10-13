import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { logoutSession, refreshSession } from "./authSession";
import { clearToken, getToken, setToken } from "./authToken";

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

      const user = await refreshSession();

      expect(user).toEqual({ id: 1, name: "Ida Initiator", email: "ida@example.com", role: "INITIATOR" });
      expect(getToken()).toBe("new-access-token");

      const [url, init] = vi.mocked(fetch).mock.calls[0];
      expect(url).toBe("/api/auth/refresh");
      expect(init?.method).toBe("POST");
      expect(init?.credentials).toBe("same-origin");
      expect((init?.headers as Record<string, string>)["X-Requested-With"]).toBe("fetch");
    });

    it("never puts the token in the returned user", async () => {
      vi.mocked(fetch).mockResolvedValue(jsonResponse(200, sessionBody));

      const user = await refreshSession();

      expect(user).not.toHaveProperty("token");
    });

    it("returns null and clears the token when the server has no valid session", async () => {
      setToken("old-token");
      vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { error: "Session expired" }));

      await expect(refreshSession()).resolves.toBeNull();

      expect(getToken()).toBeNull();
    });

    it("returns null and clears the token when the response has an unexpected shape", async () => {
      setToken("old-token");
      vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { unexpected: true }));

      await expect(refreshSession()).resolves.toBeNull();

      expect(getToken()).toBeNull();
    });

    it("returns null but keeps an existing token when the server cannot be reached", async () => {
      setToken("still-maybe-valid");
      vi.mocked(fetch).mockRejectedValue(new TypeError("Failed to fetch"));

      await expect(refreshSession()).resolves.toBeNull();

      expect(getToken()).toBe("still-maybe-valid");
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

      await expect(refreshSession()).resolves.toMatchObject({ id: 1 });
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
  });
});
