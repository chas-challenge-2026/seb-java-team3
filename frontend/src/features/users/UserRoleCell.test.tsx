import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import UserRoleCell from "./UserRoleCell";
import { updateUserRole } from "./api";
import { ApiError } from "../../error/api.error";
import { admin, initiator, renderWithClient } from "./testUtils";

vi.mock("./api", () => ({
  listUsers: vi.fn(),
  createUser: vi.fn(),
  updateUserRole: vi.fn(),
}));

const mockedUpdateUserRole = vi.mocked(updateUserRole);

describe("UserRoleCell", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("only offers Attestant and Initiator", async () => {
    const user = userEvent.setup();
    renderWithClient(<UserRoleCell user={initiator} />);

    await user.click(screen.getByRole("combobox", { name: "Roll för Erik Eriksson" }));

    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual(["Attestant", "Initiator"]);
  });

  it("saves a new role and refreshes the list", async () => {
    mockedUpdateUserRole.mockResolvedValue({ ...initiator, role: "ATTESTANT" });
    const user = userEvent.setup();
    const { invalidate } = renderWithClient(<UserRoleCell user={initiator} />);

    await user.click(screen.getByRole("combobox", { name: "Roll för Erik Eriksson" }));
    await user.click(screen.getByRole("option", { name: "Attestant" }));

    expect(mockedUpdateUserRole).toHaveBeenCalledWith(initiator.id, "ATTESTANT");
    await vi.waitFor(() => expect(invalidate).toHaveBeenCalledWith({ queryKey: ["users"] }));
  });

  it("does not call the api when the same role is picked again", async () => {
    const user = userEvent.setup();
    renderWithClient(<UserRoleCell user={initiator} />);

    await user.click(screen.getByRole("combobox", { name: "Roll för Erik Eriksson" }));
    await user.click(screen.getByRole("option", { name: "Initiator" }));

    expect(mockedUpdateUserRole).not.toHaveBeenCalled();
  });

  it("disables the dropdown while saving", async () => {
    mockedUpdateUserRole.mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    renderWithClient(<UserRoleCell user={initiator} />);

    await user.click(screen.getByRole("combobox", { name: "Roll för Erik Eriksson" }));
    await user.click(screen.getByRole("option", { name: "Attestant" }));

    expect(screen.getByRole("combobox", { name: "Roll för Erik Eriksson" })).toBeDisabled();
  });

  it("shows the backend error when the change fails", async () => {
    mockedUpdateUserRole.mockRejectedValue(new ApiError(403, "Du får inte ändra den rollen."));
    const user = userEvent.setup();
    renderWithClient(<UserRoleCell user={initiator} />);

    await user.click(screen.getByRole("combobox", { name: "Roll för Erik Eriksson" }));
    await user.click(screen.getByRole("option", { name: "Attestant" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Du får inte ändra den rollen.");
  });

  it("renders the admin role as plain text", () => {
    renderWithClient(<UserRoleCell user={admin} />);

    expect(screen.getByText("Administratör")).toBeInTheDocument();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });
});
