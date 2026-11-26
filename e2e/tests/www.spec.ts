import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

/** Product website (shikshablox.com). Page list comes from the live sitemap. */

async function sitemapPaths(page: Page): Promise<string[]> {
  const res = await page.request.get("/sitemap.xml");
  expect(res.status()).toBe(200);
  const xml = await res.text();
  return [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => new URL(m[1]!).pathname);
}

async function scrollThrough(page: Page) {
  await page.evaluate(async () => {
    // Scroll at reading speed so lazy images are triggered as a person would trigger them.
    for (let y = 0; y < document.body.scrollHeight; y += 350) {
      window.scrollTo(0, y);
      await new Promise((r) => setTimeout(r, 150));
    }
    window.scrollTo(0, 0);
  });
}

/** Desktop navigation (hover menus) is used on wide screens that can hover; everything else gets the tap menu. */
const hasDesktopNav = (page: Page) =>
  page.evaluate(() => window.innerWidth >= 1024 && window.matchMedia("(hover: hover)").matches);

test.describe("every page", () => {
  test("loads with one H1, title, description, canonical, footer copyright and no overflow", async ({ page }) => {
    const paths = await sitemapPaths(page);
    expect(paths.length).toBeGreaterThanOrEqual(27);
    for (const p of paths) {
      const res = await page.goto(p);
      expect(res?.status(), p).toBe(200);
      await expect(page.locator("h1"), p).toHaveCount(1);
      expect((await page.title()).length, p).toBeGreaterThan(10);
      await expect(page.locator('meta[name="description"]'), p).toHaveAttribute("content", /.{50,}/);
      await expect(page.locator('link[rel="canonical"]'), p).toHaveAttribute("href", /^https?:\/\//);
      await expect(page.getByTestId("copyright"), p).toContainText("Aurora Blossom");
      await expect(page.getByTestId("copyright"), p).toContainText("School ERP Software");
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `${p} is wider than the viewport`).toBeLessThanOrEqual(0);
    }
  });

  test("every image loads", async ({ page }, info) => {
    test.setTimeout(180_000);
    test.skip(info.project.name !== "iphone-13" && info.project.name !== "laptop", "two profiles are enough");
    for (const p of await sitemapPaths(page)) {
      await page.goto(p);
      await scrollThrough(page);
      await page.waitForFunction(() => [...document.images].every((i) => i.complete), null, { timeout: 15_000 });
      const broken = await page.evaluate(() => [...document.images].filter((i) => i.naturalWidth === 0).map((i) => i.currentSrc || i.src));
      expect(broken, p).toEqual([]);
    }
  });

  test("no broken internal links", async ({ page }, info) => {
    test.skip(info.project.name !== "laptop", "runs once");
    const seen = new Set<string>();
    for (const p of await sitemapPaths(page)) {
      await page.goto(p);
      const hrefs = await page.$$eval("a[href^='/']", (as) => as.map((a) => a.getAttribute("href") ?? ""));
      hrefs.forEach((h) => seen.add(h.split("#")[0]!));
    }
    for (const h of seen) {
      const res = await page.request.get(h);
      expect(res.status(), h).toBe(200);
    }
  });

  test("no accessibility violations (WCAG 2.1 A/AA)", async ({ page }, info) => {
    test.skip(info.project.name !== "laptop" && info.project.name !== "iphone-13", "two profiles");
    for (const p of await sitemapPaths(page)) {
      await page.goto(p);
      const result = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
      const summary = result.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(" ")).slice(0, 3).join(", ")}`);
      expect(summary, p).toEqual([]);
    }
  });
});

test.describe("navigation", () => {
  test("desktop dropdown and links work", async ({ page }) => {
    await page.goto("/");
    test.skip(!(await hasDesktopNav(page)), "desktop only");
    await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "Product" }).hover();
    const roadmap = page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: /Roadmap/ });
    await expect(roadmap).toBeVisible();
    await roadmap.click();
    await expect(page).toHaveURL(/\/roadmap$/);
    await expect(page.locator("h1")).toContainText("What ships");
  });

  test("mobile menu shows every link, navigates, closes and never locks scrolling", async ({ page }) => {
    await page.goto("/");
    test.skip(await hasDesktopNav(page), "touch / small screens only");
    await page.getByRole("button", { name: "Open menu" }).click();
    const menu = page.getByRole("navigation", { name: "Mobile" });
    for (const name of ["All modules", "Small schools", "International schools", "Pricing", "Schools", "Why us", "Roadmap"]) {
      await expect(menu.getByRole("link", { name, exact: false }).first(), name).toBeVisible();
    }
    // The page behind stays scrollable while the menu is open.
    expect(await page.evaluate(() => getComputedStyle(document.body).overflowY)).not.toBe("hidden");
    await menu.getByRole("link", { name: "Pricing" }).click();
    await expect(page).toHaveURL(/\/pricing$/);
    await expect(menu).toBeHidden();
    await page.mouse.wheel(0, 1500);
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(300);
  });
});

test.describe("content", () => {
  test("Moriah School is showcased with logo and a working live-site link", async ({ page }) => {
    await page.goto("/schools");
    await expect(page.getByRole("img", { name: "Moriah School logo" })).toBeVisible();
    const link = page.getByRole("link", { name: /Visit Moriah School/ });
    const href = await link.getAttribute("href");
    expect(href).toMatch(/^https:\/\//);
    const res = await page.request.get(href!);
    expect(res.status()).toBe(200);
  });

  test("pricing shows live and pre-book plans with GST note", async ({ page }) => {
    await page.goto("/pricing");
    await expect(page.locator("#web-pro")).toContainText("₹7,999");
    await expect(page.locator("#lite")).toContainText("Pre-book");
    await expect(page.getByText(/excluding GST/).first()).toBeVisible();
  });

  test("robots and sitemap are consistent with the indexing mode", async ({ page }) => {
    const robots = await (await page.request.get("/robots.txt")).text();
    const res = await page.request.get("/");
    if (process.env.WWW_EXPECT_NOINDEX === "1") {
      expect(robots).toContain("Disallow: /");
      expect(res.headers()["x-robots-tag"]).toContain("noindex");
    } else {
      expect(robots).toContain("Sitemap:");
    }
  });
});

test.describe("demo request form", () => {
  test("shows inline errors and focuses the first invalid field", async ({ page }) => {
    await page.goto("/contact?plan=lite");
    await expect(page.getByLabel("I'm interested in")).toHaveValue("lite");
    await page.getByRole("button", { name: "Request my free demo" }).click();
    await expect(page.getByText("Please enter your name.")).toBeVisible();
    await expect(page.getByText("Please enter your school's name.")).toBeVisible();
    await expect(page.getByText("Enter a valid phone number.")).toBeVisible();
    await expect(page.getByLabel(/Your name/)).toBeFocused();
  });

  test("a real request reaches the ShikshaBlox sales inbox", async ({ page }, info) => {
    test.skip(process.env.WWW_E2E_SUBMIT !== "1" || info.project.name !== "laptop", "staging only, once");
    await page.goto("/contact");
    await page.getByLabel(/Your name/).fill("E2E Test (ignore)");
    await page.getByLabel(/School name/).fill("Playwright Test School");
    await page.getByRole("textbox", { name: /Mobile \/ WhatsApp/ }).fill("+91 90000 00000");
    await page.getByRole("textbox", { name: "Email" }).fill("e2e@example.test");
    await page.getByRole("checkbox", { name: /I agree/ }).check();
    const [res] = await Promise.all([
      page.waitForResponse((r) => r.url().endsWith("/api/demo-request")),
      page.getByRole("button", { name: "Request my free demo" }).click(),
    ]);
    expect(res.status()).toBe(202);
    await expect(page.getByText("Thank you! We'll be in touch.")).toBeVisible();
  });
});
