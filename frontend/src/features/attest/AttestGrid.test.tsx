import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
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
};

type MutateOptions = { onSuccess?: () => void; onError?: (error: unknown) => void };

function mockApprovals(mutate: (id: number, options: MutateOptions) => void) {
  mockedUseApprovals.mockReturnValue({
    data: [step],
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
    approve: { mutate, isPending: false, variables: undefined },
  } as unknown as ReturnType<typeof useApprovals>);
}

describe("AttestGrid", () => {
  beforeEach(() => {
    mockedUseApprovals.mockReset();
  });

  it("confirms which payment was approved", async () => {
    mockApprovals((_id, options) => options.onSuccess?.());
    const user = userEvent.setup();
    render(<AttestGrid />);

    await user.click(screen.getByRole("button", { name: /godkänn betalning/i }));

    expect(
      screen.getByText(/Betalningen till SE4550000000058398257466 på 12\s500,00\s*kr är godkänd\./),
    ).toBeInTheDocument();
  });

  it("shows why an approval failed", async () => {
    mockApprovals((_id, options) =>
      options.onError?.(new ApiError(403, "Du har inte behörighet att göra det här.")),
    );
    const user = userEvent.setup();
    render(<AttestGrid />);

    await user.click(screen.getByRole("button", { name: /godkänn betalning/i }));

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

    await user.click(screen.getByRole("button", { name: /godkänn betalning/i }));

    expect(mutate).toHaveBeenCalledWith(7, expect.any(Object));
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
    };
    mockedUseApprovals.mockImplementation(
      () => ({ ...approvals }) as unknown as ReturnType<typeof useApprovals>,
    );
    const user = userEvent.setup();
    render(<AttestGrid />);

    await user.click(screen.getByRole("button", { name: /godkänn betalning/i }));

    expect(screen.getByRole("heading", { name: "Allt är klart" })).toBeInTheDocument();
    expect(
      screen.getByText(/är godkänd\. Inget mer väntar på ditt godkännande\./),
    ).toBeInTheDocument();
  });
});
