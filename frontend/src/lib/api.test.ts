import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { api } from "./api";
import { ApiError, ApiValidationError } from "../error/api.error";
import { setToken } from "./authToken";

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("api", () => {
  beforeEach(() => {
    localStorage.clear();
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

  it("falls back to the status text when the error body has no message", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(null, { status: 500, statusText: "Internal Server Error" }),
    );

    await expect(api("/api/ping")).rejects.toMatchObject({
      name: "ApiError",
      status: 500,
      message: "500 Internal Server Error",
    });
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
});
