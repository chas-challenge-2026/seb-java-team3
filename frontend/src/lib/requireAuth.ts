import { redirect } from "@tanstack/react-router";
import type { QueryClient } from "@tanstack/react-query";
import { userQueryOptions } from "./userQueryOptions";
import { clearToken, getToken } from "./authToken";
import { refreshSession } from "./authSession";

export async function requireAuth(queryClient: QueryClient) {
  if (!getToken()) {
    // Access-token finns bara i minnet (ADR 0012), så efter en omladdning saknas den. Försök förnya
    // sessionen tyst med refresh-cookien innan användaren skickas till inloggningen.
    const user = await refreshSession();

    if (!user) {
      queryClient.clear();
      throw redirect({ to: "/login" });
    }

    // Förnyelsen svarade redan med användaren, så vi slipper fråga /api/auth/me
    queryClient.setQueryData(userQueryOptions.queryKey, user);
    return user;
  }

  try {
    return await queryClient.ensureQueryData(userQueryOptions);
  } catch {
    clearToken();
    queryClient.clear();
    throw redirect({ to: "/login" });
  }
}
