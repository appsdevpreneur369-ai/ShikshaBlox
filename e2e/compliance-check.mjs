// End-to-end check of the compliance calendar (M02) in a deployed Studio.
// Usage: SEED_PASSWORD=... node compliance-check.mjs <studio url> <outDir>
// Creates a throwaway overdue item, marks it done with evidence, checks the vault, the export
// pack and the phone layout, then removes what it created.
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [studio, out = "compliance-shots"] = process.argv.slice(2).map((s) => s?.replace(/\/$/, ""));
if (!studio) throw new Error("usage: node compliance-check.mjs <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const pdf = Buffer.from(
  "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n",
);
const TITLE = `QA compliance check ${Date.now()}`;
const problems = [];
const browser = await chromium.launch();

async function signIn(ctx) {
  const page = await ctx.newPage();
  await page.goto(`${studio}/login`);
  await page.getByLabel("Email").fill("admin@greenfield.test");
  await page.getByLabel("Password").fill(process.env.SEED_PASSWORD ?? "");
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL(/dashboard/, { timeout: 30000 });
  return page;
}

const ctx = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  reducedMotion: "reduce",
  acceptDownloads: true,
});
const page = await signIn(ctx);
const api = (p, o = {}) =>
  ctx.request.fetch(`${studio}/api/backend/${p}`, { headers: { "X-Studio": "1" }, ...o });

await page.goto(`${studio}/compliance`, { waitUntil: "networkidle" });
if (!(await page.getByRole("link", { name: "Compliance" }).count()))
  problems.push("No Compliance item in the Studio nav");
await page
  .getByText("Overdue", { exact: true })
  .first()
  .waitFor({ timeout: 15000 })
  .catch(() => problems.push("Summary tiles missing"));
await page.screenshot({ path: `${out}/calendar.png`, fullPage: true });

// Add an overdue monthly item through the form.
await page.getByRole("button", { name: "Add item" }).click();
await page.getByLabel("What needs to be done *").fill(TITLE);
const yesterday = new Date(Date.now() - 86400000 + 5.5 * 3600000).toISOString().slice(0, 10);
await page.getByLabel("Next due date").fill(yesterday);
await page.getByLabel("Repeats").selectOption("MONTHLY");
await page.locator("form").getByRole("button", { name: "Add item" }).click();
await page
  .getByText("Item added")
  .waitFor({ timeout: 10000 })
  .catch(() => problems.push("Adding an item did not confirm"));
await page.waitForTimeout(1000);
const items = await (await api("compliance/items")).json();
const item = items.find((i) => i.title === TITLE);
if (!item) problems.push("New item not returned by the API");
else if (item.state !== "OVERDUE") problems.push(`New item should be OVERDUE, is ${item.state}`);

// Mark it done with evidence (panel opened automatically after adding).
await page
  .getByRole("button", { name: /Attach evidence/ })
  .waitFor({ timeout: 10000 })
  .catch(() => {});
await page
  .locator('input[type="file"]')
  .first()
  .setInputFiles({ name: "qa-evidence.pdf", mimeType: "application/pdf", buffer: pdf });
await page.getByRole("button", { name: "Mark done", exact: true }).click();
await page
  .getByText("Marked done")
  .waitFor({ timeout: 15000 })
  .catch(() => problems.push("Mark done did not confirm"));
await page.waitForTimeout(1000);
await page.screenshot({ path: `${out}/item-done.png`, fullPage: true });
const after = item ? await (await api(`compliance/items/${item.id}`)).json() : null;
if (after) {
  if (after.item.state === "OVERDUE") problems.push("Item still overdue after marking done");
  if (after.completions.length !== 1)
    problems.push(`Expected 1 completion, got ${after.completions.length}`);
  if (after.documents.length !== 1)
    problems.push(`Expected 1 evidence document, got ${after.documents.length}`);
  if (!(after.item.dueDate > yesterday))
    problems.push(`Next due date did not move forward: ${after.item.dueDate}`);
}

// Evidence vault + download.
await page.getByRole("button", { name: "Evidence vault" }).click();
await page.waitForTimeout(1500);
await page.screenshot({ path: `${out}/vault.png`, fullPage: true });
const docName = `${TITLE} – `;
const dlBtn = page.getByRole("button", { name: new RegExp(`^Download ${TITLE}`) }).first();
if (await dlBtn.count()) {
  const [dl] = await Promise.all([
    page.waitForEvent("download", { timeout: 20000 }),
    dlBtn.click(),
  ]);
  if (!(await dl.path().catch(() => null))) problems.push("Evidence download failed");
} else problems.push(`Evidence "${docName}…" not in the vault`);

// Inspection pack (Standard plan).
const exp = await api("compliance/export.zip");
const zip = await exp.body();
if (exp.status() !== 200) problems.push(`Export returned ${exp.status()}`);
else {
  const names = zip.toString("latin1");
  for (const f of ["register.csv", "completions.csv", "documents.csv"])
    if (!names.includes(f)) problems.push(`Export is missing ${f}`);
  if (!names.includes("QA-compliance-check"))
    problems.push("Export does not contain the evidence file");
  fs.writeFileSync(`${out}/pack.zip`, zip);
}

// Phone layout.
const phone = await browser.newContext({
  ...devices["iPhone 13"],
  defaultBrowserType: undefined,
  reducedMotion: "reduce",
});
const ppage = await signIn(phone);
await ppage.goto(`${studio}/compliance`, { waitUntil: "networkidle" });
await ppage.waitForTimeout(1200);
const overflow = await ppage.evaluate(
  () => document.documentElement.scrollWidth - window.innerWidth,
);
if (overflow > 1) problems.push(`Phone: /compliance scrolls sideways by ${overflow}px`);
await ppage.screenshot({ path: `${out}/phone.png`, fullPage: true });
await phone.close();

// Clean up: delete the evidence and the item.
if (after) {
  for (const d of after.documents) await api(`compliance/documents/${d.id}`, { method: "DELETE" });
  const del = await api(`compliance/items/${item.id}`, { method: "DELETE" });
  if (del.status() !== 204) problems.push(`Cleanup: deleting the item returned ${del.status()}`);
}
await browser.close();
console.log(
  problems.length
    ? `PROBLEMS (${problems.length}):\n- ${problems.join("\n- ")}`
    : "All checks passed.",
);
process.exit(problems.length ? 1 : 0);
