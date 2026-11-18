// QA audit of a deployed school site: responsive layout, mobile menu, scrolling, hero image,
// broken images/links, console errors, tap targets, SEO basics.
// Usage: node site-audit.mjs https://moriah-....run.app [outDir]
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const base = (process.argv[2] ?? "").replace(/\/$/, "");
const out = process.argv[3] ?? "audit-shots";
if (!base) throw new Error("usage: node site-audit.mjs <site url> [outDir]");
fs.mkdirSync(out, { recursive: true });

const sizes = [
  { name: "galaxy-s8", ...devices["Galaxy S8"] }, // 360x740
  { name: "iphone-13", ...devices["iPhone 13"], defaultBrowserType: undefined }, // 390x844
  { name: "pixel-7-landscape", ...devices["Pixel 7 landscape"] },
  { name: "ipad-mini", ...devices["iPad Mini"], defaultBrowserType: undefined }, // 768x1024
  { name: "ipad-landscape", ...devices["iPad Pro 11 landscape"], defaultBrowserType: undefined },
  { name: "laptop", viewport: { width: 1280, height: 800 } },
  { name: "desktop", viewport: { width: 1920, height: 1080 } },
];

const problems = [];
const bad = (m) => problems.push(m);

const sitemap = await (await fetch(base + "/sitemap.xml")).text();
const urls = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]);
console.log(`sitemap: ${urls.length} urls`);
const robots = await (await fetch(base + "/robots.txt")).text();
if (!/Sitemap:/.test(robots)) bad("robots.txt has no Sitemap line");

// Every internal link on every page resolves.
const browser = await chromium.launch();
{
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const links = new Set();
  for (const u of urls) {
    await page.goto(u, { waitUntil: "domcontentloaded" });
    for (const h of await page.$$eval("a[href]", (as) => as.map((a) => a.href))) {
      const url = new URL(h);
      if (url.origin === new URL(base).origin) links.add(url.origin + url.pathname);
    }
  }
  for (const l of links) {
    const r = await fetch(l, { redirect: "manual" });
    if (r.status >= 400) bad(`broken link ${new URL(l).pathname}: HTTP ${r.status}`);
  }
  console.log(`checked ${links.size} internal links`);
  await ctx.close();
}

for (const s of sizes) {
  const { name, defaultBrowserType, ...opts } = s;
  const ctx = await browser.newContext({ ...opts, reducedMotion: "reduce" });
  const page = await ctx.newPage();
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("console", (m) => m.type() === "error" && errors.push(m.text()));
  const vw = page.viewportSize().width;
  const mobile = vw < 1024;

  for (const u of urls) {
    const res = await page.goto(u, { waitUntil: "networkidle" });
    const path = new URL(u).pathname;
    if (!res || res.status() !== 200) bad(`${name} ${path}: HTTP ${res?.status()}`);
    const r = await page.evaluate(() => {
      const doc = document.documentElement;
      const wide = [...document.querySelectorAll("body *")]
        .filter((el) => {
          const b = el.getBoundingClientRect();
          return b.width > 0 && b.right > window.innerWidth + 1 && getComputedStyle(el).position !== "fixed";
        })
        .slice(0, 3)
        .map((el) => el.tagName.toLowerCase() + "." + [...el.classList].slice(0, 2).join("."));
      const meta = (n) => document.querySelector(`meta[name="${n}"]`)?.getAttribute("content");
      const tiny = [...document.querySelectorAll("p, li, a, span, label")].filter(
        (el) => el.offsetParent && el.textContent.trim() && parseFloat(getComputedStyle(el).fontSize) < 12,
      ).length;
      const brokenImgs = [...document.images].filter((i) => i.complete && i.naturalWidth === 0).map((i) => i.src).slice(0, 2);
      // Tap targets: visible links/buttons smaller than 24x24 (WCAG 2.2 minimum), ignoring inline text links.
      const smallTargets = [...document.querySelectorAll("a, button, input, select, textarea")]
        .filter((el) => {
          const b = el.getBoundingClientRect();
          if (!b.width || !el.offsetParent || el.classList.contains("sr-only")) return false;
          const inline = getComputedStyle(el).display === "inline" && el.closest("p, li");
          return !inline && (b.width < 24 || b.height < 24);
        })
        .slice(0, 3)
        .map((el) => (el.getAttribute("aria-label") || el.textContent || el.tagName).trim().slice(0, 30));
      // Hero image must not be cropped on small screens.
      const hero = document.querySelector(".sb-hero-full > img");
      let heroCrop = null;
      if (hero && hero.naturalWidth) {
        const b = hero.getBoundingClientRect();
        const fit = getComputedStyle(hero).objectFit;
        const expected = (b.width * hero.naturalHeight) / hero.naturalWidth;
        heroCrop = { fit, ratio: b.height / expected };
      }
      return {
        overflow: doc.scrollWidth - window.innerWidth,
        wide,
        tall: doc.scrollHeight > window.innerHeight + 50,
        title: document.title,
        desc: meta("description"),
        viewport: meta("viewport"),
        canonical: document.querySelector('link[rel="canonical"]')?.href,
        h1: document.querySelectorAll("h1").length,
        noAlt: [...document.images].filter((i) => !i.hasAttribute("alt")).length,
        lang: doc.lang,
        tiny,
        brokenImgs,
        smallTargets,
        heroCrop,
        bodyOverflow: getComputedStyle(document.body).overflow,
      };
    });
    if (r.overflow > 1) bad(`${name} ${path}: horizontal overflow ${r.overflow}px ${r.wide.join(", ")}`);
    if (r.tiny) bad(`${name} ${path}: ${r.tiny} text elements under 12px`);
    if (r.brokenImgs.length) bad(`${name} ${path}: broken images ${r.brokenImgs.join(" ")}`);
    if (r.smallTargets.length) bad(`${name} ${path}: small tap targets: ${r.smallTargets.join(" | ")}`);
    if (r.bodyOverflow === "hidden") bad(`${name} ${path}: page scroll locked`);
    if (mobile && r.heroCrop && Math.abs(r.heroCrop.ratio - 1) > 0.03)
      bad(`${name} ${path}: hero image cropped (${r.heroCrop.fit}, ratio ${r.heroCrop.ratio.toFixed(2)})`);
    // Scrolling actually moves the page.
    if (r.tall) {
      if (opts.hasTouch) await page.mouse.wheel(0, 600);
      else await page.mouse.wheel(0, 600);
      await page.waitForTimeout(250);
      if ((await page.evaluate(() => window.scrollY)) < 100) bad(`${name} ${path}: page does not scroll`);
    }
    if (name === "laptop") {
      if (!r.title) bad(`${path}: no title`);
      if (!r.desc || r.desc.length < 70) bad(`${path}: weak description "${r.desc ?? ""}"`);
      if (!r.viewport) bad(`${path}: no viewport meta`);
      if (!r.canonical) bad(`${path}: no canonical`);
      if (r.h1 !== 1) bad(`${path}: ${r.h1} h1 elements`);
      if (r.noAlt) bad(`${path}: ${r.noAlt} images without alt`);
      if (!r.lang) bad(`${path}: no lang`);
    }
    if (["/", "/admissions", "/contact"].includes(path)) {
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.screenshot({ path: `${out}/${name}${path === "/" ? "-home" : path.replace(/\//g, "-")}.png`, fullPage: path === "/" });
    }
  }

  // Navigation by tap/click — every menu entry, verifying the panel is really visible.
  try {
    await page.goto(base + "/", { waitUntil: "networkidle" });
    if (mobile) {
      const toggle = page.getByRole("button", { name: "Open menu" });
      await toggle.click();
      const panel = page.locator("#sb-mobile-nav");
      const box = await panel.boundingBox();
      const vh = page.viewportSize().height;
      if (!box || box.height < Math.min(300, vh - 90)) throw new Error(`menu panel too small (${box?.height}px)`);
      await page.screenshot({ path: `${out}/${name}-menu.png` });
      const hrefs = await panel.locator("a").evaluateAll((as) => as.map((a) => a.getAttribute("href")));
      if (hrefs.length < 8) throw new Error(`menu shows only ${hrefs.length} links`);
      for (const href of hrefs) {
        await page.goto(base + "/", { waitUntil: "domcontentloaded" });
        await page.getByRole("button", { name: "Open menu" }).click();
        const link = page.locator(`#sb-mobile-nav a[href="${href}"]`).first();
        await link.scrollIntoViewIfNeeded();
        await page.waitForTimeout(500);
        const lb = await link.boundingBox();
        const hit = await page.evaluate(
          ([x, y]) => document.elementFromPoint(x, y)?.closest("a")?.getAttribute("href"),
          [lb.x + lb.width / 2, lb.y + lb.height / 2],
        );
        if (hit !== href) throw new Error(`menu link ${href} is covered (tap hits ${hit})`);
        await link.click();
        await page.waitForURL((u) => u.pathname + u.hash === href || u.pathname === href.split("#")[0], { timeout: 10000 });
        await page.waitForTimeout(200);
        if (await panel.isVisible()) throw new Error(`menu stays open after tapping ${href}`);
        if ((await page.evaluate(() => getComputedStyle(document.body).overflow)) === "hidden")
          throw new Error(`scroll stays locked after tapping ${href}`);
      }
      // Escape / toggle closes it and scroll works afterwards.
      await page.goto(base + "/", { waitUntil: "networkidle" });
      await toggle.click();
      await page.getByRole("button", { name: "Close menu" }).click();
      await page.mouse.wheel(0, 800);
      await page.waitForTimeout(250);
      if ((await page.evaluate(() => window.scrollY)) < 100) throw new Error("page does not scroll after closing menu");
      console.log(`${name}: mobile menu OK (${hrefs.length} links)`);
    } else {
      const link = page.locator('nav[aria-label="Main"] > ul > li > a, nav[aria-label="Main"] > ul > li > span > a');
      const hrefs = await link.evaluateAll((as) => as.map((a) => a.getAttribute("href")));
      for (const href of hrefs) {
        await page.goto(base + "/", { waitUntil: "domcontentloaded" });
        await page.locator(`nav[aria-label="Main"] a[href="${href}"]`).first().click();
        await page.waitForURL((u) => u.pathname === href, { timeout: 10000 });
      }
      await page.goto(base + "/", { waitUntil: "networkidle" });
      const chevs = page.locator('nav[aria-label="Main"] button[aria-haspopup]');
      const n = await chevs.count();
      for (let i = 0; i < n; i++) {
        await page.goto(base + "/", { waitUntil: "domcontentloaded" });
        await page.mouse.move(5, 400);
        await chevs.nth(i).click();
        const sub = page.locator(".sb-has-dropdown[data-open] .sb-dropdown a").first();
        const href = await sub.getAttribute("href");
        await sub.click();
        await page.waitForURL((u) => u.pathname === href, { timeout: 10000 });
      }
      console.log(`${name}: desktop nav OK (${hrefs.length} top links, ${n} dropdowns)`);
    }
  } catch (e) {
    bad(`${name}: navigation — ${e.message.split("\n")[0]}`);
  }
  const uniq = [...new Set(errors)].filter((e) => !/favicon/.test(e));
  if (uniq.length) bad(`${name}: console errors: ${uniq.slice(0, 3).join(" || ")}`);
  await ctx.close();
}
await browser.close();
console.log(problems.length ? `PROBLEMS (${problems.length}):\n` + problems.join("\n") : "No problems found");
