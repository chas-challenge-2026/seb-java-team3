import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createUser, deleteUser, listUsers, updateUserRole } from "./api";
import { ApiError } from "../../error/api.error";

// Mock-API:t har en fördröjning och delat tillstånd mellan anrop, så varje test använder egna användare
async function settled<T>(promise: Promise<T>): Promise<T> {
  await vi.advanceTimersByTimeAsync(1000);
  return promise;
}

describe("users mock api", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("creates a user that then shows up in the list", async () => {
    const created = await settled(
      createUser({ name: "  Nina Ny ", email: "Nina.Ny@foretag.se", role: "ATTESTANT", password: "hemligt123" }),
    );

    expect(created).toMatchObject({ name: "Nina Ny", email: "nina.ny@foretag.se", role: "ATTESTANT" });
    expect(await settled(listUsers())).toContainEqual(created);
  });

  it("rejects a duplicate email with 409, ignoring case", async () => {
    await settled(createUser({ name: "Först", email: "dubblett@foretag.se", role: "INITIATOR", password: "hemligt123" }));

    const second = createUser({ name: "Andra", email: "DUBBLETT@foretag.se", role: "INITIATOR", password: "hemligt123" });
    const assertion = expect(second).rejects.toMatchObject({ status: 409 });
    await vi.advanceTimersByTimeAsync(1000);
    await assertion;
  });

  it("changes the role of a non-admin user", async () => {
    const created = await settled(
      createUser({ name: "Rolf Roll", email: "rolf@foretag.se", role: "INITIATOR", password: "hemligt123" }),
    );

    const updated = await settled(updateUserRole(created.id, "ATTESTANT"));

    expect(updated.role).toBe("ATTESTANT");
    expect((await settled(listUsers())).find((user) => user.id === created.id)?.role).toBe("ATTESTANT");
  });

  it("never changes or deletes the admin account", async () => {
    const adminUser = (await settled(listUsers())).find((user) => user.role === "ADMIN");
    expect(adminUser).toBeDefined();

    const roleChange = updateUserRole(adminUser!.id, "INITIATOR");
    const roleAssertion = expect(roleChange).rejects.toBeInstanceOf(ApiError);
    await vi.advanceTimersByTimeAsync(1000);
    await roleAssertion;

    const removal = deleteUser(adminUser!.id);
    const removalAssertion = expect(removal).rejects.toMatchObject({ status: 403 });
    await vi.advanceTimersByTimeAsync(1000);
    await removalAssertion;
  });

  it("deletes a user permanently", async () => {
    const created = await settled(
      createUser({ name: "Dan Delete", email: "dan@foretag.se", role: "INITIATOR", password: "hemligt123" }),
    );

    await settled(deleteUser(created.id));

    expect((await settled(listUsers())).some((user) => user.id === created.id)).toBe(false);
  });
});
