import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import LoginForm from "./LoginForm";
import { useLogin } from "./useLogin";

vi.mock("./useLogin", () => ({
  useLogin: vi.fn(),
}));

const mockedUseLogin = vi.mocked(useLogin);

function mockLoginState(overrides: Partial<ReturnType<typeof useLogin>> = {}) {
  mockedUseLogin.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    error: null,
    ...overrides,
  } as unknown as ReturnType<typeof useLogin>);
}

describe("LoginForm", () => {
  beforeEach(() => {
    mockLoginState();
  });

  it("does not call login when the email is invalid", async () => {
    const user = userEvent.setup();
    render(<LoginForm />);

    await user.type(screen.getByLabelText("E-post"), "not-an-email");
    await user.type(screen.getByLabelText("Lösenord"), "hunter2");
    await user.click(screen.getByRole("button", { name: /logga in/i }));

    expect(screen.getByText("Ange en giltig e-postadress.")).toBeInTheDocument();
    expect(mockedUseLogin().mutate).not.toHaveBeenCalled();
  });

  it("shows an error when the password is empty", async () => {
    const user = userEvent.setup();
    render(<LoginForm />);

    await user.type(screen.getByLabelText("E-post"), "user@example.com");
    await user.click(screen.getByRole("button", { name: /logga in/i }));

    expect(screen.getByText("Ange ditt lösenord.")).toBeInTheDocument();
  });

  it("calls login with the trimmed, validated credentials", async () => {
    const mutate = vi.fn();
    mockLoginState({ mutate });

    const user = userEvent.setup();
    render(<LoginForm />);

    await user.type(screen.getByLabelText("E-post"), "  user@example.com  ");
    await user.type(screen.getByLabelText("Lösenord"), "hunter2");
    await user.click(screen.getByRole("button", { name: /logga in/i }));

    expect(mutate).toHaveBeenCalledWith({
      email: "user@example.com",
      password: "hunter2",
    });
  });

  it("disables the submit button while a login is pending", () => {
    mockLoginState({ isPending: true });

    render(<LoginForm />);

    expect(screen.getByRole("button", { name: /loggar in/i })).toBeDisabled();
  });
});
