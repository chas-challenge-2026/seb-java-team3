import { describe, expect, it } from "vitest";
import { formatAttestant, formatRejectionReason, formatTimelineComment } from "./formatters";
import type { MyPaymentApprovalStep, MyPaymentStatus, PaymentAuditTimelineEntry } from "../schema";

function payment(status: string, approvalSteps: MyPaymentApprovalStep[]): MyPaymentStatus {
  return {
    paymentId: 1,
    reference: "Demo",
    amount: 7500,
    currency: "SEK",
    fromAccountName: "Driftkonto",
    toIban: "SE4550000000058398257466",
    status,
    createdAt: "2026-10-15T08:00:00",
    executedAt: null,
    approvalSteps,
  };
}

function step(stepNumber: number, attestantName: string, status: string): MyPaymentApprovalStep {
  return { stepNumber, attestantName, status, decidedAt: null, comment: null };
}

describe("formatAttestant", () => {
  it("names the attestant a pending payment is waiting for", () => {
    expect(formatAttestant(payment("PENDING_APPROVAL", [step(1, "Johan Berg", "PENDING")]))).toBe("Johan Berg");
  });

  it("names the second attestant once the first has approved", () => {
    const pending = payment("PENDING_APPROVAL", [
      step(2, "Vera VD", "PENDING"),
      step(1, "Johan Berg", "APPROVED"),
    ]);

    expect(formatAttestant(pending)).toBe("Vera VD");
  });

  it("names who approved a completed payment", () => {
    expect(formatAttestant(payment("COMPLETED", [step(1, "Johan Berg", "APPROVED")]))).toBe("Johan Berg");
  });

  it("lists every approver of a payment that needed two, in step order", () => {
    const completed = payment("COMPLETED", [
      step(2, "Vera VD", "APPROVED"),
      step(1, "Johan Berg", "APPROVED"),
    ]);

    expect(formatAttestant(completed)).toBe("Johan Berg, Vera VD");
  });

  it("names who rejected a payment", () => {
    expect(formatAttestant(payment("REJECTED", [step(1, "Johan Berg", "REJECTED")]))).toBe("Johan Berg");
  });

  it("shows a dash for a payment without approval steps", () => {
    expect(formatAttestant(payment("COMPLETED", []))).toBe("-");
  });
});

describe("formatRejectionReason", () => {
  it("shows why the attestant rejected the payment", () => {
    const rejected = payment("REJECTED", [{ ...step(1, "Johan Berg", "REJECTED"), comment: "Otillräckligt saldo" }]);

    expect(formatRejectionReason(rejected)).toBe("Otillräckligt saldo");
  });

  it("has no reason when the payment was not rejected or no reason was given", () => {
    expect(formatRejectionReason(payment("PENDING_APPROVAL", [step(1, "Johan Berg", "PENDING")]))).toBeNull();
    expect(formatRejectionReason(payment("REJECTED", [step(1, "Johan Berg", "REJECTED")]))).toBeNull();
  });
});

function timelineEntry(eventType: string, comment: string | null): PaymentAuditTimelineEntry {
  return {
    order: 1,
    sequence: "AUDIT-1",
    actor: "Johan Berg",
    eventType,
    description: "Betalning avvisad",
    comment,
    timestamp: "2026-10-29T10:00:00",
    stepNumber: null,
    status: "REJECTED",
    reference: "Faktura 4711",
    amount: 6000,
    currency: "SEK",
    toIban: "SE8550000000054910000003",
  };
}

describe("formatTimelineComment", () => {
  it.each(["APPROVE_PAYMENT", "REJECT_PAYMENT"])("shows the attestant's message on %s", (eventType) => {
    expect(formatTimelineComment(timelineEntry(eventType, "  Stämmer mot avtalet  "))).toBe("Stämmer mot avtalet");
  });

  it("has no message when the attestant wrote none", () => {
    expect(formatTimelineComment(timelineEntry("REJECT_PAYMENT", null))).toBeNull();
    expect(formatTimelineComment(timelineEntry("REJECT_PAYMENT", "   "))).toBeNull();
  });

  // Admin får en rad per atteststeg som bär samma meddelande som beslutet; det ska inte stå två gånger
  it.each(["APPROVAL_STEP_APPROVED", "APPROVAL_STEP_REJECTED"])("hides the message on the %s row", (eventType) => {
    expect(formatTimelineComment(timelineEntry(eventType, "Stämmer mot avtalet"))).toBeNull();
  });
});
