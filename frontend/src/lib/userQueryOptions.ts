import { queryOptions } from "@tanstack/react-query";
import { fetchCurrentUser } from "../features/auth/api";

export const userQueryOptions = queryOptions({
  queryKey: ["auth", "me"],
  queryFn: fetchCurrentUser,
  retry: false,
});
