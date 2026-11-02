import { expect, test, type Page } from "@playwright/test";
import { loginAs, USERS } from "./helpers.ts";

// ADR 0016: en betalning över 50 000 kr kräver två attestanter i tur och ordning. Hela flödet går mot den
// riktiga backenden, utan route-mockar, och varje användare har en egen webbläsarkontext.
test("dubbel attest: betalningen genomförs först när båda attestanterna har godkänt", async ({ browser }) => {
  const reference = `E2E dubbel attest ${Date.now()}`;
  const initiator = await loginAs(browser, USERS.initiator);
  const initiatorMenu = initiator.getByRole("navigation", { name: "Huvudmeny" });

  await test.step("initiatören skapar en betalning över tröskeln", async () => {
    await initiatorMenu.getByRole("link", { name: "Ny Betalning", exact: true }).click();
    await expect(initiator.getByText(/och över 50\s000\skr av två attestanter/)).toBeVisible();
    await initiator.getByRole("combobox", { name: "Konto", exact: true }).click();
    await initiator.getByRole("option", { name: /Driftkonto/ }).click();
    await initiator.getByLabel("Mottagar-IBAN").fill("DE89370400440532013000");
    await initiator.getByLabel("Belopp (SEK)").fill("60 000");
    await initiator.getByLabel("Referens", { exact: true }).fill(reference);
    await initiator.getByRole("button", { name: "Skicka betalning", exact: true }).click();
    await initiator.getByRole("dialog", { name: "Bekräfta betalning" })
      .getByRole("button", { name: "Bekräfta och skicka betalning", exact: true }).click();
    await initiator.getByRole("dialog", { name: "Betalningen har skickats" })
      .getByRole("button", { name: "Gå till Översikt", exact: true }).click();
  });

  const paymentRow = initiator.getByRole("row").filter({ hasText: reference });

  await test.step("betalningen väntar på attest 1 av 2", async () => {
    await initiatorMenu.getByRole("link", { name: "Mina betalningar", exact: true }).click();
    await expect(paymentRow).toContainText("Väntar attest 1 av 2");
    await expect(paymentRow).toContainText(USERS.attestant.name);
  });

  const secondAttestant = await loginAs(browser, USERS.secondAttestant);
  await openAttestQueue(secondAttestant);

  await test.step("attest 2 syns inte förrän attest 1 är klar", async () => {
    await expect(secondAttestant.getByRole("row").filter({ hasText: reference })).toHaveCount(0);
  });

  const attestant = await loginAs(browser, USERS.attestant);
  await openAttestQueue(attestant);

  await test.step("första attestanten godkänner, och betalningen är inte genomförd", async () => {
    await expect(attestant.getByRole("row").filter({ hasText: reference })).toContainText("Attest 1 av 2");
    await approve(attestant, reference);
    await expect(attestant.getByText(/Den genomförs först när attest 2 av 2 är klar\./)).toBeVisible();
    await expect(attestant.getByRole("row").filter({ hasText: reference })).toHaveCount(0);

    await initiator.reload();
    await expect(paymentRow).toContainText("Väntar attest 2 av 2");
    await expect(paymentRow).toContainText(USERS.secondAttestant.name);
  });

  await test.step("andra attestanten godkänner, och betalningen genomförs", async () => {
    await secondAttestant.reload();
    await expect(secondAttestant.getByRole("row").filter({ hasText: reference })).toContainText("Attest 2 av 2");
    await approve(secondAttestant, reference, USERS.attestant.name);
    await expect(secondAttestant.getByText(/är godkänd\./).first()).toBeVisible();

    await initiator.reload();
    await expect(paymentRow).toContainText("Genomförd");
    await expect(paymentRow).toContainText(`${USERS.attestant.name}, ${USERS.secondAttestant.name}`);
  });

  await attestant.context().close();
  await secondAttestant.context().close();
  await initiator.context().close();
});

async function openAttestQueue(page: Page) {
  await page.getByRole("navigation", { name: "Huvudmeny" }).getByRole("link", { name: /Attestera/ }).click();
  // Korgen ska ha laddat, annars säger det inget att en rad saknas
  await expect(page.getByRole("heading", { name: /Väntar på din attest|Allt är klart/ })).toBeVisible();
}

async function approve(page: Page, reference: string, previousApprover?: string) {
  await page.getByRole("row").filter({ hasText: reference })
    .getByRole("button", { name: /Granska betalning/ }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toContainText(/\d av 2/);
  if (previousApprover) {
    await expect(dialog).toContainText(`Attest 1 godkänd av${previousApprover}`);
  }
  const response = page.waitForResponse((candidate) =>
    candidate.url().includes("/api/approvals/") && candidate.url().endsWith("/approve")
      && candidate.request().method() === "POST");
  await dialog.getByRole("button", { name: /^Godkänn / }).click();
  expect((await response).ok()).toBe(true);
  await expect(dialog).toBeHidden();
}
