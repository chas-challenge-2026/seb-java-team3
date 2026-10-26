import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AuditGrid from "./AuditGrid";
import type { AuditEntry } from "../types";

function entry(id: string, reference: string, statusKey: string, status: string): AuditEntry {
  return {
    id,
    paymentId: id,
    tid: "2026-10-15 10:00",
    vem: "Lisa Persson",
    handelse: "Betalning skapad",
    status,
    statusKey,
    referens: reference,
    belopp: "7 500,00 SEK",
    saldoFore: "10 000,00 SEK",
    saldoEfter: "2 500,00 SEK",
    franKonto: "Driftkonto",
    betalning: `Betalning #${id}`,
  };
}

const entries = [
  entry("1", "Faktura väntar", "PENDING_APPROVAL", "Väntar attest"),
  entry("2", "Faktura klar", "COMPLETED", "Genomförd"),
  entry("3", "Faktura också klar", "COMPLETED", "Genomförd"),
];

describe("AuditGrid", () => {
  it("shows every payment and the count per status from the start", () => {
    render(<AuditGrid entries={entries} />);

    expect(screen.getAllByRole("button", { expanded: false })).toHaveLength(3);
    expect(screen.getByRole("button", { name: "Alla 3" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Genomförda 2" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Avvisade 0" })).toBeInTheDocument();
  });

  it("shows only payments with the chosen status", async () => {
    const user = userEvent.setup();
    render(<AuditGrid entries={entries} />);

    await user.click(screen.getByRole("button", { name: "Genomförda 2" }));

    // Referensen syns bara i den utfällda raden, så raderna känns igen på betalningsnumret
    expect(screen.queryByText("Betalning #1")).not.toBeInTheDocument();
    expect(screen.getByText("Betalning #2")).toBeInTheDocument();
    expect(screen.getByText("Betalning #3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Genomförda 2" })).toHaveAttribute("aria-pressed", "true");
  });

  it("says so when no payment has the chosen status", async () => {
    const user = userEvent.setup();
    render(<AuditGrid entries={entries} />);

    await user.click(screen.getByRole("button", { name: "Avvisade 0" }));

    expect(screen.getByText("Inga betalningar med den statusen.")).toBeInTheDocument();
  });

  it("shows which account each payment is drawn from", () => {
    render(<AuditGrid entries={entries} />);

    expect(screen.getAllByText("Driftkonto")).toHaveLength(3);
  });
});
