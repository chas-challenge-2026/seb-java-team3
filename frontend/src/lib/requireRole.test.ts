import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import { isRedirect } from "@tanstack/react-router";
import { requireRole } from "./requireRole";
import { fetchCurrentUser } from "../features/auth/api";
import type { UserResponse, UserRole } from "../features/auth/types";

vi.mock("../features/auth/api", () => ({
  fetchCurrentUser: vi.fn(),
}));

const mockedFetchCurrentUser = vi.mocked(fetchCurrentUser);

function userWithRole(role: UserRole): UserResponse {
  return { id: 1, name: "Test Testsson", email: "test@example.com", role };
}

// Samma rollistor som routerna i router.tsx använder.
const ATTEST_ROLES: UserRole[] = ["ATTESTANT", "ADMIN"];
const PAYMENT_ROLES: UserRole[] = ["INITIATOR", "ADMIN"];

describe("requireRole", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    mockedFetchCurrentUser.mockReset();
    queryClient = new QueryClient();
  });

  it.each([
    { role: "INITIATOR" as const, allowed: ATTEST_ROLES },
    { role: "ATTESTANT" as const, allowed: PAYMENT_ROLES },
  ])("sends $role back to / when the route only allows $allowed", async ({ role, allowed }) => {
    mockedFetchCurrentUser.mockResolvedValue(userWithRole(role));

    const thrown = await requireRole(queryClient, allowed).then(
      () => expect.fail("expected a redirect"),
      (error: unknown) => error,
    );

    expect(isRedirect(thrown)).toBe(true);
    expect((thrown as { options: { to: string } }).options.to).toBe("/");
  });

  it.each([
    { role: "ATTESTANT" as const, allowed: ATTEST_ROLES },
    { role: "ADMIN" as const, allowed: ATTEST_ROLES },
    { role: "INITIATOR" as const, allowed: PAYMENT_ROLES },
    { role: "ADMIN" as const, allowed: PAYMENT_ROLES },
  ])("lets $role through when the route allows $allowed", async ({ role, allowed }) => {
    const user = userWithRole(role);
    mockedFetchCurrentUser.mockResolvedValue(user);

    await expect(requireRole(queryClient, allowed)).resolves.toEqual(user);
  });
});
