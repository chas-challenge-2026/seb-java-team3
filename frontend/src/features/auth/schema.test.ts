import { describe, expect, it } from "vitest";
import { loginInputSchema, userResponseSchema } from "./schema";

describe("loginInputSchema", () => {
  it("accepts a valid email and non-empty password", () => {
    const result = loginInputSchema.safeParse({
      email: "user@example.com",
      password: "hunter2",
    });

    expect(result.success).toBe(true);
  });

  it("trims the email before validating", () => {
    const result = loginInputSchema.safeParse({
      email: "  user@example.com  ",
      password: "hunter2",
    });

    expect(result.success).toBe(true);
    expect(result.data?.email).toBe("user@example.com");
  });

  it("rejects an invalid email format", () => {
    const result = loginInputSchema.safeParse({
      email: "not-an-email",
      password: "hunter2",
    });

    expect(result.success).toBe(false);
    expect(result.error?.issues[0].path).toEqual(["email"]);
  });

  it("rejects an empty email", () => {
    const result = loginInputSchema.safeParse({ email: "", password: "hunter2" });

    expect(result.success).toBe(false);
  });

  it("rejects an empty password", () => {
    const result = loginInputSchema.safeParse({
      email: "user@example.com",
      password: "",
    });

    expect(result.success).toBe(false);
    expect(result.error?.issues[0].path).toEqual(["password"]);
  });
});

describe("userResponseSchema", () => {
  it("accepts a well-formed user", () => {
    const result = userResponseSchema.safeParse({
      id: 1,
      name: "Ada",
      email: "ada@example.com",
      role: "ADMIN",
    });

    expect(result.success).toBe(true);
  });

  it("rejects an unknown role", () => {
    const result = userResponseSchema.safeParse({
      id: 1,
      name: "Ada",
      email: "ada@example.com",
      role: "SUPERUSER",
    });

    expect(result.success).toBe(false);
  });
});
