import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import SideBar from "./SideBar";
import { useUser } from "../../../features/auth/useUser";
import { usePendingApprovalCount } from "../../../features/attest/useApprovals";
import { useChangedPaymentCount } from "../../../features/audit/useChangedPaymentCount";

// Badgen renderas av SideBarItem, men regeln "visa bara vid > 0" ligger i SideBar,
// så det är SideBar som renderas här. Datan kommer från mockade hooks.
vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, ...props }: { children: ReactNode; "aria-label"?: string }) => (
    <a aria-label={props["aria-label"]} href="#">{children}</a>
  ),
  useNavigate: () => vi.fn(),
  useRouterState: () => "/",
}));

vi.mock("../../../features/auth/useUser", () => ({
  useUser: vi.fn(),
}));

vi.mock("../../../features/attest/useApprovals", () => ({
  usePendingApprovalCount: vi.fn(),
}));

vi.mock("../../../features/audit/useChangedPaymentCount", () => ({
  useChangedPaymentCount: vi.fn(() => ({ data: 0 })),
}));

const mockedUseUser = vi.mocked(useUser);
const mockedUsePendingApprovalCount = vi.mocked(usePendingApprovalCount);

function renderSideBarWithCount(count: number) {
  mockedUsePendingApprovalCount.mockReturnValue({ data: count } as ReturnType<typeof usePendingApprovalCount>);

  render(
    <QueryClientProvider client={new QueryClient()}>
      <SideBar />
    </QueryClientProvider>,
  );

  return screen.getByRole("link", { name: "Attestera" });
}

describe("Attestera-badge", () => {
  beforeEach(() => {
    mockedUseUser.mockReturnValue({
      data: { name: "Anna Attestant", role: "ATTESTANT" },
    } as ReturnType<typeof useUser>);
  });

  it("shows the number of pending approvals when there are some", () => {
    const attestLink = renderSideBarWithCount(3);

    expect(within(attestLink).getByText("3")).toBeInTheDocument();
  });

  it("shows no badge when there are no pending approvals", () => {
    const attestLink = renderSideBarWithCount(0);

    expect(within(attestLink).queryByText(/\d/)).not.toBeInTheDocument();
    expect(attestLink).toHaveTextContent(/^Attestera$/);
  });

  it("shows admin an overview and user administration, without payment creation", () => {
    mockedUseUser.mockReturnValue({ data: { name: "Admin", role: "ADMIN" } } as ReturnType<typeof useUser>);
    mockedUsePendingApprovalCount.mockReturnValue({ data: 2 } as ReturnType<typeof usePendingApprovalCount>);
    render(<QueryClientProvider client={new QueryClient()}><SideBar /></QueryClientProvider>);
    expect(screen.getByRole("link", { name: "Attestöversikt" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Användare" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Ny Betalning" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Mina betalningar" })).not.toBeInTheDocument();
  });
});

describe("Mina betalningar-badge", () => {
  const mockedUseChangedPaymentCount = vi.mocked(useChangedPaymentCount);

  function renderInitiatorWithCount(count: number) {
    mockedUseUser.mockReturnValue({
      data: { name: "Ivan Initiator", role: "INITIATOR" },
    } as ReturnType<typeof useUser>);
    mockedUsePendingApprovalCount.mockReturnValue({ data: undefined } as ReturnType<typeof usePendingApprovalCount>);
    mockedUseChangedPaymentCount.mockReturnValue({ data: count } as ReturnType<typeof useChangedPaymentCount>);

    render(
      <QueryClientProvider client={new QueryClient()}>
        <SideBar />
      </QueryClientProvider>,
    );

    return screen.getByRole("link", { name: /^Mina betalningar/ });
  }

  it("shows how many payments have been decided since the initiator last looked", () => {
    const myPaymentsLink = renderInitiatorWithCount(2);

    expect(within(myPaymentsLink).getByText("2")).toBeInTheDocument();
    expect(mockedUseChangedPaymentCount).toHaveBeenCalledWith(true);
  });

  it("shows no badge when nothing has changed", () => {
    const myPaymentsLink = renderInitiatorWithCount(0);

    expect(myPaymentsLink).toHaveTextContent(/^Mina betalningar$/);
  });

  it("is never fetched for an attestant, who has no changed-count endpoint", () => {
    mockedUseUser.mockReturnValue({
      data: { name: "Anna Attestant", role: "ATTESTANT" },
    } as ReturnType<typeof useUser>);
    render(<QueryClientProvider client={new QueryClient()}><SideBar /></QueryClientProvider>);

    expect(mockedUseChangedPaymentCount).toHaveBeenLastCalledWith(false);
  });
});
