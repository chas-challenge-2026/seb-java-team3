import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { api } from "./api";
import { ApiError, ApiValidationError, NETWORK_ERROR_STATUS } from "../error/api.error";
import { clearToken, setToken } from "./authToken";

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("api", () => {
  beforeEach(() => {
    clearToken();
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns the parsed JSON body on success", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { hello: "world" }));

    const result = await api<{ hello: string }>("/api/ping");

    expect(result).toEqual({ hello: "world" });
  });

  it("sends the bearer token when one is stored", async () => {
    setToken("secret-token");
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, {}));

    await api("/api/ping");

    const [, init] = vi.mocked(fetch).mock.calls[0];
    const headers = init?.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer secret-token");
  });

  it("omits the Authorization header when no token is stored", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, {}));

    await api("/api/ping");

    const [, init] = vi.mocked(fetch).mock.calls[0];
    const headers = init?.headers as Record<string, string>;
    expect(headers.Authorization).toBeUndefined();
  });

  it("throws ApiError with the server message on a non-ok response", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(400, { message: "Ogiltig begäran." }),
    );

    await expect(api("/api/ping")).rejects.toMatchObject({
      name: "ApiError",
      status: 400,
      message: "Ogiltig begäran.",
    });
  });

  it("falls back to a Swedish default message when the error body has no message", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(null, { status: 500, statusText: "Internal Server Error" }),
    );

    await expect(api("/api/ping")).rejects.toMatchObject({
      name: "ApiError",
      status: 500,
      message: "Något gick fel hos oss. Försök igen om en stund.",
    });
  });

  it("ignores an empty server message", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { error: "Unauthorized", message: "" }));

    await expect(api("/api/ping")).rejects.toMatchObject({
      status: 401,
      message: "Du har blivit utloggad. Logga in igen.",
    });
  });

  it("uses the unreachable message for 502 from the proxy", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("", { status: 502, statusText: "Bad Gateway" }),
    );

    await expect(api("/api/ping")).rejects.toMatchObject({
      status: 502,
      message: expect.stringContaining("Kunde inte nå servern"),
    });
  });

  it("turns network failures into an ApiError with status 0", async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(api("/api/ping")).rejects.toMatchObject({
      name: "ApiError",
      status: NETWORK_ERROR_STATUS,
      message: expect.stringContaining("Kunde inte nå servern"),
    });
  });

  it("rethrows aborted requests unchanged", async () => {
    const abortError = new DOMException("Aborted", "AbortError");
    vi.mocked(fetch).mockRejectedValue(abortError);

    await expect(api("/api/ping")).rejects.toBe(abortError);
  });

  it("returns the schema-parsed data when a schema is provided", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(200, { count: 3, extra: "ignored" }),
    );

    const schema = z.object({ count: z.number() });
    const result = await api("/api/count", {}, schema);

    expect(result).toEqual({ count: 3 });
  });

  it("throws ApiValidationError when the response doesn't match the schema", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { count: "not-a-number" }));

    const schema = z.object({ count: z.number() });

    await expect(api("/api/count", {}, schema)).rejects.toBeInstanceOf(
      ApiValidationError,
    );
  });

  it("does not attempt schema validation on error responses", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(404, { message: "Hittades inte" }));

    const schema = z.object({ count: z.number() });

    await expect(api("/api/count", {}, schema)).rejects.toBeInstanceOf(ApiError);
  });

  // ADR 0012: en utgången access-token förnyas tyst med refresh-cookien, och anropet görs om en gång.
  describe("session refresh on 401", () => {
    const sessionBody = {
      token: "new-access-token",
      id: 1,
      name: "Ida Initiator",
      email: "ida@example.com",
      role: "INITIATOR",
    };

    function authorizationOfCall(index: number): string | undefined {
      const [, init] = vi.mocked(fetch).mock.calls[index];
      return (init?.headers as Record<string, string>).Authorization;
    }

    it("refreshes the session once and retries the request with the new token", async () => {
      setToken("expired-token");
      vi.mocked(fetch)
        .mockResolvedValueOnce(jsonResponse(401, {}))
        .mockResolvedValueOnce(jsonResponse(200, sessionBody))
        .mockResolvedValueOnce(jsonResponse(200, { ok: true }));

      const result = await api<{ ok: boolean }>("/api/ping");

      expect(result).toEqual({ ok: true });
      expect(vi.mocked(fetch).mock.calls.map(([url]) => url)).toEqual([
        "/api/ping",
        "/api/auth/refresh",
        "/api/ping",
      ]);
      expect(authorizationOfCall(0)).toBe("Bearer expired-token");
      expect(authorizationOfCall(2)).toBe("Bearer new-access-token");
    });

    it("retries a POST too, since a 401 means nothing was carried out", async () => {
      setToken("expired-token");
      vi.mocked(fetch)
        .mockResolvedValueOnce(jsonResponse(401, {}))
        .mockResolvedValueOnce(jsonResponse(200, sessionBody))
        .mockResolvedValueOnce(jsonResponse(201, { id: 7 }));

      const result = await api<{ id: number }>("/api/payments", {
        method: "POST",
        body: JSON.stringify({ amount: 100 }),
      });

      expect(result).toEqual({ id: 7 });
      const [, retryInit] = vi.mocked(fetch).mock.calls[2];
      expect(retryInit?.method).toBe("POST");
      expect(retryInit?.body).toBe(JSON.stringify({ amount: 100 }));
    });

    it("gives up with the original 401 when the session cannot be refreshed", async () => {
      setToken("expired-token");
      vi.mocked(fetch)
        .mockResolvedValueOnce(jsonResponse(401, {}))
        .mockResolvedValueOnce(jsonResponse(401, { error: "Session expired" }));

      await expect(api("/api/ping")).rejects.toMatchObject({ name: "ApiError", status: 401 });

      expect(fetch).toHaveBeenCalledTimes(2);
    });

    it("retries only once, so a second 401 is returned as an error and never loops", async () => {
      setToken("expired-token");
      vi.mocked(fetch)
        .mockResolvedValueOnce(jsonResponse(401, {}))
        .mockResolvedValueOnce(jsonResponse(200, sessionBody))
        .mockResolvedValueOnce(jsonResponse(401, {}));

      await expect(api("/api/ping")).rejects.toMatchObject({ status: 401 });

      expect(fetch).toHaveBeenCalledTimes(3);
    });

    it("does not try to refresh when the login itself returns 401", async () => {
      vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { error: "Invalid email or password" }));

      await expect(
        api("/api/auth/login", { method: "POST", body: "{}" }),
      ).rejects.toMatchObject({ status: 401 });

      expect(fetch).toHaveBeenCalledTimes(1);
    });

    it("shares one refresh between requests that expire at the same time", async () => {
      setToken("expired-token");
      let refreshCalls = 0;
      vi.mocked(fetch).mockImplementation(async (url, init) => {
        if (String(url) === "/api/auth/refresh") {
          refreshCalls += 1;
          return jsonResponse(200, sessionBody);
        }
        const authorization = (init?.headers as Record<string, string>).Authorization;
        return authorization === "Bearer new-access-token"
          ? jsonResponse(200, { ok: true })
          : jsonResponse(401, {});
      });

      const [first, second] = await Promise.all([api("/api/one"), api("/api/two")]);

      expect(first).toEqual({ ok: true });
      expect(second).toEqual({ ok: true });
      expect(refreshCalls).toBe(1);
    });

    it("retries without a new refresh when another request already renewed the token", async () => {
      setToken("expired-token");
      vi.mocked(fetch).mockImplementation(async (_url, init) => {
        const authorization = (init?.headers as Record<string, string>).Authorization;
        if (authorization === "Bearer expired-token") {
          // Ett annat anrop hinner förnya sessionen medan det här är på väg
          setToken("token-from-other-request", 1);
          return jsonResponse(401, {});
        }
        return jsonResponse(200, { ok: true });
      });

      await expect(api("/api/ping")).resolves.toEqual({ ok: true });

      expect(vi.mocked(fetch).mock.calls.map(([url]) => url)).toEqual(["/api/ping", "/api/ping"]);
      expect(authorizationOfCall(1)).toBe("Bearer token-from-other-request");
    });

    it("reports the server as unreachable, not as logged out, when the refresh gets a 503", async () => {
      setToken("expired-token");
      vi.mocked(fetch)
        .mockResolvedValueOnce(jsonResponse(401, {}))
        .mockResolvedValueOnce(new Response("Service Unavailable", { status: 503 }));

      await expect(api("/api/ping")).rejects.toMatchObject({
        name: "ApiError",
        status: NETWORK_ERROR_STATUS,
        message: "Kunde inte nå servern. Kontrollera din anslutning och försök igen om en stund.",
      });

      expect(fetch).toHaveBeenCalledTimes(2);
    });

    it("does not retry as another user when someone logged in as someone else in another tab", async () => {
      setToken("lisas-expired-token", 2);
      vi.mocked(fetch)
        .mockResolvedValueOnce(jsonResponse(401, {}))
        .mockResolvedValueOnce(jsonResponse(200, sessionBody));

      await expect(
        api("/api/payments", { method: "POST", body: "{}" }),
      ).rejects.toMatchObject({ name: "ApiError", status: 401 });

      // Bara det ursprungliga anropet och förnyelsen: betalningen skickas aldrig om som Ida
      expect(vi.mocked(fetch).mock.calls.map(([url]) => url)).toEqual([
        "/api/payments",
        "/api/auth/refresh",
      ]);
    });

    it("sends cookies only to the session endpoints", async () => {
      vi.mocked(fetch).mockResolvedValue(jsonResponse(200, {}));

      await api("/api/auth/login", { method: "POST", body: "{}" });
      await api("/api/payments");

      const [, loginInit] = vi.mocked(fetch).mock.calls[0];
      const [, paymentsInit] = vi.mocked(fetch).mock.calls[1];
      expect(loginInit?.credentials).toBe("same-origin");
      expect(paymentsInit?.credentials).toBe("omit");
    });
  });
});
