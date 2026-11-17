import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";

// Seeded, fictional accounts (see backend seed module). Password from SEED_PASSWORD.
const PASSWORD = process.env.SEED_PASSWORD ?? "Shiksha@Dev2026";

test("studio pages require sign-in", async ({ page }) => {
  await page.goto(`${urls.admin}/dashboard`);
  await expect(page).toHaveURL(/\/login$/);
});

test("a wrong password shows a generic error", async ({ page }) => {
  await page.goto(`${urls.admin}/login`);
  await page.getByLabel("Email").fill("admin@moriah.test");
  await page.getByLabel("Password").fill("definitely-wrong");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(
    page.getByRole("alert").filter({ hasText: "email or password is incorrect" }),
  ).toBeVisible();
});

test("a school admin signs in, sees their school and signs out", async ({ page }) => {
  await page.goto(`${urls.admin}/login`);
  await page.getByLabel("Email").fill("admin@moriah.test");
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: /Welcome/ })).toBeVisible();
  await expect(page.getByText("You are working on Moriah School.")).toBeVisible();
  await expect(page.getByText("content:publish")).toBeVisible();

  // Session cookies are httpOnly: page scripts cannot read them.
  expect(await page.evaluate(() => document.cookie)).not.toContain("sb_");

  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto(`${urls.admin}/dashboard`);
  await expect(page).toHaveURL(/\/login$/);
});

test("a platform admin is required to set up an authenticator", async ({ page }) => {
  await page.goto(`${urls.admin}/login`);
  await page.getByLabel("Email").fill("superadmin@shikshablox.test");
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  // First sign-in enrols; later sign-ins ask for the code.
  await expect(page).toHaveURL(/\/login\/mfa(-setup)?$/);
  await expect(page.getByLabel("Authenticator code")).toBeVisible();
});
