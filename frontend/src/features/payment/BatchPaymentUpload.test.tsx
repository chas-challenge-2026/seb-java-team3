import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { importPayments } from "./api";
import BatchPaymentUpload from "./BatchPaymentUpload";

vi.mock("./api", () => ({
  importPayments: vi.fn(),
}));

const mockedImportPayments = vi.mocked(importPayments);

const ACCOUNTS = [
  { id: 1, name: "Driftkonto", iban: "SE4550000000058398257466", balance: 2500000, currency: "SEK" },
  { id: 2, name: "Lönekonto", iban: "SE1850000000058398257467", balance: 890000, currency: "SEK" },
];

const VALID_CSV =
  "from_account_id,to_iban,amount,reference\n" +
  "1,SE3550000000054910000003,1250.50,Faktura 2001\n" +
  '2,SE7850000000054910000005,8000.00,"Lön, oktober"\n';

function renderUpload() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  // Kontona ligger redan i cachen, så testet anropar aldrig /api/accounts
  queryClient.setQueryData(["accounts"], ACCOUNTS);
  return render(
    <QueryClientProvider client={queryClient}>
      <BatchPaymentUpload />
    </QueryClientProvider>,
  );
}

async function chooseFile(content: string, name = "payments.csv") {
  const user = userEvent.setup();
  const file = new File([content], name, { type: "text/csv" });
  await user.upload(screen.getByLabelText("CSV-fil"), file);
  await user.click(screen.getByRole("button", { name: "Granska betalningar" }));
  return { user, file };
}

describe("BatchPaymentUpload", () => {
  beforeEach(() => {
    mockedImportPayments.mockReset();
  });

  it("lists the user's accounts with the id to use in the file", () => {
    renderUpload();

    const table = screen.getByRole("table");
    expect(within(table).getByRole("row", { name: /1 Driftkonto SE4550000000058398257466/ })).toBeInTheDocument();
    expect(within(table).getByRole("row", { name: /2 Lönekonto SE1850000000058398257467/ })).toBeInTheDocument();
  });

  it("shows the payments for confirmation before anything is uploaded", async () => {
    renderUpload();
    await chooseFile(VALID_CSV);

    const dialog = await screen.findByRole("dialog", { name: "Bekräfta betalningar" });
    const payments = within(within(dialog).getByRole("list", { name: "Betalningar i filen" })).getAllByRole("listitem");
    expect(payments).toHaveLength(2);
    expect(payments[0]).toHaveTextContent("SE3550000000054910000003");
    expect(payments[0]).toHaveTextContent("Från Driftkonto · Faktura 2001");
    expect(payments[1]).toHaveTextContent("SE7850000000054910000005");
    expect(payments[1]).toHaveTextContent("Från Lönekonto · Lön, oktober");
    expect(within(dialog).getByText(/^9\s250,50 SEK$/)).toBeInTheDocument();
    expect(mockedImportPayments).not.toHaveBeenCalled();
  });

  it("uploads the file after confirmation and shows the result", async () => {
    mockedImportPayments.mockResolvedValue([
      { id: 1, amount: 1250.5, toIban: "SE3550000000054910000003", status: "COMPLETED", createdAt: null },
      { id: 2, amount: 8000, toIban: "SE7850000000054910000005", status: "PENDING_APPROVAL", createdAt: null },
    ]);
    renderUpload();
    const { user, file } = await chooseFile(VALID_CSV);

    const dialog = await screen.findByRole("dialog", { name: "Bekräfta betalningar" });
    await user.click(within(dialog).getByRole("button", { name: "Bekräfta och skapa 2 betalningar" }));

    expect(mockedImportPayments).toHaveBeenCalledWith(file);
    const resultDialog = await screen.findByRole("dialog", { name: "Betalningarna har skapats" });
    expect(within(resultDialog).getByText("1 väntar på attest")).toBeInTheDocument();

    await user.click(within(resultDialog).getByRole("button", { name: "Klar" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("2 betalningar har skapats");
    expect(screen.getByRole("status")).toHaveTextContent("1 väntar på attest; 1 har genomförts direkt");
  });

  it("does not upload anything when the confirmation is cancelled", async () => {
    renderUpload();
    const { user } = await chooseFile(VALID_CSV);

    const dialog = await screen.findByRole("dialog", { name: "Bekräfta betalningar" });
    await user.click(within(dialog).getByRole("button", { name: "Avbryt" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(mockedImportPayments).not.toHaveBeenCalled();
  });

  it("shows the server error in the dialog when the import is rejected", async () => {
    const { ApiError } = await import("../../error/api.error");
    mockedImportPayments.mockRejectedValue(new ApiError(400, "CSV-rad 3: Ogiltigt IBAN"));
    renderUpload();
    const { user } = await chooseFile(VALID_CSV);

    const dialog = await screen.findByRole("dialog", { name: "Bekräfta betalningar" });
    await user.click(within(dialog).getByRole("button", { name: "Bekräfta och skapa 2 betalningar" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("CSV-rad 3: Ogiltigt IBAN");
  });

  it("stops a row with an account id the user does not have", async () => {
    renderUpload();
    await chooseFile("from_account_id,to_iban,amount,reference\n42,SE3550000000054910000003,100.00,A\n");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Rad 2: konto-id 42 finns inte bland dina konton.",
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("rejects a non-CSV extension without calling the API", async () => {
    renderUpload();
    await chooseFile("not csv", "payments.txt");

    expect(await screen.findByRole("alert")).toHaveTextContent("Filen måste ha filändelsen .csv.");
    expect(mockedImportPayments).not.toHaveBeenCalled();
  });
});
