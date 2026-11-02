import { api } from "../../lib/api";
import { userListSchema } from "./schema";
import { userResponseSchema } from "../auth/schema";
import type { CreatableRole, CreateUserInput } from "./schema";

// Användaradministration för ADMIN (ADR 0017). Företaget kommer från admins token i backend,
// så inget företags-id skickas härifrån.

export async function listUsers() {
  return api("/api/admin/users", {}, userListSchema);
}

export async function createUser(input: CreateUserInput) {
  return api(
    "/api/admin/users",
    {
      method: "POST",
      body: JSON.stringify(input),
    },
    userResponseSchema,
  );
}

// 204 utan innehåll. Backend vägrar (409) om användaren har betalningar eller atteststeg kopplade till sig,
// och (403) om det är en administratör.
export async function deleteUser(id: number) {
  await api(`/api/admin/users/${id}`, { method: "DELETE" });
}

export async function updateUserRole(id: number, role: CreatableRole) {
  return api(
    `/api/admin/users/${id}/role`,
    {
      method: "PATCH",
      body: JSON.stringify({ role }),
    },
    userResponseSchema,
  );
}
