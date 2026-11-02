import { queryOptions } from "@tanstack/react-query";
import { listUsers } from "./api";

export const usersQueryKey = ["users"] as const;

export const usersQueryOptions = queryOptions({
  queryKey: usersQueryKey,
  queryFn: listUsers,
});
