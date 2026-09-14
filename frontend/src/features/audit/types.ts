
export type AuditEntry = {
  id: string;
  tid: string;
  vem: string;
  handelse: string;
  betalning: string;
};

export type AuditApiEntry = {
  id: number;
  action: string;
  entityType: string;
  entityId: number;
  description: string;
  createdAt: string;
  userName: string;
};

export function toAuditEntry(raw: AuditApiEntry): AuditEntry {
  return {
    id: String(raw.id),
    tid: new Date(raw.createdAt).toLocaleString("sv-SE", {
      dateStyle: "short",
      timeStyle: "short",
    }),
    vem: raw.userName,
    handelse: raw.description,
    betalning: `${raw.entityType} #${raw.entityId}`,
  };
}
