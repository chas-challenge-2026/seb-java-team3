import { defineConfig, devices } from "@playwright/test";

// E2E körs mot en redan startad app, Playwright startar ingen server själv.
// Default är Vite dev-servern (npm run dev, port 3000, proxar /api till 8084).
// Peka om med E2E_BASE_URL, t.ex. mot Docker-bygget: E2E_BASE_URL=http://localhost:8084
const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:3000";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "html",
  use: {
    baseURL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
