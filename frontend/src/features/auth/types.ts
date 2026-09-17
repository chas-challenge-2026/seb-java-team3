export type LoginInput = {
    email: string;
    password: string;
}

export type UserRole = "ADMIN" | "ATTESTANT" | "INITIATOR";

export type UserResponse = {
  id: number;
  name: string;
  email: string;
  role: UserRole;
};

export type LoginResponse = UserResponse & {
  token: string;
};
