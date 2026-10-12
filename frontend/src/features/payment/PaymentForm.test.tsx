import { beforeEach, describe, expect, it, vi } from "vitest";
import { render as rtlRender, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { getAccounts } from "../accounts/api";
import PaymentForm from "./PaymentForm";
import { createPayment, getPaymentConfig } from "./api";
import { ApiError } from "../../error/api.error";

vi.mock("@tanstack/react-router", () => ({
  useNavigate: () => vi.fn(),
}));

vi.mock("./api", () => ({
  createPayment: vi.fn(),
  getPaymentConfig: vi.fn(),
}));

vi.mock("../accounts/api", () => ({
  getAccounts: vi.fn(),
  accountsQueryOptions: { queryKey: ["accounts"], queryFn: () => getAccounts() },
}));
function render(ui: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return rtlRender(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}
const mockedCreatePayment = vi.mocked(createPayment);
const mockedGetPaymentConfig = vi.mocked(getPaymentConfig);

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await selectAccount(user, "Driftkonto");
  await user.type(
    screen.getByLabelText(/^Mottagar-IBAN/),
    "SE45 5000 0000 0583 9825 7466",
  );
  await user.type(screen.getByLabelText(/^Belopp \(SEK\)/), "1000.00");
}

async function selectAccount(
  user: ReturnType<typeof userEvent.setup>,
  accountName: string,
) {
  await user.click(screen.getByRole("combobox"));
  await user.click(await screen.findByRole("option", { name: new RegExp(accountName, "i") }));
}

describe("PaymentForm", () => {
  it("warns and blocks payment above the selected balance, then allows an equal amount", async () => {
    vi.mocked(getAccounts).mockResolvedValue([{ id: 42, name: "Driftkonto", iban: "SE4550000000058398257466", balance: 1000, currency: "SEK" }]);
    const user = userEvent.setup();
    render(<PaymentForm />);
    await fillValidForm(user);
    await user.clear(screen.getByLabelText(/^Belopp/));
    await user.type(screen.getByLabelText(/^Belopp/), "1 000,01");
    expect(screen.getByText("Beloppet överstiger tillgängligt saldo.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /skicka betalning/i })).toBeDisabled();
    expect(mockedCreatePayment).not.toHaveBeenCalled();
    await user.clear(screen.getByLabelText(/^Belopp/));
    await user.type(screen.getByLabelText(/^Belopp/), "1000");
    expect(screen.queryByText("Beloppet överstiger tillgängligt saldo.")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /skicka betalning/i })).toBeEnabled();
  });
  it("blocks submission when accounts cannot be loaded", async () => {
    vi.mocked(getAccounts).mockRejectedValue(new Error("offline"));
    render(<PaymentForm />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Kontona kunde inte hämtas");
    expect(screen.getByRole("button", { name: /skicka betalning/i })).toBeDisabled();
  });
  it("shows an empty account list without placeholder accounts", async () => {
    vi.mocked(getAccounts).mockResolvedValue([]);
    render(<PaymentForm />);
    expect(await screen.findByText("Företaget har inga konton att betala från.")).toBeInTheDocument();
    expect(screen.getByRole("combobox")).toBeDisabled();
  });
  beforeEach(() => {
    vi.mocked(getAccounts).mockReset();
    vi.mocked(getAccounts).mockResolvedValue([{ id: 42, name: "Driftkonto", iban: "SE4550000000058398257466", balance: 10000, currency: "SEK" }]);
    mockedCreatePayment.mockReset();
    mockedGetPaymentConfig.mockReset();
    mockedGetPaymentConfig.mockResolvedValue({ approvalThreshold: 5000 });
  });

  it("shows the approval threshold loaded from the backend", async () => {
    render(<PaymentForm />);

    expect(
      await screen.findByText(/betalningar över 5 000 kr behöver attesteras/i),
    ).toBeInTheDocument();
  });

  it("shows a validation error and does not submit for an invalid IBAN", async () => {
    const user = userEvent.setup();
    render(<PaymentForm />);

    await selectAccount(user, "Driftkonto");
    await user.type(screen.getByLabelText(/^Mottagar-IBAN/), "not-an-iban");
    await user.type(screen.getByLabelText(/^Belopp \(SEK\)/), "1000.00");
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    expect(await screen.findByText("Ange en giltig IBAN.")).toBeInTheDocument();
    expect(mockedCreatePayment).not.toHaveBeenCalled();
  });

  it("shows a validation error when no account is selected", async () => {
    const user = userEvent.setup();
    const { container } = render(<PaymentForm />);

    await user.type(
      screen.getByLabelText(/^Mottagar-IBAN/),
      "SE45 5000 0000 0583 9825 7466",
    );
    await user.type(screen.getByLabelText(/^Belopp \(SEK\)/), "1000.00");
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    await waitFor(() => {
      expect(container.querySelector('[class*="fieldError"]')).toHaveTextContent(
        "Välj vilket konto betalningen ska dras från.",
      );
    });
    expect(mockedCreatePayment).not.toHaveBeenCalled();
  });

  it("moves focus to the first invalid field when submitting", async () => {
    const user = userEvent.setup();
    render(<PaymentForm />);

    await user.type(screen.getByLabelText(/^Mottagar-IBAN/), "not-an-iban");
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    await waitFor(() => {
      expect(screen.getByRole("combobox")).toHaveFocus();
    });

    await selectAccount(user, "Driftkonto");
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    await waitFor(() => {
      expect(screen.getByLabelText(/^Mottagar-IBAN/)).toHaveFocus();
    });
  });

  it("marks the required fields", () => {
    render(<PaymentForm />);

    expect(screen.getByRole("combobox")).toHaveAttribute("aria-required", "true");
    expect(screen.getByLabelText(/^Mottagar-IBAN/)).toBeRequired();
    expect(screen.getByLabelText(/^Belopp \(SEK\)/)).toBeRequired();
    expect(screen.getByLabelText(/^Referens/)).not.toBeRequired();
  });

  it("submits the normalized data and shows the confirmation on success", async () => {
    mockedCreatePayment.mockResolvedValue({
      id: 1,
      amount: 1000,
      toIban: "SE4550000000058398257466",
      status: "COMPLETED",
      createdAt: "2026-01-01T00:00:00Z",
    });

    const user = userEvent.setup();
    render(<PaymentForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    await waitFor(() => expect(mockedCreatePayment).toHaveBeenCalledTimes(1));

    expect(mockedCreatePayment).toHaveBeenCalledWith(
      expect.objectContaining({
        account: "42",
        recipientIban: "SE45 5000 0000 0583 9825 7466",
        amount: "1000.00",
      }),
    );

    expect(
      await screen.findByText("Betalningen har skickats"),
    ).toBeInTheDocument();
  });

  it("accepts an amount written in Swedish format", async () => {
    mockedCreatePayment.mockResolvedValue({
      id: 1,
      amount: 1500.5,
      toIban: "SE4550000000058398257466",
      status: "COMPLETED",
      createdAt: "2026-01-01T00:00:00Z",
    });

    const user = userEvent.setup();
    render(<PaymentForm />);

    await selectAccount(user, "Driftkonto");
    await user.type(
      screen.getByLabelText(/^Mottagar-IBAN/),
      "SE45 5000 0000 0583 9825 7466",
    );
    await user.type(screen.getByLabelText(/^Belopp \(SEK\)/), "1 500,50");
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    await waitFor(() =>
      expect(mockedCreatePayment).toHaveBeenCalledWith(
        expect.objectContaining({ amount: "1500.50" }),
      ),
    );
  });

  it("shows an approval notice for a payment awaiting approval", async () => {
    mockedCreatePayment.mockResolvedValue({
      id: 2,
      amount: 5000,
      toIban: "DE10535600287154123082",
      status: "PENDING_APPROVAL",
      createdAt: "2026-01-01T00:00:00Z",
    });
    const user = userEvent.setup();
    render(<PaymentForm />);

    await fillValidForm(user);
    await user.clear(screen.getByLabelText(/^Belopp \(SEK\)/));
    await user.type(screen.getByLabelText(/^Belopp \(SEK\)/), "5000");
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    expect(await screen.findByText("Väntar på attest")).toBeInTheDocument();
    expect(
      screen.getByText("Betalningen behöver godkännas innan den genomförs."),
    ).toBeInTheDocument();
  });

  it("shows the API error when creating the payment fails", async () => {
    mockedCreatePayment.mockRejectedValue(
      new ApiError(409, "Ingen attestant finns tillgänglig."),
    );
    const user = userEvent.setup();
    render(<PaymentForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Ingen attestant finns tillgänglig.",
    );
  });

  it("shows a friendly message instead of 502 when the backend is down", async () => {
    mockedCreatePayment.mockRejectedValue(
      new ApiError(502, "Kunde inte nå servern. Kontrollera din anslutning och försök igen om en stund."),
    );
    const user = userEvent.setup();
    render(<PaymentForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Kunde inte nå servern. Betalningen har inte skickats.");
    expect(alert).not.toHaveTextContent("502");
  });

  it("asks the user to check before retrying when the outcome is uncertain", async () => {
    mockedCreatePayment.mockRejectedValue(
      new ApiError(500, "Något gick fel hos oss. Försök igen om en stund."),
    );
    const user = userEvent.setup();
    render(<PaymentForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Kontrollera under Mina betalningar innan du försöker igen.",
    );
  });

  it("disables the submit button while the payment is being created", async () => {
    mockedCreatePayment.mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    render(<PaymentForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    expect(screen.getByRole("button", { name: /skickar/i })).toBeDisabled();
  });
});
