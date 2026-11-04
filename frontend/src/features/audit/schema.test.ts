import { describe, expect, it } from "vitest";
import { auditApiEntrySchema, paymentAuditTimelineEntrySchema } from "./schema";

// Zod tar bort fält som schemat inte känner till, så utan `comment` i schemat når attestantens meddelande
// aldrig gränssnittet även om backend skickar det (#228).
const timelineEntry = {
  order: 2,
  sequence: "AUDIT-2",
  actor: "Johan Berg",
  eventType: "REJECT_PAYMENT",
  description: "Betalning avvisad. Kommentar: Fel konto",
  timestamp: "2026-10-29T10:00:00",
  stepNumber: null,
  status: "REJECTED",
  reference: "Faktura 4711",
  amount: 6000,
  currency: "SEK",
  toIban: "SE8550000000054910000003",
};

const auditEntry = {
  id: 2,
  action: "REJECT_PAYMENT",
  entityType: "PAYMENT",
  entityId: 100,
  description: "Betalning avvisad. Kommentar: Fel konto",
  status: "REJECTED",
  reference: "Faktura 4711",
  amount: 6000,
  currency: "SEK",
  createdAt: "2026-10-29T10:00:00",
  userName: "Johan Berg",
};

describe("paymentAuditTimelineEntrySchema", () => {
  it("keeps the attestant's message", () => {
    expect(paymentAuditTimelineEntrySchema.parse({ ...timelineEntry, comment: "Fel konto" }).comment)
      .toBe("Fel konto");
  });

  it("has no message when backend sends null or nothing", () => {
    expect(paymentAuditTimelineEntrySchema.parse({ ...timelineEntry, comment: null }).comment).toBeNull();
    expect(paymentAuditTimelineEntrySchema.parse(timelineEntry).comment).toBeNull();
  });
});

describe("auditApiEntrySchema", () => {
  it("keeps the attestant's message", () => {
    expect(auditApiEntrySchema.parse({ ...auditEntry, comment: "Fel konto" }).comment).toBe("Fel konto");
  });

  it("has no message when backend sends null or nothing", () => {
    expect(auditApiEntrySchema.parse({ ...auditEntry, comment: null }).comment).toBeNull();
    expect(auditApiEntrySchema.parse(auditEntry).comment).toBeNull();
  });
});
