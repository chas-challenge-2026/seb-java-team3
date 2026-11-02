import { ApiError } from "../../error/api.error";
import { userListSchema } from "./schema";
import type { CreatableRole, CreateUserInput } from "./schema";
import type { UserResponse } from "../auth/types";

// MOCK: backend för användaradministration finns inte än. Funktionerna nedan har samma signaturer som
// de riktiga anropen ska få, så vid kopplingen byts bara innehållet ut mot:
//   listUsers():  api("/api/admin/users", {}, userListSchema)
//   createUser(): api("/api/admin/users", { method: "POST", body: JSON.stringify(input) }, userResponseSchema)
//   updateUserRole(): api(`/api/admin/users/${id}/role`, { method: "PUT", body: JSON.stringify({ role }) }, userResponseSchema)
// (sökvägarna är ett antagande, justera efter backendens kontrakt)

const MOCK_DELAY_MS = 500;

let nextId = 5;
let mockUsers: UserResponse[] = [
  { id: 1, name: "Anna Admin", email: "anna.admin@malmobygg.se", role: "ADMIN" },
  { id: 2, name: "Erik Eriksson", email: "erik.eriksson@malmobygg.se", role: "INITIATOR" },
  { id: 3, name: "Sara Svensson", email: "sara.svensson@malmobygg.se", role: "ATTESTANT" },
  { id: 4, name: "Johan Lind", email: "johan.lind@malmobygg.se", role: "INITIATOR" },
];

const delay = () => new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));

export async function listUsers(): Promise<UserResponse[]> {
  await delay();
  return userListSchema.parse(mockUsers);
}

export async function createUser(input: CreateUserInput): Promise<UserResponse> {
  await delay();

  const email = input.email.trim().toLowerCase();

  if (mockUsers.some((user) => user.email.toLowerCase() === email)) {
    throw new ApiError(409, "E-postadressen används redan.");
  }

  const user: UserResponse = {
    id: nextId++,
    name: input.name.trim(),
    email,
    role: input.role,
  };
  mockUsers = [...mockUsers, user];

  return user;
}

export async function updateUserRole(id: number, role: CreatableRole): Promise<UserResponse> {
  await delay();

  const existing = mockUsers.find((user) => user.id === id);

  if (!existing) {
    throw new ApiError(404, "Användaren finns inte.");
  }

  // Det finns bara ett adminkonto och dess roll får inte ändras
  if (existing.role === "ADMIN") {
    throw new ApiError(403, "Administratörens roll kan inte ändras.");
  }

  const updated: UserResponse = { ...existing, role };
  mockUsers = mockUsers.map((user) => (user.id === id ? updated : user));

  return updated;
}
