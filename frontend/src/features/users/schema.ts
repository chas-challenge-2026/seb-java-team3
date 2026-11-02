import { z } from "zod";
import { loginInputSchema, userResponseSchema } from "../auth/schema";

// Roller som en admin får skapa. ADMIN skapas inte härifrån.
export const creatableRoleSchema = z.enum(["ATTESTANT", "INITIATOR"], {
  message: "Välj en roll.",
});

export type CreatableRole = z.infer<typeof creatableRoleSchema>;

export const createUserFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Ange namn.")
    .max(100, "Namnet får vara högst 100 tecken."),
  email: loginInputSchema.shape.email,
  role: creatableRoleSchema,
  password: z
    .string()
    .min(8, "Startlösenordet måste vara minst 8 tecken.")
    .max(72, "Startlösenordet får vara högst 72 tecken."),
});

export type CreateUserInput = z.infer<typeof createUserFormSchema>;

export const userListSchema = z.array(userResponseSchema);
