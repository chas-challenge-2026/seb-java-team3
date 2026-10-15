import { describe, expect, it } from "vitest";
import { toAuditEntry } from "./mappers";
import type { AuditApiEntry } from "../types";

function apiEntry(overrides: Partial<AuditApiEntry>): AuditApiEntry {
  return {
    id: 1,
    action: "CREATE_PAYMENT",
    entityType: "PAYMENT",
    entityId: 100,
    description: "Betalning skapad",
    status: "COMPLETED",
    reference: "Demo",
    amount: 4500,
    currency: "SEK",
    createdAt: "2026-10-15T08:00:00",
    userName: "Lisa Persson",
    fromAccountName: "Driftkonto",
    autoApproved: false,
    ...overrides,
  };
}

describe("toAuditEntry", () => {
  it("shows a payment under the threshold as automatically approved", () => {
    expect(toAuditEntry(apiEntry({ autoApproved: true })).handelse).toBe("Automatiskt godkänd");
  });

  it("keeps 'Betalning skapad' for a payment that needs approval", () => {
    const entry = toAuditEntry(apiEntry({ status: "PENDING_APPROVAL", autoApproved: false }));

    expect(entry.handelse).toBe("Betalning skapad");
  });

  it("only renames the creation event, not a later approval", () => {
    expect(toAuditEntry(apiEntry({ action: "APPROVE_PAYMENT", autoApproved: true })).handelse).toBe(
      "Betalning godkänd",
    );
  });

  it("shows which account the payment is drawn from", () => {
    expect(toAuditEntry(apiEntry({})).franKonto).toBe("Driftkonto");
    expect(toAuditEntry(apiEntry({ fromAccountName: null })).franKonto).toBe("-");
  });

  it("keeps the raw status for the filter", () => {
    expect(toAuditEntry(apiEntry({ status: "REJECTED" })).statusKey).toBe("REJECTED");
  });
});
