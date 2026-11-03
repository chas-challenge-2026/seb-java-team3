import { describe, expect, it } from "vitest";
import { z } from "zod";
import { zodIssuesToFieldErrors } from "./zodErrors";

describe("zodIssuesToFieldErrors", () => {
  const schema = z.object({
    email: z.string().min(1, "Ange e-post."),
    password: z.string().min(1, "Ange lösenord."),
  });

  it("maps each issue to its top-level field name", () => {
    const result = schema.safeParse({ email: "", password: "" });
    expect(result.success).toBe(false);

    const errors = zodIssuesToFieldErrors(result.error!);

    expect(errors).toEqual({
      email: "Ange e-post.",
      password: "Ange lösenord.",
    });
  });

  it("keeps only the first issue per field", () => {
    const withTwoRules = z.object({
      password: z.string().min(8, "För kort.").regex(/[0-9]/, "Måste innehålla siffra."),
    });

    const result = withTwoRules.safeParse({ password: "abc" });
    expect(result.success).toBe(false);

    const errors = zodIssuesToFieldErrors(result.error!);

    expect(errors.password).toBe("För kort.");
  });

  it("returns an empty object when there are no issues", () => {
    const result = schema.safeParse({ email: "a@b.com", password: "secret" });
    expect(result.success).toBe(true);
  });

  it("ignores issues without a string path segment", () => {
    const arraySchema = z.array(z.string().min(1, "Krävs."));
    const result = arraySchema.safeParse([""]);
    expect(result.success).toBe(false);

    const errors = zodIssuesToFieldErrors(result.error!);

    expect(errors).toEqual({});
  });
});
