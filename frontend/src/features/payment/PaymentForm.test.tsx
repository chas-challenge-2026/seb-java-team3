import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PaymentForm from "./PaymentForm";
import { createPayment } from "./api";

vi.mock("@tanstack/react-router", () => ({
  useNavigate: () => vi.fn(),
}));

vi.mock("./api", () => ({
  createPayment: vi.fn(),
}));

const mockedCreatePayment = vi.mocked(createPayment);

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
});
