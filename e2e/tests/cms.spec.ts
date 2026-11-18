import { expect, test, type Page } from "@playwright/test";
import { urls } from "../playwright.config";

/**
 * M2–M5 end to end on the seeded Moriah School site: public pages, enquiry form → Studio inbox,
 * page editing → publish → live site, content items → feeds, draft preview and rollback.
 */
const PASSWORD = process.env.SEED_PASSWORD ?? "Shiksha@Dev2026";
const SITE = urls.site("moriah");

test.describe.configure({ mode: "serial" });
test.skip(({ isMobile }) => isMobile, "Studio flows run on desktop");

/** Fetches a public page through a browser tab (Node cannot resolve *.localhost on Windows). */
async function siteHtml(page: Page, path: string): Promise<string> {
  const tab = await page.context().newPage();
  try {
    await tab.goto(`${SITE}${path}`);
    return await tab.content();
  } finally {
    await tab.close();
  }
}

let session: Awaited<ReturnType<import("@playwright/test").BrowserContext["storageState"]>> | undefined;

/** Signs in once per run (sign-in is rate limited) and reuses the session cookies. */
async function signIn(page: Page) {
  if (session) {
    await page.context().addCookies(session.cookies);
    await page.goto(`${urls.admin}/dashboard`);
    if (/\/dashboard$/.test(page.url())) return;
  }
  await page.goto(`${urls.admin}/login`);
  await page.getByLabel("Email").fill("admin@moriah.test");
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  session = await page.context().storageState();
}

test("the public site renders every standard page", async ({ page }) => {
  for (const path of ["/", "/about", "/academics", "/admissions", "/facilities", "/faculty", "/events", "/gallery", "/news", "/contact", "/careers", "/faqs"]) {
    const res = await page.goto(`${SITE}${path}`);
    expect(res?.status(), path).toBe(200);
    await expect(page.locator("h1").first()).toBeVisible();
  }
  await page.goto(SITE);
  await expect(page.getByRole("heading", { level: 1 })).toContainText("best in every child");
  await expect(page.getByText("aurora-blossom.com")).toBeVisible();
  expect((await page.goto(`${SITE}/no-such-page`))?.status()).toBe(404);
});

test("an enquiry from the website reaches the Studio inbox", async ({ page }) => {
  const name = `E2E Parent ${Date.now()}`;
  await page.goto(`${SITE}/admissions`);
  await page.getByLabel("Parent / guardian name *").fill(name);
  await page.getByLabel("Phone *").fill("+91 90000 00000");
  await page.getByLabel("Admission sought for").selectOption("Class 2");
  await page.getByLabel("Your message *").fill("Automated test enquiry");
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Send enquiry" }).click();
  await expect(page.getByText("Thank you!")).toBeVisible();

  await signIn(page);
  await page.goto(`${urls.admin}/enquiries`);
  await page.getByText(name).click();
  await page.getByRole("button", { name: "Contacted", exact: true }).click();
  await expect(page.getByText("Saved")).toBeVisible();
});

test("editing and publishing a page updates the live site, and rollback restores it", async ({ page }) => {
  await signIn(page);
  await page.goto(`${urls.admin}/pages`);
  await page.getByRole("link", { name: "Principal's Message", exact: true }).click();
  await page.getByRole("button", { name: /Image \+ text/ }).click();
  const heading = page.getByLabel("Heading (optional)", { exact: true });
  const marker = `Welcome to Moriah ${Date.now()}`;
  await heading.fill(marker);
  await page.getByRole("button", { name: "Save draft" }).click();
  await expect(page.getByText("Draft saved")).toBeVisible();

  // Draft preview opens in a new tab with the unpublished change.
  const [preview] = await Promise.all([page.waitForEvent("popup"), page.getByRole("button", { name: "Preview" }).click()]);
  await expect(preview.getByText(marker)).toBeVisible();
  await expect(preview.getByText("Preview of unpublished changes")).toBeVisible();
  await preview.close();

  await page.getByRole("button", { name: "Publish" }).click();
  await expect(page.getByText("Published — your website is updated")).toBeVisible();
  await expect
    .poll(async () => siteHtml(page, "/principal-message"), { timeout: 15_000 })
    .toContain(marker);

  await page.getByRole("button", { name: "History" }).click();
  await page.getByRole("button", { name: "Restore" }).first().click();
  await expect(page.getByText("Restored this version")).toBeVisible();
  await expect
    .poll(async () => siteHtml(page, "/principal-message"), { timeout: 15_000 })
    .not.toContain(marker);
});

test("a published notice appears on the notice board", async ({ page }) => {
  const title = `E2E notice ${Date.now()}`;
  await signIn(page);
  await page.goto(`${urls.admin}/content/notices`);
  await page.getByRole("button", { name: "Add" }).click();
  await page.getByLabel("Title *", { exact: true }).fill(title);
  await page.getByLabel("Notice text").fill("Created by the automated test.");
  await page.getByRole("button", { name: "Publish" }).click();
  await expect(page.getByRole("listitem").filter({ hasText: title }).getByText("Published")).toBeVisible();
  await expect
    .poll(async () => siteHtml(page, "/news"), { timeout: 15_000 })
    .toContain(title);

  // Clean up.
  page.on("dialog", (d) => void d.accept());
  await page.getByRole("listitem").filter({ hasText: title }).getByRole("button", { name: "Delete" }).click();
  await expect(page.getByText("Deleted")).toBeVisible();
});
