import { expect, test, type Page } from "@playwright/test";
import { loginAs, USERS } from "./helpers.ts";

// Över tröskeln för attest (payment.approval.no-attestant-threshold=5000)
const AMOUNT_INPUT = "5 500,50";
const AMOUNT_SHOWN = /5\s500,50/;
const AMOUNT_ORE = 550050;
const ACCOUNT_NAME = "Driftkonto";
const RECIPIENT_IBAN = "DE89370400440532013000";

// MVP-tråden: logga in -> skapa betalning -> attest-badge -> godkänn -> saldo dras -> audit
test("MVP-tråden: en betalning över tröskeln skapas, attesteras och syns i audit", async ({
  browser,
}) => {
  // Unik referens så att testet hittar sin egen betalning oavsett vad som redan finns i databasen
  const reference = `E2E ${Date.now()}`;

  const initiator = await loginAs(browser, USERS.initiator);

  // Före och efter i stället för ett fast saldo: varje körning drar pengar i dev-databasen
  const balanceBefore = await accountBalanceInOre(initiator, ACCOUNT_NAME);

  await test.step("initiatören skapar en betalning som kräver attest", async () => {
    await menu(initiator).getByRole("link", { name: "Ny Betalning" }).click();

    await initiator.getByRole("combobox", { name: /Konto/ }).click();
    await initiator.getByRole("option", { name: new RegExp(ACCOUNT_NAME) }).click();
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
    // Initiatören ser vem betalningen väntar på, och vet vem som ska kontaktas om den fastnar
    await expect(rowWith(initiator, reference)).toContainText(USERS.attestant.name);
    await expect(rowWith(initiator, reference)).toContainText(ACCOUNT_NAME);
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
    await row.getByRole("button", { name: /Granska betalning/ }).click();
    const dialog = attestant.getByRole("dialog", { name: "Granska betalning" });
    await expect(dialog).toContainText(RECIPIENT_IBAN);
    await dialog.getByRole("button", { name: /^Godkänn 5\s500,50 SEK$/ }).click();
    await expect(dialog).toBeHidden();

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

    // Referensen visas bara när raden fälls ut, så raden hittas på beloppet. Loggen är sorterad med den
    // senaste händelsen först, och godkännandet nyss är den senaste.
    const row = attestant.locator("tbody tr").filter({ hasText: AMOUNT_SHOWN }).first();
    await expect(row).toContainText("Betalning godkänd");
    await expect(row).toContainText("Genomförd");
    await row.click();
    await expect(attestant.locator("tbody")).toContainText(reference);

    const timeline = attestant.getByRole("list").filter({ hasText: "Betalning skapad" });
    await expect(
      timeline.getByRole("listitem").filter({ hasText: "Betalning skapad" }),
    ).toContainText(USERS.initiator.name);
    await expect(
      timeline.getByRole("listitem").filter({ hasText: "Betalning godkänd" }),
    ).toContainText(USERS.attestant.name);
  });

  await test.step("betalningen är genomförd för initiatören", async () => {
    await initiator.reload();

    const row = rowWith(initiator, reference);
    await expect(row).toContainText("Genomförd");
    await expect(row).not.toContainText("Ej utförd");
  });

  // Saldot läses från GET /api/accounts, eftersom dashboarden fortfarande visar mockade konton.
  // Lägg till en kontroll i UI:t när saldovyn hämtar från backend.
  await test.step("saldot på kontot har dragits med exakt beloppet", async () => {
    const balanceAfter = await accountBalanceInOre(initiator, ACCOUNT_NAME);

    expect(balanceBefore - balanceAfter).toBe(AMOUNT_ORE);
  });
});

// Hämtar saldot med initiatörens egen token, så tenant-filtret i backend gäller som i appen.
// Räknar i öre för att slippa avrundningsfel med flyttal.
async function accountBalanceInOre(page: Page, accountName: string): Promise<number> {
  const token = await accessTokenFor(page);
  const response = await page.request.get("/api/accounts", {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok()).toBe(true);

  const accounts: { name: string; balance: number }[] = await response.json();
  const account = accounts.find((candidate) => candidate.name === accountName);
  expect(account, `kontot ${accountName} finns i /api/accounts`).toBeDefined();

  return Math.round(account!.balance * 100);
}

// Access-token finns bara i appens minne (ADR 0012), så testet hämtar en egen med refresh-cookien.
// page.request delar cookies med sidan: cookien roteras, men sidan ser den nya och märker inget.
async function accessTokenFor(page: Page): Promise<string> {
  const response = await page.request.post("/api/auth/refresh", {
    headers: { "X-Requested-With": "fetch" },
  });
  expect(response.ok(), "förnyelsen med refresh-cookien lyckas").toBe(true);

  const { token } = (await response.json()) as { token: string };
  return token;
}

function menu(page: Page) {
  return page.getByRole("navigation", { name: "Huvudmeny" });
}

function rowWith(page: Page, text: string) {
  return page.locator("tbody tr").filter({ hasText: text });
}
