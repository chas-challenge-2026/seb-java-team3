import { describe, expect, it } from "vitest";
import { createUserFormSchema } from "./schema";

const valid = {
  name: "Kalle Kula",
  email: "kalle@foretag.se",
  role: "INITIATOR",
  password: "hemligt123",
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

  it("kräver minst 8 tecken i lösenordet", () => {
    expect(createUserFormSchema.safeParse({ ...valid, password: "kort" }).success).toBe(false);
  });
});
