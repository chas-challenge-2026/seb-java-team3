import { redirect } from "@tanstack/react-router";
import type { QueryClient } from "@tanstack/react-query";
import { userQueryOptions } from "./userQueryOptions";
import type { UserRole } from "../features/auth/types";

export async function requireRole(queryClient: QueryClient, allowedRoles: UserRole[]) {
  const user = await queryClient.ensureQueryData(userQueryOptions);

  if (!allowedRoles.includes(user.role)) {
    throw redirect({ to: "/" });
  }

  return user;
}
