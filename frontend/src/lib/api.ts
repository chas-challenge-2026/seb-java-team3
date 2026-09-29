import type { z } from "zod";
import { ApiError, ApiValidationError, NETWORK_ERROR_STATUS } from "../error/api.error";
import { getToken } from "./authToken";

const BASE_URL = import.meta.env.VITE_API_URL ?? "";

export async function api<T>(
  path: string,
  options: RequestInit = {},
  schema?: z.ZodType<T>,
): Promise<T> {
  const token = getToken();

  let res: Response;

  try {
    res = await fetch(`${BASE_URL}${path}`, {
      ...options,
      credentials: "omit",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
      },
    });
  } catch (error) {
    // Avbrutna anrop (AbortController) ska inte visas som fel för användaren
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }

    throw new ApiError(
      NETWORK_ERROR_STATUS,
      getDefaultErrorMessage(NETWORK_ERROR_STATUS),
    );
  }

  const isJson = res.headers.get("content-type")?.includes("application/json");
  const body = isJson ? await res.json().catch(() => null) : null;

  if (!res.ok) {
    const serverMessage = (body as { message?: unknown } | null)?.message;
    const message =
      typeof serverMessage === "string" && serverMessage.trim()
        ? serverMessage
        : getDefaultErrorMessage(res.status);
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

// Används när backend inte skickar något eget message, eller inte svarar alls
function getDefaultErrorMessage(status: number): string {
  switch (status) {
    case NETWORK_ERROR_STATUS:
    case 502:
    case 503:
    case 504:
      return "Kunde inte nå servern. Kontrollera din anslutning och försök igen om en stund.";
    case 401:
      return "Du har blivit utloggad. Logga in igen.";
    case 403:
      return "Du har inte behörighet att göra det här.";
    case 404:
      return "Det du letade efter kunde inte hittas.";
    default:
      return status >= 500
        ? "Något gick fel hos oss. Försök igen om en stund."
        : "Begäran kunde inte genomföras. Kontrollera uppgifterna och försök igen.";
  }
}
