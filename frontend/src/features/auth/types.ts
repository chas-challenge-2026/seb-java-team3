export type LoginInput = {
    email: string;
    password: string;
}

export type UserResponse = {
 email: string;
};

export type LoginResponse = UserResponse & {
  token: string;
};