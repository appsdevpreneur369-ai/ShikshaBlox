import { defineConfig, devices } from "@playwright/test";

/**
 * Product website (apps/www) suite. Runs against any deployed or local URL:
 *   WWW_URL=http://localhost:3002 pnpm exec playwright test -c playwright.www.config.ts
 * Set WWW_E2E_SUBMIT=1 to also send one real demo request (staging only).
 */
const chromium = { browserName: "chromium" as const };

export default defineConfig({
  testDir: "./tests",
  testMatch: /www\.spec\.ts/,
  fullyParallel: true,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  timeout: 60_000,
  use: {
    baseURL: process.env.WWW_URL ?? "http://localhost:3002",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  // The 7 device profiles used for the school-site audit.
  projects: [
    { name: "galaxy-s8", use: { ...devices["Galaxy S8"], ...chromium } },
    { name: "iphone-13", use: { ...devices["iPhone 13"], ...chromium } },
    { name: "pixel-7-landscape", use: { ...devices["Pixel 7 landscape"], ...chromium } },
    { name: "ipad-mini", use: { ...devices["iPad Mini"], ...chromium } },
    { name: "ipad-landscape", use: { ...devices["iPad Pro 11 landscape"], ...chromium } },
    { name: "laptop", use: { ...devices["Desktop Chrome"], viewport: { width: 1280, height: 800 } } },
    { name: "desktop-wide", use: { ...devices["Desktop Chrome"], viewport: { width: 1920, height: 1080 } } },
  ],
});
