import { expect, type Browser, type Page } from "@playwright/test";

// Användare från dev-seeden (backend: V5__dev_seed_data.sql), tenant Malmö Bygg AB
export const USERS = {
  initiator: { name: "Lisa Persson", email: "lisa@malmobygg.se" },
  attestant: { name: "Johan Berg", email: "johan@malmobygg.se" },
} as const;

const SEED_PASSWORD = "password123";

type User = (typeof USERS)[keyof typeof USERS];

// Varje användare får en egen webbläsarkontext, som två personer vid var sin dator
export async function loginAs(browser: Browser, user: User): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();

  await page.goto("/login");
  await page.getByLabel("E-post").fill(user.email);
  await page.getByLabel("Lösenord", { exact: true }).fill(SEED_PASSWORD);
  await page.getByRole("button", { name: "Logga in" }).click();

  await expect(page.getByRole("navigation", { name: "Huvudmeny" })).toBeVisible();

  return page;
}
