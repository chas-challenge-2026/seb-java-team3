import type { AuditApiEntry } from "./types";

export const mockAuditData: AuditApiEntry[] = [
  {
    id: 1042,
    action: "APPROVE_PAYMENT",
    entity_type: "payment",
    entity_id: 512,
    description: "Betalning godkänd och genomförd: 5000 SEK till SE4550000000058398257466",
    created_at: "2026-09-05T14:32:10",
    user_name: "Anna Andersson",
  },
  {
    id: 1041,
    action: "REJECT_PAYMENT",
    entity_type: "payment",
    entity_id: 509,
    description: "Betalning avvisad: 12000 SEK. Kommentar: Fel mottagarkonto",
    created_at: "2026-09-05T13:58:44",
    user_name: "Erik Svensson",
  },
  {
    id: 1040,
    action: "CREATE_PAYMENT",
    entity_type: "payment",
    entity_id: 512,
    description: "Ny betalning skapad: 5000 SEK till SE4550000000058398257466",
    created_at: "2026-09-05T13:40:02",
    user_name: "Anna Andersson",
  },
  {
    id: 1039,
    action: "BATCH_PAYMENT",
    entity_type: "batch",
    entity_id: 88,
    description: "Batchbetalning genomförd: 14 betalningar, totalt 245000 SEK",
    created_at: "2026-09-04T09:15:30",
    user_name: "Systemet",
  },
];
