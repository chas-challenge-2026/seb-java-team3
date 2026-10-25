import { describe, expect, it } from "vitest";
import { auditApiEntrySchema } from "../schema";
import { toAuditEntry } from "./mappers";

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
