// End-to-end check of online applications (M03b) on a deployed school site + Studio.
// Usage: SEED_PASSWORD=... node applications-check.mjs <site url> <studio url> <outDir>
// Submits one application per device profile with sample documents, then reviews the newest
// application in Studio (verify a document, open its signed link, mark the fee paid).
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [site, studio, out = "applications-shots"] = process.argv
  .slice(2)
  .map((s) => s?.replace(/\/$/, ""));
if (!site || !studio)
  throw new Error("usage: node applications-check.mjs <site url> <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const pdf = Buffer.from(
  "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n",
);
const problems = [];
const browser = await chromium.launch();

const profiles = [
  { name: "galaxy-s8", ...devices["Galaxy S8"] },
  { name: "iphone-13", ...devices["iPhone 13"], defaultBrowserType: undefined },
  { name: "desktop", viewport: { width: 1440, height: 900 } },
];
const numbers = [];
// STUDIO_ONLY=APP-2027-0005 skips the website part (the form is rate limited to 5 per 30 min).
if (process.env.STUDIO_ONLY) numbers.push(process.env.STUDIO_ONLY);
for (const [i, p] of (process.env.STUDIO_ONLY ? [] : profiles).entries()) {
  const { name, ...ctxOpts } = p;
  const ctx = await browser.newContext({ ...ctxOpts, reducedMotion: "reduce" });
  const page = await ctx.newPage();
  const errors = [];
  page.on("console", (m) => m.type() === "error" && errors.push(m.text()));
  await page.goto(`${site}/`, { waitUntil: "networkidle" });
  // On phones the header CTA lives in the menu, so check the link target rather than visibility.
  if (!(await page.locator('header a[href="/apply"]').count())) problems.push(`${name}: header "Apply now" does not link to /apply`);
  await page.goto(`${site}/apply`, { waitUntil: "networkidle" });
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  );
  if (overflow > 1) problems.push(`${name}: /apply scrolls sideways by ${overflow}px`);
  await page.screenshot({ path: `${out}/${name}-form.png`, fullPage: true });

  await page.getByLabel("Student's full name *").fill(`QA Test Child ${i + 1}`);
  await page.getByLabel("Date of birth *").fill("2020-06-15");
  await page.getByLabel("Admission sought for *").selectOption({ index: 1 });
  await page.getByLabel("Name *", { exact: true }).fill("QA Tester");
  await page.getByLabel("Mobile number *").fill(`+91 90000 2000${i + 1}`);
  await page.getByLabel("Email", { exact: true }).fill("qa@example.test");
  await page.getByLabel("Home address *").fill("1 QA Lane, Test City");
  // Try submitting without the required documents first: the form must say what is missing.
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Submit application" }).click();
  const alert = page.locator("form [role=alert]").first(); // Next adds its own role=alert announcer
  if (!(await alert.textContent({ timeout: 5000 }).catch(() => ""))?.includes("Please upload")) {
    problems.push(`${name}: no "Please upload" message when required documents are missing`);
  }
  for (const label of ["Birth certificate", "Student photograph"]) {
    await page
      .getByLabel(new RegExp(`^${label}`))
      .setInputFiles({ name: `${label}.pdf`, mimeType: "application/pdf", buffer: pdf });
  }
  await page.getByRole("button", { name: "Submit application" }).click();
  const status = page.getByRole("status");
  try {
    await status.waitFor({ timeout: 30000 });
    const no = (await status.textContent())?.match(/APP-\d{4}-\d{4}/)?.[0];
    if (!no) problems.push(`${name}: success screen without an application number`);
    else numbers.push(no);
  } catch {
    problems.push(`${name}: submission failed: ${await alert.textContent().catch(() => "?")}`);
  }
  await page.screenshot({ path: `${out}/${name}-done.png`, fullPage: true });
  const real = errors.filter((e) => !/favicon|ERR_BLOCKED/.test(e));
  if (real.length) problems.push(`${name}: console errors: ${real.slice(0, 3).join(" | ")}`);
  await ctx.close();
}

// Studio review of the newest application.
const ctx = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  reducedMotion: "reduce",
});
const page = await ctx.newPage();
await page.goto(`${studio}/login`);
await page.getByLabel("Email").fill("admin@greenfield.test");
await page.getByLabel("Password").fill(process.env.SEED_PASSWORD ?? "");
await page.getByRole("button", { name: "Sign in" }).click();
await page.waitForURL(/dashboard/, { timeout: 30000 });
await page.goto(`${studio}/admissions?view=applications`, { waitUntil: "networkidle" });
await page.waitForTimeout(1500);
await page.screenshot({ path: `${out}/studio-applications.png`, fullPage: true });
const newest = numbers.at(-1);
if (newest) {
  const row = page.getByRole("button", { name: newest });
  if (!(await row.count())) problems.push(`Studio: ${newest} not in the Applications list`);
  else {
    await row.click();
    await page.getByText("Documents", { exact: true }).waitFor();
    const [download] = await Promise.all([
      page.waitForEvent("download", { timeout: 20000 }),
      page
        .getByRole("button", { name: /^Download / })
        .first()
        .click(),
    ]);
    if (!/storage\.googleapis\.com|X-Goog-Signature|X-Amz-Signature/i.test(download.url()))
      problems.push(`Studio: document link is not a signed URL: ${download.url()}`);
    if (!(await download.path().catch(() => null)))
      problems.push(`Studio: document download failed: ${await download.failure()}`);
    if (page.context().pages().length > 1) problems.push("Studio: downloading opened an extra tab");
    await page.getByRole("button", { name: "Verify", exact: true }).first().click();
    await page
      .getByText("Document verified")
      .waitFor({ timeout: 10000 })
      .catch(() => problems.push("Studio: verify did not confirm"));
    await page.getByLabel("Receipt or reference").fill("QA-RCPT-1");
    await page.getByRole("button", { name: "Mark paid" }).click();
    await page
      .getByText("Fee updated")
      .waitFor({ timeout: 10000 })
      .catch(() => problems.push("Studio: mark paid did not confirm"));
    await page.waitForTimeout(800);
    await page.screenshot({ path: `${out}/studio-application-panel.png`, fullPage: true });
    await page.getByRole("button", { name: "Open lead" }).click();
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${out}/studio-lead.png`, fullPage: true });
    if (!(await page.getByText("Online applications").count()))
      problems.push("Studio: lead panel does not list the application");
  }
}
await page.goto(`${studio}/admissions?view=form`, { waitUntil: "networkidle" });
await page.waitForTimeout(1200);
await page.screenshot({ path: `${out}/studio-form-settings.png`, fullPage: true });
await browser.close();

console.log("Submitted:", numbers.join(", ") || "none");
console.log(
  problems.length
    ? `PROBLEMS (${problems.length}):\n- ${problems.join("\n- ")}`
    : "All checks passed.",
);
process.exit(problems.length ? 1 : 0);
