import type { z } from "zod";
import { ApiError, ApiValidationError, NETWORK_ERROR_STATUS } from "../error/api.error";
import { getToken } from "./authToken";
import { refreshSession } from "./authSession";
import { BASE_URL } from "./baseUrl";

// Inloggningen är det enda anrop via api() som tar emot en cookie (refresh-token, ADR 0012). Den ska inte
// heller försöka förnya sessionen vid 401: fel lösenord betyder fel lösenord, inte en utgången session.
// /refresh och /logout går inte via api(), se authSession.ts. Övrigt API använder bara Bearer och
// `credentials: "omit"`, som i ADR 0009.
const LOGIN_PATH = "/api/auth/login";

export async function api<T>(
  path: string,
  options: RequestInit = {},
  schema?: z.ZodType<T>,
): Promise<T> {
  const sentToken = getToken();
  let res = await send(path, options, sentToken);

  // Access-token har gått ut: förnya sessionen tyst en gång och försök igen. 401 betyder att anropet
  // avvisades innan något utfördes, så ett nytt försök är säkert även för POST.
  if (res.status === 401 && path !== LOGIN_PATH) {
    const currentToken = getToken();

    if (currentToken && currentToken !== sentToken) {
      // Ett annat anrop hann förnya sessionen medan det här var på väg. Försök igen med den nya token,
      // i stället för att rotera refresh-token en gång till.
      res = await send(path, options, currentToken);
    } else {
      const session = await refreshSession();

      if (session.status === "renewed") {
        res = await send(path, options, getToken());
      } else if (session.status === "unreachable") {
        // Sessionen kan fortfarande vara giltig: säg inte att användaren är utloggad
        throw new ApiError(NETWORK_ERROR_STATUS, getDefaultErrorMessage(NETWORK_ERROR_STATUS));
      } else if (session.status === "user-changed") {
        // Anropet får inte göras om som en annan användare. Appen laddas om (se main.tsx).
        throw new ApiError(401, "Någon har loggat in som en annan användare i en annan flik. Sidan laddas om.");
      }
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

// Skickar anropet med den access-token som anroparen anger. api() håller reda på vilken token som
// skickades, så att den vid 401 kan se om en annan förfrågan redan har förnyat sessionen.
async function send(path: string, options: RequestInit, token: string | null): Promise<Response> {
  try {
    return await fetch(`${BASE_URL}${path}`, {
      ...options,
      credentials: path === LOGIN_PATH ? "same-origin" : "omit",
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
export function getDefaultErrorMessage(status: number): string {
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
