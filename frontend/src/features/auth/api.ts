import type { LoginInput } from "./types";
import { loginResponseSchema, userResponseSchema } from "./schema";
import { api } from "../../lib/api";

export function fetchLoginUser(input: LoginInput) {
  return api(
    "/api/auth/login",
    {
      method: "POST",
      body: JSON.stringify(input),
    },
    loginResponseSchema,
  );
}

export function fetchCurrentUser() {
  return api("/api/auth/me", {}, userResponseSchema);
}