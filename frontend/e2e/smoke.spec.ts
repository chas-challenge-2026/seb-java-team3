import { expect, test } from "@playwright/test";

// Verifierar bara att Playwright når den körande appen via baseURL.
test("appen svarar på baseURL", async ({ page }) => {
  const response = await page.goto("/");

  expect(response?.ok()).toBe(true);
  await expect(page.locator("#root")).not.toBeEmpty();
});
