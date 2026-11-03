import { describe, expect, it } from "vitest";
import { createUserFormSchema } from "./schema";

const valid = {
  name: "Kalle Kula",
  email: "kalle@foretag.se",
  role: "INITIATOR",
  password: "hemligt-lösen-123",
};

describe("createUserFormSchema", () => {
  it("accepterar giltiga värden", () => {
    expect(createUserFormSchema.safeParse(valid).success).toBe(true);
  });

  it("kräver namn, e-post, roll och lösenord", () => {
    const result = createUserFormSchema.safeParse({ name: "", email: "", role: "", password: "" });
    expect(result.success).toBe(false);
    expect(result.error?.issues.map((issue) => issue.path[0])).toEqual(
      expect.arrayContaining(["name", "email", "role", "password"]),
    );
  });

  it("avvisar ogiltig e-post", () => {
    expect(createUserFormSchema.safeParse({ ...valid, email: "inte-en-epost" }).success).toBe(false);
  });

  it("tillåter inte ADMIN som roll", () => {
    expect(createUserFormSchema.safeParse({ ...valid, role: "ADMIN" }).success).toBe(false);
  });

  it("kräver minst 12 tecken i lösenordet", () => {
    expect(createUserFormSchema.safeParse({ ...valid, password: "elvatecken1" }).success).toBe(false);
    expect(createUserFormSchema.safeParse({ ...valid, password: "tolvtecken12" }).success).toBe(true);
  });

  it("avvisar lösenord över 72 byte, räknat i byte och inte tecken", () => {
    expect(createUserFormSchema.safeParse({ ...valid, password: "a".repeat(72) }).success).toBe(true);
    expect(createUserFormSchema.safeParse({ ...valid, password: "a".repeat(73) }).success).toBe(false);
    // 40 tecken men 80 byte
    expect(createUserFormSchema.safeParse({ ...valid, password: "å".repeat(40) }).success).toBe(false);
  });

  it("avvisar namn och e-post över 100 tecken", () => {
    expect(createUserFormSchema.safeParse({ ...valid, name: "n".repeat(101) }).success).toBe(false);
    expect(createUserFormSchema.safeParse({ ...valid, email: `${"a".repeat(95)}@x.se` }).success).toBe(true); // 100 tecken
    expect(createUserFormSchema.safeParse({ ...valid, email: `${"a".repeat(96)}@x.se` }).success).toBe(false); // 101 tecken
  });
});
