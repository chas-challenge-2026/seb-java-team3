import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import MyPaymentsPage from "./MyPaymentsPage";
import { fetchMyPaymentStatuses, markChangedPaymentsSeen } from "../api/auditApi";
import { changedPaymentCountQueryKey } from "../useChangedPaymentCount";
import type { MyPaymentStatus } from "../schema";

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children }: { children: ReactNode }) => <a>{children}</a>,
}));

vi.mock("../api/auditApi", async (importOriginal) => ({
  ...await importOriginal<typeof import("../api/auditApi")>(),
  fetchMyPaymentStatuses: vi.fn(),
  markChangedPaymentsSeen: vi.fn(),
}));

function payment(paymentId: number, status: string, reference: string): MyPaymentStatus {
  return {
    paymentId,
    reference,
    amount: 7500,
    currency: "SEK",
    fromAccountName: "Driftkonto",
    toIban: "DE89370400440532013000",
    status,
    createdAt: "2026-10-28T10:00:00",
    executedAt: null,
    approvalSteps: [],
  };
}

function renderPage(queryClient: QueryClient) {
  render(
    <QueryClientProvider client={queryClient}>
      <MyPaymentsPage />
    </QueryClientProvider>,
  );
}

describe("MyPaymentsPage", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.mocked(markChangedPaymentsSeen).mockReset().mockResolvedValue();
    vi.mocked(fetchMyPaymentStatuses).mockResolvedValue([
      payment(1, "PENDING_APPROVAL", "Faktura väntar"),
      payment(2, "COMPLETED", "Faktura klar"),
      payment(3, "REJECTED", "Faktura avvisad"),
    ]);
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it("marks the payments as seen once the list has loaded, which clears the badge", async () => {
    queryClient.setQueryData(changedPaymentCountQueryKey, 2);
    renderPage(queryClient);

    await screen.findByText("Faktura klar");
    await waitFor(() => expect(markChangedPaymentsSeen).toHaveBeenCalled());
    await waitFor(() => expect(queryClient.getQueryData(changedPaymentCountQueryKey)).toBe(0));
  });

  it("does not mark anything as seen when the list could not be loaded", async () => {
    vi.mocked(fetchMyPaymentStatuses).mockRejectedValue(new Error("nätverksfel"));
    renderPage(queryClient);

    await screen.findByText("Kunde inte hämta dina betalningar");
    expect(markChangedPaymentsSeen).not.toHaveBeenCalled();
  });

  it("filters the list by status, with a count per status", async () => {
    const user = userEvent.setup();
    renderPage(queryClient);
    await screen.findByText("Faktura klar");
    const filters = screen.getByRole("group", { name: "Filtrera på status" });

    expect(within(filters).getByRole("button", { name: "Avvisade 1" })).toHaveAttribute("aria-pressed", "false");
    await user.click(within(filters).getByRole("button", { name: "Avvisade 1" }));

    expect(screen.getByText("Faktura avvisad")).toBeInTheDocument();
    expect(screen.queryByText("Faktura klar")).not.toBeInTheDocument();
    expect(screen.queryByText("Faktura väntar")).not.toBeInTheDocument();
    expect(screen.getByLabelText("1 betalningar")).toBeInTheDocument();

    await user.click(within(filters).getByRole("button", { name: "Alla 3" }));
    expect(screen.getByText("Faktura klar")).toBeInTheDocument();
  });
});
