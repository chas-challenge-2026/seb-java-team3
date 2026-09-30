import { expect, test, type Page } from "@playwright/test";
import { loginAs, USERS } from "./helpers.ts";

// Över tröskeln för attest (payment.approval.no-attestant-threshold=5000)
const AMOUNT_INPUT = "5 500,50";
const AMOUNT_SHOWN = /5\s500,50/;
const RECIPIENT_IBAN = "DE89370400440532013000";

// MVP-tråden: logga in -> skapa betalning -> attest-badge -> godkänn -> saldo dras -> audit
test("MVP-tråden: en betalning över tröskeln skapas, attesteras och syns i audit", async ({
  browser,
}) => {
  // Unik referens så att testet hittar sin egen betalning oavsett vad som redan finns i databasen
  const reference = `E2E ${Date.now()}`;

  const initiator = await loginAs(browser, USERS.initiator);

  await test.step("initiatören skapar en betalning som kräver attest", async () => {
    await menu(initiator).getByRole("link", { name: "Ny Betalning" }).click();

    await initiator.getByRole("combobox", { name: /Konto/ }).click();
    await initiator.getByRole("option", { name: /Driftkonto/ }).click();
    await initiator.getByLabel("Mottagar-IBAN").fill(RECIPIENT_IBAN);
    await initiator.getByLabel("Belopp (SEK)").fill(AMOUNT_INPUT);
    await initiator.getByLabel("Referens").fill(reference);
    await initiator.getByRole("button", { name: "Skicka betalning" }).click();

    await expect(
      initiator.getByRole("heading", { name: "Betalningen har skickats" }),
    ).toBeVisible();
    await expect(initiator.getByText("Väntar på attest", { exact: true })).toBeVisible();
  });

  await test.step("betalningen väntar på attest i Mina betalningar", async () => {
    await menu(initiator).getByRole("link", { name: "Mina betalningar" }).click();

    await expect(rowWith(initiator, reference)).toContainText("Väntar attest");
  });

  const attestant = await loginAs(browser, USERS.attestant);
  const attestLink = menu(attestant).getByRole("link", { name: /Attestera/ });
  const badge = attestLink.locator("span").last();
  let pendingBefore = 0;

  await test.step("attestanten ser en badge med väntande attester", async () => {
    await expect(badge).toHaveText(/^\d+$/);
    pendingBefore = Number(await badge.textContent());

    expect(pendingBefore).toBeGreaterThan(0);
  });

  await test.step("attestanten godkänner betalningen", async () => {
    await attestLink.click();

    const row = rowWith(attestant, reference);
    await expect(row).toContainText(RECIPIENT_IBAN);
    await row.getByRole("button", { name: /Godkänn betalning/ }).click();

    await expect(attestant.getByText(/är godkänd\./).first()).toBeVisible();
    await expect(rowWith(attestant, reference)).toHaveCount(0);
  });

  await test.step("badgen räknas ned med ett", async () => {
    if (pendingBefore === 1) {
      // Sista väntande attesten: badgen försvinner helt
      await expect(attestLink).toHaveText("Attestera");
    } else {
      await expect(badge).toHaveText(String(pendingBefore - 1));
    }
  });

  await test.step("audit visar hela händelsekedjan med vem som gjorde vad", async () => {
    await menu(attestant).getByRole("link", { name: "Historik" }).click();

    const row = rowWith(attestant, reference);
    await expect(row).toContainText("Genomförd");
    await expect(row).toContainText(AMOUNT_SHOWN);
    await row.click();

    const timeline = attestant.getByRole("list").filter({ hasText: "Betalning skapad" });
    await expect(
      timeline.getByRole("listitem").filter({ hasText: "Betalning skapad" }),
    ).toContainText(USERS.initiator.name);
    await expect(
      timeline.getByRole("listitem").filter({ hasText: "Betalning godkänd" }),
    ).toContainText(USERS.attestant.name);
  });

  // Saldot exponeras varken i UI eller API än. Det dras i samma transaktion som sätter
  // betalningen till genomförd (ApprovalService.approve), så statusen är en indirekt kontroll.
  // Byt till en riktig saldokontroll när konton finns i frontend.
  await test.step("betalningen är genomförd för initiatören (saldo dras i samma transaktion)", async () => {
    await initiator.reload();

    const row = rowWith(initiator, reference);
    await expect(row).toContainText("Genomförd");
    await expect(row).not.toContainText("Ej utförd");
  });
});

function menu(page: Page) {
  return page.getByRole("navigation", { name: "Huvudmeny" });
}

function rowWith(page: Page, text: string) {
  return page.locator("tbody tr").filter({ hasText: text });
}
