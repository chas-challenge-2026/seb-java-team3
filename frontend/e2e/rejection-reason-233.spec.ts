import { expect, test } from "@playwright/test";
import { loginAs, USERS } from "./helpers.ts";

// Hela flödet använder den riktiga lokala backendens data, utan route-mockar.
// Varje test skapar och avvisar bara sin egen betalning.
const reason = "Referensen behöver rättas. " + "Å".repeat(200);

for (const comment of [reason, ""]) {
  test(`#233: initiatorn ser ${comment ? "sparad avvisningsorsak" : "att orsak saknas"} på sin betalning`, async ({ browser }) => {
    const reference = `E2E #233 ${comment ? "orsak" : "tom"} ${Date.now()}`;
    const initiator = await loginAs(browser, USERS.initiator);
    const initiatorMenu = initiator.getByRole("navigation", { name: "Huvudmeny" });

    await initiatorMenu.getByRole("link", { name: "Ny Betalning", exact: true }).click();
    await initiator.getByRole("combobox", { name: "Konto", exact: true }).click();
    await initiator.getByRole("option", { name: /Driftkonto/ }).click();
    await initiator.getByLabel("Mottagar-IBAN").fill("DE89370400440532013000");
    await initiator.getByLabel("Belopp (SEK)").fill("7 500");
    await initiator.getByLabel("Referens", { exact: true }).fill(reference);
    await initiator.getByRole("button", { name: "Skicka betalning", exact: true }).click();
    await expect(initiator.getByRole("heading", { name: "Betalningen har skickats" })).toBeVisible();
    await initiatorMenu.getByRole("link", { name: "Mina betalningar", exact: true }).click();

    const paymentRow = initiator.getByRole("row").filter({ hasText: reference });
    await expect(paymentRow).toContainText("Väntar attest");
    await expect(paymentRow.getByRole("button")).toHaveCount(0);

    const attestant = await loginAs(browser, USERS.attestant);
    await attestant.getByRole("navigation", { name: "Huvudmeny" })
      .getByRole("link", { name: /Attestera/ }).click();
    await attestant.getByRole("row").filter({ hasText: reference })
      .getByRole("button", { name: /Granska betalning/ }).click();
    await attestant.getByRole("dialog").getByRole("button", { name: "Avvisa betalningen", exact: true }).click();
    await attestant.getByLabel("Anledning (valfritt)", { exact: true }).fill(comment);
    const rejectionResponse = attestant.waitForResponse((response) =>
      response.url().includes("/api/approvals/") && response.url().endsWith("/reject")
        && response.request().method() === "POST");
    await attestant.getByRole("button", { name: "Bekräfta avvisning", exact: true }).click();
    expect((await rejectionResponse).ok()).toBe(true);
    await expect(attestant.getByRole("dialog")).toBeHidden();

    // Omladdningen läser den sparade kommentaren från API:t och testar även sessionen.
    const statusesResponse = initiator.waitForResponse((response) =>
      response.url().endsWith("/api/my-payments") && response.request().method() === "GET");
    await initiator.reload();
    const response = await statusesResponse;
    expect(response.ok()).toBe(true);
    const payments: {
      reference: string;
      status: string;
      approvalSteps: { status: string; comment: string | null }[];
    }[] = await response.json();
    const saved = payments.find((payment) => payment.reference === reference);
    expect(saved?.status).toBe("REJECTED");
    expect(saved?.approvalSteps.find((step) => step.status === "REJECTED")?.comment)
      .toBe(comment || null);

    await expect(paymentRow).toContainText("Avvisad");
    const toggle = paymentRow.getByRole("button", { name: /Visa avvisningsorsak/ });
    await expect(toggle).toHaveAttribute("aria-expanded", "false");
    const details = paymentRow.locator("xpath=following-sibling::tr[1]");
    await expect(details.getByRole("heading", { name: "Avvisningsorsak", exact: true })).toHaveCount(0);

    await toggle.press("Enter");
    const expandedToggle = paymentRow.getByRole("button", { name: /Dölj avvisningsorsak/ });
    await expect(expandedToggle).toHaveAttribute("aria-expanded", "true");
    await expect(expandedToggle).toBeFocused();
    await expect(details.getByRole("heading", { name: "Avvisningsorsak", exact: true })).toBeVisible();
    await expect(details.locator("p")).toHaveText(comment || "Ingen avvisningsorsak har angetts.");

    // Den långa kommentaren ska vara läsbar utan att sidan flyter ut på mobil.
    await initiator.setViewportSize({ width: 390, height: 844 });
    await expect(details.locator("p")).toBeVisible();
    expect(await initiator.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).toBe(true);
    await expandedToggle.press("Space");
    await expect(paymentRow.getByRole("button", { name: /Visa avvisningsorsak/ }))
      .toHaveAttribute("aria-expanded", "false");
    await expect(paymentRow.locator("xpath=following-sibling::tr[1]")
      .getByRole("heading", { name: "Avvisningsorsak", exact: true })).toHaveCount(0);

    await attestant.context().close();
    await initiator.context().close();
  });
}
