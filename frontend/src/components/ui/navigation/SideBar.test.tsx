import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import SideBar from "./SideBar";
import { useUser } from "../../../features/auth/useUser";
import type { UserRole } from "../../../features/auth/types";

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, to, ...props }: { children: ReactNode; to: string }) => (
    <a href={to} {...props}>{children}</a>
  ),
  useRouterState: () => "/",
}));

vi.mock("../../../features/auth/useUser", () => ({
  useUser: vi.fn(),
}));

vi.mock("../../../features/auth/useLogout", () => ({
  useLogout: () => vi.fn(),
}));

vi.mock("../../../features/attest/useApprovals", () => ({
  usePendingApprovalCount: () => ({ data: 0 }),
}));

const mockedUseUser = vi.mocked(useUser);

function renderAs(role: UserRole) {
  mockedUseUser.mockReturnValue({
    data: { id: 1, name: "Test Testsson", email: "test@example.com", role },
  } as unknown as ReturnType<typeof useUser>);
  render(<SideBar />);
}

describe("SideBar user administration link", () => {
  beforeEach(() => {
    mockedUseUser.mockReset();
  });

  it("is shown to admins and points to /admin/users", () => {
    renderAs("ADMIN");

    expect(screen.getByRole("link", { name: "Användare" })).toHaveAttribute("href", "/admin/users");
  });

  it.each(["ATTESTANT", "INITIATOR"] as const)("is hidden from %s", (role) => {
    renderAs(role);

    expect(screen.queryByRole("link", { name: "Användare" })).not.toBeInTheDocument();
  });
});
