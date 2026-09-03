import { redirect } from "@tanstack/react-router";
import type { QueryClient } from "@tanstack/react-query";
import { userQueryOptions } from "./userQueryOptions";

export async function requireAuth(queryClient: QueryClient) {
  try {
    return await queryClient.ensureQueryData(userQueryOptions);
  } catch {
    throw redirect({ to: "/login" });
  }
}