import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import type { ReactNode } from "react";
import userEvent from "@testing-library/user-event";
import AttestGrid from "./AttestGrid";
import { useApprovals } from "./useApprovals";
import { ApiError } from "../../error/api.error";
import type { AttestSteg } from "./schema";

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children }: { children: ReactNode }) => <a>{children}</a>,
}));

vi.mock("./useApprovals", () => ({
  useApprovals: vi.fn(),
}));

const mockedUseApprovals = vi.mocked(useApprovals);

const step: AttestSteg = {
  id: 7,
  paymentId: 42,
  mottagare: "SE4550000000058398257466",
  typ: "Betalning",
  belopp: 12500,
  currency: "SEK",
  reference: "Faktura 1042",
  createdAt: "2026-09-28T13:52:00",
  status: "Väntar",
  fromAccountName: "Driftkonto",
  balanceAfterPayment: 87500,
  initiatorName: "Lisa Persson",
};

// Godkänn-knappen visar beloppet som godkänns
const APPROVE_BUTTON = /^Godkänn 12\s500,00 SEK$/;

type MutateOptions = { onSuccess?: () => void; onError?: (error: unknown) => void };

type RejectMutate = (input: { stepId: number; comment: string }, options: MutateOptions) => void;

function mockApprovals(
  mutate: (id: number, options: MutateOptions) => void,
  { reject = vi.fn(), data = [step] }: { reject?: RejectMutate; data?: AttestSteg[] } = {},
) {
  mockedUseApprovals.mockReturnValue({
    data,
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
    approve: { mutate, isPending: false, variables: undefined },
    reject: { mutate: reject, isPending: false, variables: undefined },
  } as unknown as ReturnType<typeof useApprovals>);
}

async function openReview(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: /granska betalning till/i }));
}

describe("AttestGrid", () => {
  beforeEach(() => {
    mockedUseApprovals.mockReset();
  });

  it("shows which account is drawn from and who created the payment", () => {
    mockApprovals(vi.fn());
    render(<AttestGrid />);

    const row = screen.getByRole("row", { name: /SE4550000000058398257466/ });
    expect(within(row).getByText("Driftkonto")).toBeInTheDocument();
    expect(within(row).getByText("Lisa Persson")).toBeInTheDocument();
  });

  it("shows the amount the same way as Mina betalningar", () => {
    mockApprovals(vi.fn());
    render(<AttestGrid />);

    const row = screen.getByRole("row", { name: /SE4550000000058398257466/ });
    expect(within(row).getByText(/^12\s500,00 SEK$/)).toBeInTheDocument();
  });

  it("shows the remaining balance for the payment account", () => {
    mockApprovals(vi.fn());
    render(<AttestGrid />);
    expect(screen.getByText(/^Saldo efter betalning: 87\s500,00 SEK$/)).toBeInTheDocument();
  });

  it("does not show a made-up balance when the account balance is unavailable", () => {
    mockApprovals(vi.fn(), { data: [{ ...step, balanceAfterPayment: null }] });
    render(<AttestGrid />);
    expect(screen.getByText("Saldo efter betalning: Kan inte beräknas")).toBeInTheDocument();
  });

  it("confirms which payment was approved", async () => {
    mockApprovals((_id, options) => options.onSuccess?.());
    const user = userEvent.setup();
    render(<AttestGrid />);

    await openReview(user);
    await user.click(screen.getByRole("button", { name: APPROVE_BUTTON }));

    expect(
      screen.getByText(/Betalningen till SE4550000000058398257466 på 12\s500,00 SEK är godkänd\./),
    ).toBeInTheDocument();
  });

  it("shows why an approval failed", async () => {
    mockApprovals((_id, options) =>
      options.onError?.(new ApiError(403, "Du har inte behörighet att göra det här.")),
    );
    const user = userEvent.setup();
    render(<AttestGrid />);

    await openReview(user);
    await user.click(screen.getByRole("button", { name: APPROVE_BUTTON }));

    expect(
      screen.getByText(
        "Kunde inte godkänna betalningen till SE4550000000058398257466. Du har inte behörighet att göra det här.",
      ),
    ).toBeInTheDocument();
  });

  it("approves the clicked payment", async () => {
    const mutate = vi.fn();
    mockApprovals(mutate);
    const user = userEvent.setup();
    render(<AttestGrid />);

    await openReview(user);
    await user.click(screen.getByRole("button", { name: APPROVE_BUTTON }));

    expect(mutate).toHaveBeenCalledWith(7, expect.any(Object));
  });

  describe("granska", () => {
    it("shows one review button per payment instead of approve and reject", () => {
      mockApprovals(vi.fn());
      render(<AttestGrid />);

      const row = screen.getByRole("row", { name: /SE4550000000058398257466/ });
      expect(within(row).getByRole("button", { name: /granska betalning till/i })).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /godkänn/i })).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /avvisa/i })).not.toBeInTheDocument();
    });

    it("opens a dialog with a summary of the payment and both decisions", async () => {
      mockApprovals(vi.fn());
      const user = userEvent.setup();
      render(<AttestGrid />);

      await openReview(user);

      const dialog = screen.getByRole("dialog", { name: "Granska betalning" });
      expect(within(dialog).getByText(/^12\s500,00 SEK$/)).toBeInTheDocument();
      expect(within(dialog).getByText("SE4550000000058398257466")).toBeInTheDocument();
      expect(within(dialog).getByText("Driftkonto")).toBeInTheDocument();
      expect(within(dialog).getByText(/^87\s500,00 SEK$/)).toBeInTheDocument();
      expect(within(dialog).getByText(/av Lisa Persson$/)).toBeInTheDocument();
      expect(within(dialog).getByText("Faktura 1042")).toBeInTheDocument();
      expect(within(dialog).getByRole("button", { name: APPROVE_BUTTON })).toBeInTheDocument();
      expect(within(dialog).getByRole("button", { name: "Avvisa betalningen" })).toBeInTheDocument();
    });

    it("can be closed without deciding anything", async () => {
      const mutate = vi.fn();
      const rejectMutate = vi.fn();
      mockApprovals(mutate, { reject: rejectMutate });
      const user = userEvent.setup();
      render(<AttestGrid />);

      await openReview(user);
      await user.click(screen.getByRole("button", { name: "Stäng" }));

      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      expect(mutate).not.toHaveBeenCalled();
      expect(rejectMutate).not.toHaveBeenCalled();
    });

    it("closes the dialog when the payment is approved", async () => {
      mockApprovals((_id, options) => options.onSuccess?.());
      const user = userEvent.setup();
      render(<AttestGrid />);

      await openReview(user);
      await user.click(screen.getByRole("button", { name: APPROVE_BUTTON }));

      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("keeps the dialog open and shows the error inside it when approval fails", async () => {
      mockApprovals((_id, options) => options.onError?.(new ApiError(409, "Betalningen är redan hanterad.")));
      const user = userEvent.setup();
      render(<AttestGrid />);

      await openReview(user);
      await user.click(screen.getByRole("button", { name: APPROVE_BUTTON }));

      const dialog = screen.getByRole("dialog");
      expect(within(dialog).getByRole("alert")).toHaveTextContent("Betalningen är redan hanterad.");
    });
  });

  it("shows the confirmation inside the empty state when the last payment is approved", async () => {
    const approvals = {
      data: [step],
      isLoading: false,
      isError: false,
      isFetching: false,
      refetch: vi.fn(),
      approve: {
        mutate: (_id: number, options: MutateOptions) => {
          // Som i appen: listan hämtas om och är tom efter godkännandet
          approvals.data = [];
          options.onSuccess?.();
        },
        isPending: false,
        variables: undefined,
      },
      reject: { mutate: vi.fn(), isPending: false, variables: undefined },
    };
    mockedUseApprovals.mockImplementation(
      () => ({ ...approvals }) as unknown as ReturnType<typeof useApprovals>,
    );
    const user = userEvent.setup();
    render(<AttestGrid />);

    await openReview(user);
    await user.click(screen.getByRole("button", { name: APPROVE_BUTTON }));

    expect(screen.getByRole("heading", { name: "Allt är klart" })).toBeInTheDocument();
    expect(
      screen.getByText(/är godkänd\. Inget mer väntar på ditt godkännande\./),
    ).toBeInTheDocument();
  });

  describe("avvisa", () => {
    it("asks for an optional reason before rejecting", async () => {
      const rejectMutate = vi.fn();
      mockApprovals(vi.fn(), { reject: rejectMutate });
      const user = userEvent.setup();
      render(<AttestGrid />);

      await openReview(user);
      await user.click(screen.getByRole("button", { name: "Avvisa betalningen" }));

      expect(screen.getByLabelText("Anledning (valfritt)")).toHaveFocus();
      expect(rejectMutate).not.toHaveBeenCalled();
    });

    it("rejects the payment with the reason that was written", async () => {
      const rejectMutate = vi.fn<RejectMutate>((_input, options) => options.onSuccess?.());
      mockApprovals(vi.fn(), { reject: rejectMutate });
      const user = userEvent.setup();
      render(<AttestGrid />);

      await openReview(user);
      await user.click(screen.getByRole("button", { name: "Avvisa betalningen" }));
      await user.type(screen.getByLabelText("Anledning (valfritt)"), "Otillräckligt saldo");
      await user.click(screen.getByRole("button", { name: "Bekräfta avvisning" }));

      expect(rejectMutate).toHaveBeenCalledWith({ stepId: 7, comment: "Otillräckligt saldo" }, expect.anything());
      expect(
        screen.getByText(/Betalningen till SE4550000000058398257466 på 12\s500,00 SEK är avvisad\./),
      ).toBeInTheDocument();
      expect(screen.queryByLabelText("Anledning (valfritt)")).not.toBeInTheDocument();
    });

    it("can be cancelled without rejecting anything", async () => {
      const rejectMutate = vi.fn();
      mockApprovals(vi.fn(), { reject: rejectMutate });
      const user = userEvent.setup();
      render(<AttestGrid />);

      await openReview(user);
      await user.click(screen.getByRole("button", { name: "Avvisa betalningen" }));
      await user.click(screen.getByRole("button", { name: "Tillbaka" }));

      expect(screen.queryByLabelText("Anledning (valfritt)")).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: APPROVE_BUTTON })).toBeInTheDocument();
      expect(rejectMutate).not.toHaveBeenCalled();
    });

    it("shows why a rejection failed and keeps the reason", async () => {
      const rejectMutate = vi.fn<RejectMutate>((_input, options) =>
        options.onError?.(new ApiError(400, "Kommentaren får vara högst 255 tecken.")),
      );
      mockApprovals(vi.fn(), { reject: rejectMutate });
      const user = userEvent.setup();
      render(<AttestGrid />);

      await openReview(user);
      await user.click(screen.getByRole("button", { name: "Avvisa betalningen" }));
      await user.type(screen.getByLabelText("Anledning (valfritt)"), "För lång");
      await user.click(screen.getByRole("button", { name: "Bekräfta avvisning" }));

      expect(screen.getByText(/Kunde inte avvisa betalningen.*högst 255 tecken/)).toBeInTheDocument();
      expect(screen.getByLabelText("Anledning (valfritt)")).toHaveValue("För lång");
    });
  });
});
