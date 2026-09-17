import { redirect } from "@tanstack/react-router";
import type { QueryClient } from "@tanstack/react-query";
import { userQueryOptions } from "./userQueryOptions";
import { clearToken, getToken } from "./authToken";

export async function requireAuth(queryClient: QueryClient) {
  if (!getToken()) {
    queryClient.clear();
    throw redirect({ to: "/login" });
  }

  try {
    return await queryClient.ensureQueryData(userQueryOptions);
  } catch {
    clearToken();
    queryClient.clear();
    throw redirect({ to: "/login" });
  }
}
