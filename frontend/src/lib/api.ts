import { ApiError } from "../error/api.error";
import { getToken } from "./authToken";

const BASE_URL = import.meta.env.VITE_API_URL ?? "";

export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getToken();

  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    credentials: "include",
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

  return body as T;
}
