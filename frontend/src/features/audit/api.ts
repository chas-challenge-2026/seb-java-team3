import type { AuditApiEntry } from "./types";
import { api } from "../../lib/api";

export function fetchAuditEntries() {
  return api<AuditApiEntry[]>("/api/audit");
}
