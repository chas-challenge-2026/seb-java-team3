import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { importPayments } from "./api";
import BatchPaymentUpload from "./BatchPaymentUpload";

vi.mock("./api", () => ({
  importPayments: vi.fn(),
}));

const mockedImportPayments = vi.mocked(importPayments);

function renderUpload() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <BatchPaymentUpload />
    </QueryClientProvider>,
  );
}

describe("BatchPaymentUpload", () => {
  beforeEach(() => {
    mockedImportPayments.mockReset();
  });

  it("uploads a CSV and displays the batch result", async () => {
    mockedImportPayments.mockResolvedValue([
      {
        id: 1,
        amount: 100,
        toIban: "SE4550000000058398257466",
        status: "PENDING_APPROVAL",
        createdAt: null,
      },
      {
        id: 2,
        amount: 50,
        toIban: "SE4550000000058398257466",
        status: "COMPLETED",
        createdAt: null,
      },
    ]);

    const user = userEvent.setup();
    renderUpload();
    const file = new File(
      ["from_account_id,to_iban,amount,reference\n42,SE4550000000058398257466,100,Invoice"],
      "payments.csv",
      { type: "text/csv" },
    );
    await user.upload(screen.getByLabelText("CSV-fil"), file);
    await user.click(screen.getByRole("button", { name: /ladda upp och skapa/i }));

    await waitFor(() => expect(mockedImportPayments).toHaveBeenCalledWith(file));
    expect(await screen.findByRole("status")).toHaveTextContent(
      "2 betalningar har skapats",
    );
    expect(screen.getByRole("status")).toHaveTextContent("1 väntar på attest");
    expect(screen.getByRole("status")).toHaveTextContent("1 har genomförts direkt");
  });

  it("rejects a non-CSV extension without calling the API", async () => {
    const user = userEvent.setup();
    renderUpload();
    const file = new File(["not csv"], "payments.txt", { type: "text/csv" });
    await user.upload(screen.getByLabelText("CSV-fil"), file);
    await user.click(screen.getByRole("button", { name: /ladda upp och skapa/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Filen måste ha filändelsen .csv.",
    );
    expect(mockedImportPayments).not.toHaveBeenCalled();
  });
});
