import type { LoginInput, LoginResponse, UserResponse } from "./types";
import { api } from "../../lib/api";

export function fetchLoginUser(input: LoginInput) {
  return api<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function fetchCurrentUser() {
    return api<UserResponse>("/api/auth/me", {
    });
}