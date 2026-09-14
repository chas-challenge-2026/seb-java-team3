import type { AuditApiEntry } from "./types";

export const mockAuditData: AuditApiEntry[] = [
  {
    id: 1042,
    action: "APPROVE_PAYMENT",
    entityType: "payment",
    entityId: 512,
    description: "Betalning godkänd och genomförd: 5000 SEK till SE4550000000058398257466",
    createdAt: "2026-09-05T14:32:10",
    userName: "Anna Andersson",
  },
  {
    id: 1041,
    action: "REJECT_PAYMENT",
    entityType: "payment",
    entityId: 509,
    description: "Betalning avvisad: 12000 SEK. Kommentar: Fel mottagarkonto",
    createdAt: "2026-09-05T13:58:44",
    userName: "Erik Svensson",
  },
  {
    id: 1040,
    action: "CREATE_PAYMENT",
    entityType: "payment",
    entityId: 512,
    description: "Ny betalning skapad: 5000 SEK till SE4550000000058398257466",
    createdAt: "2026-09-05T13:40:02",
    userName: "Anna Andersson",
  },
  {
    id: 1039,
    action: "BATCH_PAYMENT",
    entityType: "batch",
    entityId: 88,
    description: "Batchbetalning genomförd: 14 betalningar, totalt 245000 SEK",
    createdAt: "2026-09-04T09:15:30",
    userName: "Systemet",
  },
];
