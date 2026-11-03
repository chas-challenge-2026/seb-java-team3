import { expect, test } from "@playwright/test";
import { loginAs, USERS } from "./helpers.ts";

test("admin ser konton, användare och attestöversikt utan funktioner för egna betalningar", async ({ browser }) => {
  const admin = await loginAs(browser, USERS.admin);
  const initiatorRequests: string[] = [];
  admin.on("request", (request) => {
    if (/\/api\/(my-payments|payments\/(config|changed-count))/.test(request.url())) {
      initiatorRequests.push(request.url());
    }
  });
  await admin.reload();
  const menu = admin.getByRole("navigation", { name: "Huvudmeny" });
  await expect(admin.getByRole("heading", { name: "Konton", exact: true })).toBeVisible();
  await expect(menu.getByRole("link", { name: "Ny Betalning" })).toHaveCount(0);
  await expect(menu.getByRole("link", { name: "Mina betalningar" })).toHaveCount(0);
  for (const route of ["/payments/new", "/my-payments"]) {
    await admin.goto(route);
    await expect(admin).toHaveURL(/\/$/);
    await expect(admin.getByRole("heading", { name: "Konton", exact: true })).toBeVisible();
  }
  expect(initiatorRequests).toEqual([]);

  await menu.getByRole("link", { name: "Attestöversikt" }).click();
  await expect(admin.getByRole("heading", { name: "Väntande betalningar i företaget" })).toBeVisible();
  await admin.getByRole("button", { name: /Granska betalning/ }).first().click();
  const dialog = admin.getByRole("dialog", { name: "Betalningsöversikt" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("button", { name: /Godkänn|Avvisa/ })).toHaveCount(0);
  await dialog.getByRole("button", { name: "Tillbaka till attestöversikten" }).click();

  await menu.getByRole("link", { name: "Användare", exact: true }).click();
  await expect(admin.getByRole("cell", { name: "lisa@malmobygg.se" })).toBeVisible();
  await admin.context().close();
});
