import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import UserList from "./UserList";
import { deleteUser, listUsers } from "./api";
import { ApiError } from "../../error/api.error";
import { admin, attestant, initiator, renderWithClient } from "./testUtils";

vi.mock("./api", () => ({
  listUsers: vi.fn(),
  createUser: vi.fn(),
  updateUserRole: vi.fn(),
  deleteUser: vi.fn(),
}));

const mockedListUsers = vi.mocked(listUsers);
const mockedDeleteUser = vi.mocked(deleteUser);

describe("UserList", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("shows a loading message while users are fetched", () => {
    mockedListUsers.mockReturnValue(new Promise(() => {}));
    renderWithClient(<UserList />);

    expect(screen.getByText("Laddar användare…")).toBeInTheDocument();
  });

  it("shows an error with a retry button when the fetch fails", async () => {
    mockedListUsers.mockRejectedValueOnce(new ApiError(500, "Serverfel"));
    const user = userEvent.setup();
    renderWithClient(<UserList />);

    expect(await screen.findByText("Kunde inte hämta användarna")).toBeInTheDocument();

    mockedListUsers.mockResolvedValueOnce([initiator]);
    await user.click(screen.getByRole("button", { name: "Försök igen" }));

    expect(await screen.findByText("Erik Eriksson")).toBeInTheDocument();
  });

  it("shows an empty state when there are no users", async () => {
    mockedListUsers.mockResolvedValue([]);
    renderWithClient(<UserList />);

    expect(await screen.findByText("Inga användare än")).toBeInTheDocument();
    // Skapa-knappen finns kvar så att man kan lägga till den första
    expect(screen.getByRole("button", { name: "Skapa användare" })).toBeInTheDocument();
  });

  it("lists name, email and role for every user", async () => {
    mockedListUsers.mockResolvedValue([admin, initiator, attestant]);
    renderWithClient(<UserList />);

    const row = (await screen.findByText("Erik Eriksson")).closest("tr")!;
    expect(within(row).getByText("erik@foretag.se")).toBeInTheDocument();
    expect(within(row).getByRole("combobox", { name: "Roll för Erik Eriksson" })).toHaveTextContent("Initiator");

    expect(screen.getByRole("combobox", { name: "Roll för Sara Svensson" })).toHaveTextContent("Attestant");
  });

  it("shows the admin without role dropdown or delete button", async () => {
    mockedListUsers.mockResolvedValue([admin, initiator]);
    renderWithClient(<UserList />);

    const row = (await screen.findByText("Anna Admin")).closest("tr")!;
    expect(within(row).getByText("Administratör")).toBeInTheDocument();
    expect(within(row).queryByRole("combobox")).not.toBeInTheDocument();
    expect(within(row).queryByRole("button")).not.toBeInTheDocument();
  });

  it("opens the create modal from the button", async () => {
    mockedListUsers.mockResolvedValue([initiator]);
    const user = userEvent.setup();
    renderWithClient(<UserList />);

    await screen.findByText("Erik Eriksson");
    await user.click(screen.getByRole("button", { name: "Skapa användare" }));

    expect(within(screen.getByRole("dialog")).getByRole("heading", { name: "Skapa användare" })).toBeInTheDocument();
  });

  describe("deleting", () => {
    beforeEach(() => {
      mockedListUsers.mockResolvedValue([admin, initiator]);
    });

    it("asks for confirmation and does nothing when the user keeps the account", async () => {
      const user = userEvent.setup();
      renderWithClient(<UserList />);

      await user.click(await screen.findByRole("button", { name: "Ta bort Erik Eriksson" }));

      const dialog = screen.getByRole("dialog");
      expect(within(dialog).getByText(/^Borttagningen är permanent/)).toBeInTheDocument();
      expect(within(dialog).getByText("erik@foretag.se")).toBeInTheDocument();

      await user.click(within(dialog).getByRole("button", { name: "Nej, behåll användaren" }));

      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      expect(mockedDeleteUser).not.toHaveBeenCalled();
    });

    it("deletes the user, refreshes the list and closes the modal on confirm", async () => {
      mockedDeleteUser.mockResolvedValue(undefined);
      const user = userEvent.setup();
      const { invalidate } = renderWithClient(<UserList />);

      await user.click(await screen.findByRole("button", { name: "Ta bort Erik Eriksson" }));
      mockedListUsers.mockResolvedValue([admin]);
      await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Ja, ta bort permanent" }));

      expect(mockedDeleteUser).toHaveBeenCalledWith(initiator.id);
      expect(await screen.findByRole("heading", { name: "Användare" })).toBeInTheDocument();
      await vi.waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ["users"] });
      await vi.waitFor(() => expect(screen.queryByText("Erik Eriksson")).not.toBeInTheDocument());
    });

    it("keeps the modal open and shows the error when deleting fails", async () => {
      mockedDeleteUser.mockRejectedValue(new ApiError(403, "Administratören kan inte tas bort."));
      const user = userEvent.setup();
      renderWithClient(<UserList />);

      await user.click(await screen.findByRole("button", { name: "Ta bort Erik Eriksson" }));
      await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Ja, ta bort permanent" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("Administratören kan inte tas bort.");
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });
  });
});
