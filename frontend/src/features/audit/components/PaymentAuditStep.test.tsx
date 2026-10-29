import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import PaymentAuditStep from "./PaymentAuditStep";
import type { PaymentAuditTimelineEntry } from "../schema";

function entry(overrides: Partial<PaymentAuditTimelineEntry> = {}): PaymentAuditTimelineEntry {
  return {
    order: 2,
    sequence: "AUDIT-2",
    actor: "Johan Berg",
    eventType: "REJECT_PAYMENT",
    description: "Betalning avvisad. Kommentar: Fel konto",
    comment: "Fel konto",
    timestamp: "2026-10-29T10:00:00",
    stepNumber: null,
    status: "REJECTED",
    reference: "Faktura 4711",
    amount: 6000,
    currency: "SEK",
    toIban: "SE8550000000054910000003",
    ...overrides,
  };
}

function renderStep(overrides?: Partial<PaymentAuditTimelineEntry>) {
  return render(<ol><PaymentAuditStep entry={entry(overrides)} /></ol>);
}

describe("PaymentAuditStep", () => {
  it("shows the attestant's message under the decision", () => {
    renderStep();

    expect(screen.getByRole("heading", { name: "Betalning avvisad" })).toBeInTheDocument();
    expect(screen.getByText("Meddelande")).toBeInTheDocument();
    expect(screen.getByText("Fel konto")).toBeInTheDocument();
  });

  it("shows a message left when approving too", () => {
    renderStep({ eventType: "APPROVE_PAYMENT", status: "COMPLETED", comment: "Stämmer mot avtalet" });

    expect(screen.getByRole("heading", { name: "Betalning godkänd" })).toBeInTheDocument();
    expect(screen.getByText("Stämmer mot avtalet")).toBeInTheDocument();
  });

  it("shows no message section when the attestant wrote none", () => {
    renderStep({ comment: null });

    expect(screen.getByText("Johan Berg")).toBeInTheDocument();
    expect(screen.queryByText("Meddelande")).not.toBeInTheDocument();
  });

  // Meddelandet är fri text från en användare och ska aldrig tolkas som HTML
  it("shows a message containing markup as plain text", () => {
    const { container } = renderStep({ comment: "<img src=x onerror=alert(1)>" });

    expect(screen.getByText("<img src=x onerror=alert(1)>")).toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
  });
});
