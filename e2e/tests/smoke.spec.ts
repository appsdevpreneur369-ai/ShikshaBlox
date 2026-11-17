import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";

// Navigated through the browser: browsers resolve *.localhost to loopback, but Node's resolver
// on Windows does not, so the APIRequestContext can't reach these hostnames directly.
test("backend is healthy behind the proxy", async ({ page }) => {
  const res = await page.goto(`${urls.api}/api/public/v1/system/ping`);
  expect(res?.ok()).toBeTruthy();
  expect(res?.headers()["x-request-id"]).toBeTruthy();
  expect(await res?.json()).toMatchObject({ status: "ok" });
});

test("a school subdomain renders that school's site", async ({ page }) => {
  await page.goto(urls.site("moriah"));
  await expect(page.getByRole("heading", { level: 1, name: "Moriah School" })).toBeVisible();
});

test("an unknown school subdomain returns 404", async ({ page }) => {
  const res = await page.goto(urls.site("no-such-school"));
  expect(res?.status()).toBe(404);
  await expect(page.getByText("We couldn't find that page")).toBeVisible();
});

test("admin login page is reachable and accessible", async ({ page }) => {
  await page.goto(`${urls.admin}/login`);
  await expect(page.getByRole("heading", { name: "Sign in to Studio" })).toBeVisible();
  await expect(page.getByLabel("Email")).toBeVisible();
});
