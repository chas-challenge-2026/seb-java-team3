import type { LoginInput, UserResponse } from "./types";
import { api } from "../../lib/api";

export function fetchLoginUser(input: LoginInput) {
  return api<UserResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
}
