import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import SideBar from "./SideBar";
import { useUser } from "../../../features/auth/useUser";
import { usePendingApprovalCount } from "../../../features/attest/useApprovals";

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
});
