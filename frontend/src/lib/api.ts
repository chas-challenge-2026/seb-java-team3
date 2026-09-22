import type { z } from "zod";
import { ApiError, ApiValidationError } from "../error/api.error";
import { getToken } from "./authToken";

const BASE_URL = import.meta.env.VITE_API_URL ?? "";

export async function api<T>(
  path: string,
  options: RequestInit = {},
  schema?: z.ZodType<T>,
): Promise<T> {
  const token = getToken();

  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    credentials: "omit",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  const isJson = res.headers.get("content-type")?.includes("application/json");
  const body = isJson ? await res.json().catch(() => null) : null;

  if (!res.ok) {
    const message =
      (body as { message?: string })?.message ??
      `${res.status} ${res.statusText}`;
    throw new ApiError(res.status, message, body);
  }

  if (!schema) {
    return body as T;
  }

  const result = schema.safeParse(body);

  if (!result.success) {
    if (import.meta.env.DEV) {
      console.error(`Unexpected response shape from ${path}`, result.error.issues);
    }

    throw new ApiValidationError(
      "Svaret från servern hade ett oväntat format.",
      result.error.issues,
    );
  }

  return result.data;
}
