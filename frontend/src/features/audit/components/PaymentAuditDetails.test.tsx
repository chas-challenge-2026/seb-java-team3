import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import PaymentAuditDetails from "./PaymentAuditDetails";
import { fetchPaymentAuditTimeline } from "../api/auditApi";
import { paymentAuditTimelineEntryListSchema } from "../schema";

// auditQueryOptions läser alla tre hämtningarna när modulen laddas, så de måste finnas med i mocken
vi.mock("../api/auditApi", () => ({
  fetchAuditEntries: vi.fn(),
  fetchMyPaymentStatuses: vi.fn(),
  fetchPaymentAuditTimeline: vi.fn(),
}));

const payment = {
  status: "REJECTED",
  reference: "Faktura 4711",
  amount: 6000,
  currency: "SEK",
  toIban: "SE8550000000054910000003",
};

// Så som backend svarar för en avvisad betalning. Admin får också en rad för atteststeget, som bär samma
// meddelande som beslutet. Svaret går genom det riktiga schemat, som api() gör i appen.
const adminTimeline = [
  {
    ...payment, order: 1, sequence: "AUDIT-1", actor: "Lisa Persson", eventType: "CREATE_PAYMENT",
    description: "Betalning skapad", comment: null, timestamp: "2026-10-29T09:00:00", stepNumber: null,
  },
  {
    ...payment, order: 2, sequence: "AUDIT-2", actor: "Johan Berg", eventType: "REJECT_PAYMENT",
    description: "Betalning avvisad. Kommentar: Fel konto", comment: "Fel konto",
    timestamp: "2026-10-29T10:00:00", stepNumber: null,
  },
  {
    ...payment, order: 3, sequence: "APPROVAL_STEP-300", actor: "Johan Berg", eventType: "APPROVAL_STEP_REJECTED",
    description: "Atteststeg 1 är REJECTED", comment: "Fel konto", timestamp: "2026-10-29T10:00:00", stepNumber: 1,
  },
];

function renderDetails() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <PaymentAuditDetails paymentId="100" id="details-100" saldoFore="10 000,00 SEK" saldoEfter="Ej genomförd" />
    </QueryClientProvider>,
  );
}

describe("PaymentAuditDetails", () => {
  beforeEach(() => {
    vi.mocked(fetchPaymentAuditTimeline).mockResolvedValue(paymentAuditTimelineEntryListSchema.parse(adminTimeline));
  });

  it("shows the attestant's message once, even when admin also gets the approval step row", async () => {
    renderDetails();

    expect(await screen.findByRole("heading", { name: "Betalning avvisad" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Atteststeg avvisat" })).toBeInTheDocument();
    expect(screen.getAllByText("Fel konto")).toHaveLength(1);
  });

  it("shows no message for a payment where the attestant wrote none", async () => {
    vi.mocked(fetchPaymentAuditTimeline).mockResolvedValue(paymentAuditTimelineEntryListSchema.parse(
      adminTimeline.map((entry) => ({ ...entry, comment: null })),
    ));
    renderDetails();

    expect(await screen.findByRole("heading", { name: "Betalning avvisad" })).toBeInTheDocument();
    expect(screen.queryByText("Meddelande")).not.toBeInTheDocument();
  });
});
