import type { AuditEntry } from "./AuditGrid";
export type AuditApiEntry = {
  id: number;
  action: string;
  entity_type: string;
  entity_id: number;
  description: string;
  created_at: string;
  user_name: string;
};

export function toAuditEntry(raw: AuditApiEntry): AuditEntry {
  return {
    id: String(raw.id),
    tid: new Date(raw.created_at).toLocaleString("sv-SE", {
      dateStyle: "short",
      timeStyle: "short",
    }),
    vem: raw.user_name,
    handelse: raw.description,
    betalning: `${raw.entity_type} #${raw.entity_id}`,
  };
}
