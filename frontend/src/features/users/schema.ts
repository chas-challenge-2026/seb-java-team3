import { z } from "zod";
import { loginInputSchema, userResponseSchema } from "../auth/schema";

// Roller som en admin får skapa. ADMIN skapas inte härifrån.
export const creatableRoleSchema = z.enum(["ATTESTANT", "INITIATOR"], {
  message: "Välj en roll.",
});

export type CreatableRole = z.infer<typeof creatableRoleSchema>;

// Samma gränser som backend (ADR 0017). Backend är fortfarande den som avgör.
export const PASSWORD_MIN_LENGTH = 12;
const PASSWORD_MAX_BYTES = 72; // BCrypt ignorerar allt efter 72 byte

export const createUserFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Ange namn.")
    .max(100, "Namnet får vara högst 100 tecken."),
  email: loginInputSchema.shape.email.max(100, "E-postadressen får vara högst 100 tecken."),
  role: creatableRoleSchema,
  password: z
    .string()
    .min(PASSWORD_MIN_LENGTH, `Startlösenordet måste vara minst ${PASSWORD_MIN_LENGTH} tecken.`)
    .refine(
      (value) => new TextEncoder().encode(value).length <= PASSWORD_MAX_BYTES,
      "Startlösenordet är för långt.",
    ),
});

export type CreateUserInput = z.infer<typeof createUserFormSchema>;

export const userListSchema = z.array(userResponseSchema);
