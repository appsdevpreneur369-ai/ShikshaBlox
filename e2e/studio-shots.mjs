// Ad-hoc Studio screenshots (not a test): node studio-shots.mjs <outDir>
import { chromium } from "@playwright/test";
const [out] = process.argv.slice(2);
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, reducedMotion: "reduce" });
await page.goto("http://admin.shikshablox.localhost/login");
await page.getByLabel("Email").fill("admin@moriah.test");
await page.getByLabel("Password").fill(process.env.SEED_PASSWORD ?? "Shiksha@Dev2026");
await page.getByRole("button", { name: "Sign in" }).click();
await page.waitForURL(/dashboard/);
for (const [name, path] of [["pages", "/pages"], ["content", "/content/events"], ["enquiries", "/enquiries"], ["media", "/media"], ["design", "/design"]]) {
  await page.goto(`http://admin.shikshablox.localhost${path}`, { waitUntil: "networkidle" });
  await page.waitForTimeout(800);
  await page.screenshot({ path: `${out}/s-${name}.png`, fullPage: true });
}
await page.goto("http://admin.shikshablox.localhost/pages", { waitUntil: "networkidle" });
await page.getByRole("link", { name: "Home", exact: true }).click();
await page.waitForLoadState("networkidle");
await page.getByRole("button", { name: /Hero banner/ }).click();
await page.waitForTimeout(800);
await page.screenshot({ path: `${out}/s-editor.png`, fullPage: false });
await browser.close();
