import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import LoadErrorMessage from "./LoadErrorMessage";

describe("LoadErrorMessage", () => {
  it("calls onRetry when the user clicks retry", async () => {
    const onRetry = vi.fn();
    const user = userEvent.setup();
    render(<LoadErrorMessage title="Kunde inte hämta" onRetry={onRetry} isRetrying={false} />);

    await user.click(screen.getByRole("button", { name: "Försök igen" }));

    expect(onRetry).toHaveBeenCalledOnce();
  });

  it("disables the retry button while retrying", () => {
    render(<LoadErrorMessage title="Kunde inte hämta" onRetry={vi.fn()} isRetrying />);

    expect(screen.getByRole("button", { name: "Försöker igen…" })).toBeDisabled();
  });
});
