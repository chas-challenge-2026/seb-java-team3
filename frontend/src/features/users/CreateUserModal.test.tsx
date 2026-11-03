import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import CreateUserModal from "./CreateUserModal";
import { createUser } from "./api";
import { ApiError } from "../../error/api.error";
import { renderWithClient } from "./testUtils";

vi.mock("./api", () => ({
  listUsers: vi.fn(),
  createUser: vi.fn(),
  updateUserRole: vi.fn(),
}));

const mockedCreateUser = vi.mocked(createUser);

async function fillForm(user: ReturnType<typeof userEvent.setup>, overrides: { name?: string; email?: string; role?: string; password?: string } = {}) {
  const { name = "Kalle Kula", email = "kalle@foretag.se", role = "Initiator", password = "hemligt-lösen-123" } = overrides;

  if (name) await user.type(screen.getByLabelText(/^Namn/), name);
  if (email) await user.type(screen.getByLabelText(/^E-post/), email);
  if (role) {
    await user.click(screen.getByRole("combobox", { name: /^Roll/ }));
    await user.click(screen.getByRole("option", { name: role }));
  }
  if (password) await user.type(screen.getByLabelText(/^Startlösenord/), password);
}

function submit(user: ReturnType<typeof userEvent.setup>) {
  return user.click(screen.getByRole("button", { name: "Skapa användare" }));
}

describe("CreateUserModal", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("only offers Attestant and Initiator as roles, never admin", async () => {
    const user = userEvent.setup();
    renderWithClient(<CreateUserModal onClose={vi.fn()} />);

    await user.click(screen.getByRole("combobox", { name: /^Roll/ }));

    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual(["Attestant", "Initiator"]);
  });

  it("shows errors for every empty required field and does not submit", async () => {
    const user = userEvent.setup();
    renderWithClient(<CreateUserModal onClose={vi.fn()} />);

    await submit(user);

    expect(screen.getByText("Ange namn.")).toBeInTheDocument();
    expect(screen.getByText("Ange din e-postadress.")).toBeInTheDocument();
    expect(screen.getByText("Välj en roll.")).toBeInTheDocument();
    expect(screen.getByText("Startlösenordet måste vara minst 12 tecken.")).toBeInTheDocument();
    expect(mockedCreateUser).not.toHaveBeenCalled();
  });

  it("rejects an invalid email format", async () => {
    const user = userEvent.setup();
    renderWithClient(<CreateUserModal onClose={vi.fn()} />);

    await fillForm(user, { email: "inte-en-epost" });
    await submit(user);

    expect(screen.getByText("Ange en giltig e-postadress.")).toBeInTheDocument();
    expect(mockedCreateUser).not.toHaveBeenCalled();
  });

  it("clears a field error once the user edits that field", async () => {
    const user = userEvent.setup();
    renderWithClient(<CreateUserModal onClose={vi.fn()} />);

    await submit(user);
    await user.type(screen.getByLabelText(/^Namn/), "K");

    expect(screen.queryByText("Ange namn.")).not.toBeInTheDocument();
  });

  it("submits the validated values", async () => {
    mockedCreateUser.mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    renderWithClient(<CreateUserModal onClose={vi.fn()} />);

    await fillForm(user, { name: "  Kalle Kula  " });
    await submit(user);

    expect(mockedCreateUser).toHaveBeenCalledWith({
      name: "Kalle Kula",
      email: "kalle@foretag.se",
      role: "INITIATOR",
      password: "hemligt-lösen-123",
    });
  });

  it("disables the submit button while the request is pending", async () => {
    mockedCreateUser.mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    renderWithClient(<CreateUserModal onClose={vi.fn()} />);

    await fillForm(user);
    await submit(user);

    expect(screen.getByRole("button", { name: "Skapar…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Avbryt" })).toBeDisabled();
  });

  it("shows the backend error for a duplicate email at the email field", async () => {
    mockedCreateUser.mockRejectedValue(new ApiError(409, "E-postadressen används redan."));
    const user = userEvent.setup();
    renderWithClient(<CreateUserModal onClose={vi.fn()} />);

    await fillForm(user);
    await submit(user);

    expect(await screen.findByText("E-postadressen används redan.")).toBeInTheDocument();
    expect(screen.getByLabelText(/^E-post/)).toHaveAttribute("aria-invalid", "true");
    // Formuläret finns kvar med ifyllda värden så att man kan rätta e-postadressen
    expect(screen.getByLabelText(/^Namn/)).toHaveValue("Kalle Kula");
  });

  it("shows other backend errors above the buttons", async () => {
    mockedCreateUser.mockRejectedValue(new ApiError(0, "Kunde inte nå servern."));
    const user = userEvent.setup();
    renderWithClient(<CreateUserModal onClose={vi.fn()} />);

    await fillForm(user);
    await submit(user);

    expect(await screen.findByRole("alert")).toHaveTextContent("Kunde inte nå servern. Användaren har inte skapats.");
  });

  it("shows a confirmation and refreshes the user list after creating", async () => {
    mockedCreateUser.mockResolvedValue({ id: 9, name: "Kalle Kula", email: "kalle@foretag.se", role: "INITIATOR" });
    const onClose = vi.fn();
    const user = userEvent.setup();
    const { invalidate } = renderWithClient(<CreateUserModal onClose={onClose} />);

    await fillForm(user);
    await submit(user);

    expect(await screen.findByRole("heading", { name: "Användaren har skapats" })).toBeInTheDocument();
    expect(screen.getByText("kalle@foretag.se")).toBeInTheDocument();
    expect(screen.getByText("Initiator")).toBeInTheDocument();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["users"] });

    await user.click(screen.getByRole("button", { name: "Klar" }));
    expect(onClose).toHaveBeenCalled();
  });

  it("returns to an empty form from the confirmation", async () => {
    mockedCreateUser.mockResolvedValue({ id: 9, name: "Kalle Kula", email: "kalle@foretag.se", role: "INITIATOR" });
    const user = userEvent.setup();
    renderWithClient(<CreateUserModal onClose={vi.fn()} />);

    await fillForm(user);
    await submit(user);
    await user.click(await screen.findByRole("button", { name: "Skapa en till" }));

    expect(screen.getByLabelText(/^Namn/)).toHaveValue("");
    expect(screen.getByLabelText(/^E-post/)).toHaveValue("");
  });
});
