import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { myPaymentStatusSchema, type MyPaymentStatus } from "../schema";
import MyPaymentRow from "./MyPaymentRow";

// Testdata används bara här. Komponenten visar endast kommentaren från API:t.
function payment(overrides: Partial<MyPaymentStatus> = {}): MyPaymentStatus {
  return {
    paymentId: 233,
    reference: "Testfaktura 233",
    amount: 7500,
    currency: "SEK",
    fromAccountName: "Driftkonto",
    toIban: "DE89370400440532013000",
    status: "REJECTED",
    createdAt: "2026-10-28T10:00:00",
    executedAt: null,
    approvalSteps: [{
      stepNumber: 1,
      attestantName: "Testattestant",
      status: "REJECTED",
      decidedAt: "2026-10-28T10:05:00",
      comment: "Kontrollera fakturans referens.",
    }],
    ...overrides,
  };
}

function renderRows(...payments: MyPaymentStatus[]) {
  return render(
    <table><tbody>
      {payments.map((entry) => <MyPaymentRow key={entry.paymentId} payment={entry} />)}
    </tbody></table>,
  );
}

describe("MyPaymentRow", () => {
  it("keeps the reason collapsed until opened, then closes it again", async () => {
    const user = userEvent.setup();
    renderRows(payment());
    const toggle = screen.getByRole("button", { name: "Visa avvisningsorsak för betalning #233" });

    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("Kontrollera fakturans referens.")).not.toBeInTheDocument();
    expect(screen.getByText("Testfaktura 233")).toBeInTheDocument();
    expect(screen.getByText("Testattestant")).toBeInTheDocument();
    await user.click(toggle);

    expect(toggle).toHaveAttribute("aria-expanded", "true");
    const reason = screen.getByText("Kontrollera fakturans referens.");
    expect(document.getElementById(toggle.getAttribute("aria-controls")!)).toContainElement(reason);
    expect(reason.closest("td")).toHaveAttribute("colspan", "8");
    await user.click(toggle);

    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("Kontrollera fakturans referens.")).not.toBeInTheDocument();
  });

  it("supports Tab, Enter and Space and keeps focus on the toggle", async () => {
    const user = userEvent.setup();
    renderRows(payment());
    const toggle = screen.getByRole("button");

    await user.tab();
    expect(toggle).toHaveFocus();
    await user.keyboard("{Enter}");
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    await user.keyboard(" ");
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(toggle).toHaveFocus();
  });

  it.each(["PENDING_APPROVAL", "COMPLETED", "UNKNOWN"])(
    "does not offer a rejection dropdown for %s, even with an inconsistent rejected step",
    (status) => {
      renderRows(payment({ status }));
      expect(screen.queryByRole("button")).not.toBeInTheDocument();
      expect(screen.queryByText("Kontrollera fakturans referens.")).not.toBeInTheDocument();
    },
  );

  it.each([null, "", "   \n "])("explains that no reason was given for comment %j", async (comment) => {
    const user = userEvent.setup();
    const entry = payment();
    entry.approvalSteps[0].comment = comment;
    renderRows(entry);

    await user.click(screen.getByRole("button"));
    expect(screen.getByText("Ingen avvisningsorsak har angetts.")).toBeInTheDocument();
  });

  it("handles an API response without optional approval steps or comment", async () => {
    const user = userEvent.setup();
    const entry = payment();
    const withMissingComment = myPaymentStatusSchema.parse({
      ...entry, approvalSteps: [{ ...entry.approvalSteps[0], comment: undefined }],
    });
    renderRows(withMissingComment, myPaymentStatusSchema.parse({
      ...entry, paymentId: 234, approvalSteps: undefined,
    }));

    for (const toggle of screen.getAllByRole("button")) {
      await user.click(toggle);
    }
    expect(screen.getAllByText("Ingen avvisningsorsak har angetts.")).toHaveLength(2);
  });

  it("uses only the rejected step's comment and displays HTML as plain text", async () => {
    const user = userEvent.setup();
    const entry = payment();
    const reason = '<img src=x onerror="alert(1)">\n' + "Å".repeat(215);
    entry.approvalSteps = [
      { ...entry.approvalSteps[0], status: "APPROVED", comment: "Ska inte visas" },
      { ...entry.approvalSteps[0], stepNumber: 2, comment: reason },
    ];
    const { container } = renderRows(entry);

    await user.click(screen.getByRole("button"));
    expect(screen.getByText((_, element) => element?.tagName === "P" && element.textContent === reason)).toBeInTheDocument();
    expect(screen.queryByText("Ska inte visas")).not.toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
  });

  it("opens payment rows independently and gives each panel a unique ID", async () => {
    const user = userEvent.setup();
    const other = payment({ paymentId: 234, reference: "Testfaktura 234" });
    other.approvalSteps[0].comment = "En annan anledning.";
    renderRows(payment(), other);
    const [first, second] = screen.getAllByRole("button");

    await user.click(first);
    expect(second).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("En annan anledning.")).not.toBeInTheDocument();
    await user.click(second);
    expect(first.getAttribute("aria-controls")).not.toBe(second.getAttribute("aria-controls"));
    expect(screen.getByText("En annan anledning.")).toBeInTheDocument();
    await user.click(first);
    expect(second).toHaveAttribute("aria-expanded", "true");
  });
});
