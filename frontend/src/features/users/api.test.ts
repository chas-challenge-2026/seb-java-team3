import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../lib/api";
import { createUser, deleteUser, listUsers, updateUserRole } from "./api";

vi.mock("../../lib/api", () => ({ api: vi.fn() }));

describe("users api", () => {
  beforeEach(() => {
    vi.mocked(api).mockReset();
  });

  it("lists users from the admin endpoint", async () => {
    await listUsers();

    expect(api).toHaveBeenCalledWith("/api/admin/users", {}, expect.anything());
  });

  it("creates a user with exactly the fields the backend accepts, and no company id", async () => {
    await createUser({ name: "Kalle Kula", email: "kalle@foretag.se", role: "INITIATOR", password: "hemligt-lösen-123" });

    expect(api).toHaveBeenCalledWith(
      "/api/admin/users",
      {
        method: "POST",
        body: JSON.stringify({
          name: "Kalle Kula",
          email: "kalle@foretag.se",
          role: "INITIATOR",
          password: "hemligt-lösen-123",
        }),
      },
      expect.anything(),
    );
  });

  it("deletes a user with DELETE on the user's path", async () => {
    await deleteUser(7);

    expect(api).toHaveBeenCalledWith("/api/admin/users/7", { method: "DELETE" });
  });

  it("changes a role with PATCH on the user's role path", async () => {
    await updateUserRole(7, "ATTESTANT");

    expect(api).toHaveBeenCalledWith(
      "/api/admin/users/7/role",
      { method: "PATCH", body: JSON.stringify({ role: "ATTESTANT" }) },
      expect.anything(),
    );
  });
});
