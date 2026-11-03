import { expect, test } from "@playwright/test";
import { loginAs, USERS } from "./helpers.ts";

// Sessionen (ADR 0012): access-token hålls bara i minnet och förnyas tyst med refresh-cookien, som är
// HttpOnly och därför oläsbar för JavaScript. Testerna går genom den riktiga inloggningen mot en app som
// är igång, med backend som har /api/auth/refresh och /api/auth/logout.
test.describe("sessionen", () => {
  test("är kvar efter en omladdning, och ingen token ligger i localStorage", async ({ browser }) => {
    const page = await loginAs(browser, USERS.initiator);

    // Strängbaserade uttryck: e2e-konfigurationen har inga DOM-typer, så `document` och
    // `localStorage` går inte att skriva som kod här.
    expect(await page.evaluate("localStorage.getItem('authToken')")).toBeNull();

    // Efter omladdningen finns ingen access-token i minnet: appen måste förnya den tyst
    await page.reload();

    await expect(page.getByRole("navigation", { name: "Huvudmeny" })).toBeVisible();
    await expect(page).not.toHaveURL(/\/login/);
  });

  test("refresh-token är en HttpOnly-cookie som JavaScript inte kan läsa", async ({ browser }) => {
    const page = await loginAs(browser, USERS.initiator);

    const cookie = (await page.context().cookies()).find((c) => c.name === "refresh_token");

    expect(cookie).toBeDefined();
    expect(cookie).toMatchObject({ httpOnly: true, sameSite: "Strict", path: "/api/auth" });
    expect(await page.evaluate("document.cookie")).not.toContain("refresh_token");
  });

  test("utloggning avslutar sessionen, även efter en omladdning", async ({ browser }) => {
    const page = await loginAs(browser, USERS.initiator);

    await page.getByRole("button", { name: "Logga ut" }).click();
    await expect(page).toHaveURL(/\/login/);

    // Utan en giltig refresh-cookie går det inte att förnya sig tillbaka in
    await page.reload();

    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole("navigation", { name: "Huvudmeny" })).toHaveCount(0);
  });
});
