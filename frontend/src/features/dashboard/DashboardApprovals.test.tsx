import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import DashboardApprovals from "./DashboardApprovals";
import { useApprovals } from "../attest/useApprovals";
import type { AttestSteg } from "../attest/schema";

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, to, ...props }: { children: ReactNode; to: string }) => (
    <a href={to} {...props}>{children}</a>
  ),
}));

vi.mock("../attest/useApprovals", () => ({
  useApprovals: vi.fn(),
}));

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
  stepNumber: 1,
  totalSteps: 1,
  previousApprovals: [],
};

function mockApprovals(data: AttestSteg[]) {
  vi.mocked(useApprovals).mockReturnValue({
    data,
    isPending: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useApprovals>);
}

describe("DashboardApprovals", () => {
  beforeEach(() => {
    vi.mocked(useApprovals).mockReset();
  });

  it("gives the attestant a shortcut straight to the attest view", () => {
    mockApprovals([step]);
    render(<DashboardApprovals />);

    expect(screen.getByRole("link", { name: "Gå till attestering" })).toHaveAttribute("href", "/attest");
  });

  it("gives admin no shortcut, since admin cannot approve", () => {
    mockApprovals([step]);
    render(<DashboardApprovals readOnly />);

    expect(screen.queryByRole("link", { name: "Gå till attestering" })).not.toBeInTheDocument();
  });

  it("shows no shortcut when nothing is waiting", () => {
    mockApprovals([]);
    render(<DashboardApprovals />);

    expect(screen.getByRole("heading", { name: "Inga betalningar väntar på attest" })).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
