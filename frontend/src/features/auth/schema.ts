import { z } from "zod";

export const loginInputSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, "Ange din e-postadress.")
    .email("Ange en giltig e-postadress."),
  password: z.string().min(1, "Ange ditt lösenord."),
});

export type LoginInput = z.infer<typeof loginInputSchema>;

export const userRoleSchema = z.enum(["ADMIN", "ATTESTANT", "INITIATOR"]);

export type UserRole = z.infer<typeof userRoleSchema>;

export const userResponseSchema = z.object({
  id: z.number(),
  name: z.string(),
  email: z.string(),
  role: userRoleSchema,
});

export type UserResponse = z.infer<typeof userResponseSchema>;

export const loginResponseSchema = userResponseSchema.extend({
  token: z.string(),
});

export type LoginResponse = z.infer<typeof loginResponseSchema>;
