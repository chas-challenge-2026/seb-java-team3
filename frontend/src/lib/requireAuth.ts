import { redirect } from "@tanstack/react-router";
import type { QueryClient } from "@tanstack/react-query";
import { userQueryOptions } from "./userQueryOptions";
import { clearToken, getToken } from "./authToken";
import { refreshSession } from "./authSession";
import { getDefaultErrorMessage } from "./api";
import { ApiError, NETWORK_ERROR_STATUS } from "../error/api.error";

// Servern gick inte att nå, eller svarade med 5xx. Det säger ingenting om sessionen, så användaren ska inte
// skickas till inloggningen: en ny inloggning skulle dessutom spärra den session som fortfarande gäller.
function isServerUnavailable(error: unknown): boolean {
  return error instanceof ApiError && (error.status === NETWORK_ERROR_STATUS || error.status >= 500);
}

function serverUnavailableError(): ApiError {
  return new ApiError(NETWORK_ERROR_STATUS, getDefaultErrorMessage(NETWORK_ERROR_STATUS));
}

// Kastar ett ApiError när servern inte går att nå. Routens felvy (AuthErrorView) visar det med ett nytt försök.
export async function requireAuth(queryClient: QueryClient) {
  if (!getToken()) {
    // Access-token finns bara i minnet (ADR 0012), så efter en omladdning saknas den. Försök förnya
    // sessionen tyst med refresh-cookien innan användaren skickas till inloggningen.
    const session = await refreshSession();

    if (session.status === "unreachable") {
      throw serverUnavailableError();
    }

    if (session.status !== "renewed") {
      queryClient.clear();
      throw redirect({ to: "/login" });
    }

    // Förnyelsen svarade redan med användaren, så vi slipper fråga /api/auth/me
    queryClient.setQueryData(userQueryOptions.queryKey, session.user);
    return session.user;
  }

  try {
    return await queryClient.ensureQueryData(userQueryOptions);
  } catch (error) {
    if (isServerUnavailable(error)) {
      throw serverUnavailableError();
    }

    clearToken();
    queryClient.clear();
    throw redirect({ to: "/login" });
  }
}
