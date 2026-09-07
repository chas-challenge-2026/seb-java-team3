import { useQuery } from "@tanstack/react-query";
import { userQueryOptions } from "../../lib/userQueryOptions";

export function useUser() {
  return useQuery(userQueryOptions);
}
