import type { LoginInput, UserResponse } from "./types";
import { api } from "../../lib/api";

export function fetchLoginUser(input: LoginInput) {
  return api<UserResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function fetchCurrentUser() {
    return api<UserResponse>("/auth/me", {
    });
}