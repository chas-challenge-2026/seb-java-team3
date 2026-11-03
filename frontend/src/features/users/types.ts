export type { UserResponse, UserRole } from "../auth/types";
export type { CreatableRole, CreateUserInput } from "./schema";

export interface CreateUserFormData {
  name: string;
  email: string;
  role: string;
  password: string;
}

export interface CreateUserFormErrors {
  name?: string;
  email?: string;
  role?: string;
  password?: string;
}
