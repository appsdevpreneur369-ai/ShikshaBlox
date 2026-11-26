import { defineConfig, devices } from "@playwright/test";

/**
 * E2E tests run against the full Docker Compose stack behind Caddy.
 * Start it first with `docker compose up -d --build`.
 */
const BASE_DOMAIN = process.env.SB_BASE_DOMAIN ?? "shikshablox.localhost";
const HTTP_PORT = process.env.SB_HTTP_PORT ?? "80";
const portSuffix = HTTP_PORT === "80" ? "" : `:${HTTP_PORT}`;

export const urls = {
  admin: `http://admin.${BASE_DOMAIN}${portSuffix}`,
  api: `http://api.${BASE_DOMAIN}${portSuffix}`,
  site: (sub: string) => `http://${sub}.${BASE_DOMAIN}${portSuffix}`,
};

export default defineConfig({
  testDir: "./tests",
  testIgnore: /www\.spec\.ts/, // product website: see playwright.www.config.ts
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  use: { trace: "retain-on-failure", screenshot: "only-on-failure" },
  projects: [
    { name: "desktop-chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile-chromium", use: { ...devices["Pixel 7"] } },
  ],
});
