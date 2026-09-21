import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

const mockedCreatePayment = vi.mocked(createPayment);
const mockedGetPaymentConfig = vi.mocked(getPaymentConfig);

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(screen.getByRole("combobox"), "driftkonto");
  await user.type(
    screen.getByLabelText("Mottagar-IBAN"),
    "SE45 5000 0000 0583 9825 7466",
  );
  await user.type(screen.getByLabelText("Belopp (SEK)"), "1000.00");
}

describe("PaymentForm", () => {
  beforeEach(() => {
    mockedCreatePayment.mockReset();
    mockedGetPaymentConfig.mockReset();
    mockedGetPaymentConfig.mockResolvedValue({ approvalThreshold: 5000 });
  });

  it("shows the approval threshold loaded from the backend", async () => {
    render(<PaymentForm />);

    expect(
      await screen.findByText(/betalningar på 5 000 kr eller mer behöver attesteras/i),
    ).toBeInTheDocument();
  });

  it("shows a validation error and does not submit for an invalid IBAN", async () => {
    const user = userEvent.setup();
    render(<PaymentForm />);

    await user.selectOptions(screen.getByRole("combobox"), "driftkonto");
    await user.type(screen.getByLabelText("Mottagar-IBAN"), "not-an-iban");
    await user.type(screen.getByLabelText("Belopp (SEK)"), "1000.00");
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    expect(await screen.findByText("Ange en giltig IBAN.")).toBeInTheDocument();
    expect(mockedCreatePayment).not.toHaveBeenCalled();
  });

  it("shows a validation error when no account is selected", async () => {
    const user = userEvent.setup();
    const { container } = render(<PaymentForm />);

    await user.type(
      screen.getByLabelText("Mottagar-IBAN"),
      "SE45 5000 0000 0583 9825 7466",
    );
    await user.type(screen.getByLabelText("Belopp (SEK)"), "1000.00");
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    await waitFor(() => {
      expect(container.querySelector('[class*="fieldError"]')).toHaveTextContent(
        "Välj vilket konto betalningen ska dras från.",
      );
    });
    expect(mockedCreatePayment).not.toHaveBeenCalled();
  });

  it("submits the normalized data and shows the confirmation on success", async () => {
    mockedCreatePayment.mockResolvedValue({
      id: 1,
      amount: 1000,
      toIban: "SE4550000000058398257466",
      status: "PENDING",
      createdAt: "2026-01-01T00:00:00Z",
    });

    const user = userEvent.setup();
    render(<PaymentForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    await waitFor(() => expect(mockedCreatePayment).toHaveBeenCalledTimes(1));

    expect(mockedCreatePayment).toHaveBeenCalledWith(
      expect.objectContaining({
        account: "driftkonto",
        recipientIban: "SE45 5000 0000 0583 9825 7466",
        amount: "1000.00",
      }),
    );

    expect(
      await screen.findByText("Betalningen har skickats"),
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

  it("disables the submit button while the payment is being created", async () => {
    mockedCreatePayment.mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    render(<PaymentForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: /skicka betalning/i }));

    expect(screen.getByRole("button", { name: /skickar/i })).toBeDisabled();
  });
});
