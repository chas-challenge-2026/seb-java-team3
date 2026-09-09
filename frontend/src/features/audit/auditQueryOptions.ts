import { queryOptions } from "@tanstack/react-query";
import { fetchAuditEntries } from "./api";

export const auditQueryOptions = queryOptions({
  queryKey: ["audit", "entries"],
  queryFn: fetchAuditEntries,
});
