import { expect, test, type Page } from "@playwright/test";
import { urls } from "../playwright.config";

const PASSWORD = process.env.SEED_PASSWORD ?? "Shiksha@Dev2026";

async function signIn(page: Page, email: string) {
  await page.goto(`${urls.admin}/login`);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

test("Moriah is on Web Pro and Greenfield on Web Free", async ({ page }) => {
  await signIn(page, "admin@moriah.test");
  await page.getByRole("link", { name: "Plan & usage" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Web Pro" })).toBeVisible();
  await expect(page.getByText("Approval workflow")).toBeVisible();

  await page.getByRole("button", { name: "Sign out" }).click();
  await signIn(page, "admin@greenfield.test");
  await page.goto(`${urls.admin}/plan`);
  await expect(page.getByRole("heading", { level: 1, name: "Web Free" })).toBeVisible();
  await expect(page.getByText("Not included in this plan")).toBeVisible();
});

test("school admins see their audit log; editors do not", async ({ page }) => {
  await signIn(page, "admin@moriah.test");
  await page.getByRole("link", { name: "Audit log" }).click();
  await expect(page.getByRole("heading", { name: "Audit log" })).toBeVisible();
  await expect(page.getByRole("table")).toContainText("auth.login");

  await page.getByRole("button", { name: "Sign out" }).click();
  await signIn(page, "editor@moriah.test");
  await expect(page.getByRole("link", { name: "Audit log" })).toHaveCount(0);
});
