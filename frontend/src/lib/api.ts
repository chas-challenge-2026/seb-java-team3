import type { z } from "zod";
import { ApiError, ApiValidationError, NETWORK_ERROR_STATUS } from "../error/api.error";
import { getToken } from "./authToken";
import { refreshSession } from "./authSession";

const BASE_URL = import.meta.env.VITE_API_URL ?? "";

// Endpoints som själva hanterar sessionen (ADR 0012). De är de enda som skickar och tar emot cookies
// (refresh-token), och de ska inte själva försöka förnya sessionen när de får 401: ett fel lösenord vid
// inloggning betyder fel lösenord, inte en utgången session. Övrigt API använder bara Bearer och
// `credentials: "omit"`, som i ADR 0009.
const SESSION_PATHS = ["/api/auth/login", "/api/auth/refresh", "/api/auth/logout"];

function isSessionPath(path: string): boolean {
  return SESSION_PATHS.includes(path);
}

export async function api<T>(
  path: string,
  options: RequestInit = {},
  schema?: z.ZodType<T>,
): Promise<T> {
  let res = await send(path, options);

  // Access-token har gått ut: förnya sessionen tyst en gång och försök igen. 401 betyder att anropet
  // avvisades innan något utfördes, så ett nytt försök är säkert även för POST.
  if (res.status === 401 && !isSessionPath(path)) {
    const session = await refreshSession();

    if (session) {
      res = await send(path, options);
    }
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

// Skickar anropet med den access-token som finns just nu. Läses vid varje anrop, så ett nytt försök
// efter en förnyelse använder den nya token.
async function send(path: string, options: RequestInit): Promise<Response> {
  const token = getToken();

  try {
    return await fetch(`${BASE_URL}${path}`, {
      ...options,
      credentials: isSessionPath(path) ? "same-origin" : "omit",
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
