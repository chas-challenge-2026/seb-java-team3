import { describe, expect, it } from "vitest";
import { auditApiEntrySchema } from "../schema";
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
    balanceBeforePayment: 10000,
    balanceAfterPayment: 5500,
    fromAccountName: "Driftkonto",
    autoApproved: false,
    comment: null,
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

const entry = auditApiEntrySchema.parse({
  id: 1, action: "APPROVE_PAYMENT", entityType: "PAYMENT", entityId: 10,
  description: "Betalning godkänd", status: "COMPLETED", reference: "Faktura",
  amount: 250, currency: "SEK", createdAt: "2026-10-21T10:00:00", userName: "Johan",
  balanceBeforePayment: 1000, balanceAfterPayment: 750,
});

describe("audit balance history", () => {
  it("shows the historical balances alongside the payment amount", () => {
    const mapped = toAuditEntry(entry);
    expect(mapped.saldoFore).toMatch(/^1\s000,00 SEK$/);
    expect(mapped.saldoEfter).toBe("750,00 SEK");
    expect(mapped.belopp).toBe("250,00 SEK");
  });

  it("shows zero as a recorded balance", () => {
    expect(toAuditEntry({ ...entry, balanceAfterPayment: 0 }).saldoEfter).toBe("0,00 SEK");
  });

  it("identifies missing historical balances for older completed payments", () => {
    const mapped = toAuditEntry({ ...entry, balanceBeforePayment: null, balanceAfterPayment: null });
    expect(mapped.saldoFore).toBe("Saknas i historiken");
    expect(mapped.saldoEfter).toBe("Saknas i historiken");
  });

  it("does not claim a pending or rejected payment has been debited", () => {
    for (const status of ["PENDING_APPROVAL", "REJECTED"]) {
      const mapped = toAuditEntry({ ...entry, status, balanceBeforePayment: null, balanceAfterPayment: null });
      expect(mapped.saldoFore).toBe("Ej genomförd");
      expect(mapped.saldoEfter).toBe("Ej genomförd");
    }
  });
});
