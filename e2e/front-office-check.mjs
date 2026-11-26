// End-to-end check of the front office (M43) in a deployed Studio, signed in as the receptionist.
// Usage: SEED_PASSWORD=... node front-office-check.mjs <studio url> <outDir>
// Logs an admission walk-in and hands it to admissions, books an appointment, logs
// and hands over post, logs a found item with a photo, checks the phone layout, then deletes
// everything it created (the lead as the school admin).
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [studio, out = "front-office-shots"] = process.argv
  .slice(2)
  .map((s) => s?.replace(/\/$/, ""));
if (!studio) throw new Error("usage: node front-office-check.mjs <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const RUN = `QA${Date.now().toString().slice(-6)}`;
// 1x1 PNG
const png = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  "base64",
);
const problems = [];
const browser = await chromium.launch();

async function signIn(ctx, who) {
  const page = await ctx.newPage();
  await page.goto(`${studio}/login`);
  await page.getByLabel("Email").fill(`${who}@greenfield.test`);
  await page.getByLabel("Password").fill(process.env.SEED_PASSWORD ?? "");
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL(/dashboard/, { timeout: 30000 });
  return page;
}

const ctx = await browser.newContext({
  viewport: { width: 1280, height: 900 },
  reducedMotion: "reduce",
});
const page = await signIn(ctx, "reception");
const api = (p, o = {}) =>
  ctx.request.fetch(`${studio}/api/backend/${p}`, { headers: { "X-Studio": "1" }, ...o });

// The receptionist sees Front office but not Admissions, Compliance or Pages.
const nav = page.getByRole("navigation").first();
for (const [label, want] of [
  ["Front office", true],
  ["Admissions", false],
  ["Compliance", false],
  ["Pages", false],
]) {
  const has = (await nav.getByRole("link", { name: label, exact: true }).count()) > 0;
  if (has !== want)
    problems.push(`Receptionist nav: "${label}" ${want ? "missing" : "should be hidden"}`);
}

await page.goto(`${studio}/front-office`, { waitUntil: "networkidle" });
await page
  .getByText("Walk-ins today")
  .waitFor({ timeout: 15000 })
  .catch(() => problems.push("Today tiles missing"));

// 1. Admission walk-in -> admissions lead.
await page.getByLabel("Name *").fill(`${RUN} Parent`);
await page.getByLabel("Phone").first().fill("+91 90000 39999");
await page.getByLabel("Purpose").selectOption("ADMISSION");
await page.getByRole("button", { name: /^Log walk-in/ }).click();
await page
  .getByText("Logged")
  .first()
  .waitFor({ timeout: 10000 })
  .catch(() => problems.push("Logging a walk-in did not confirm"));
await page.getByRole("button", { name: "To admissions" }).first().click();
await page.getByLabel("Student name").fill(`${RUN} Child`);
await page.getByLabel("Class sought").fill("Class 4");
await page.getByRole("button", { name: "Create lead" }).click();
await page
  .getByText("Added to admissions")
  .waitFor({ timeout: 10000 })
  .catch(() => problems.push("Hand-off to admissions did not confirm"));
await page.screenshot({ path: `${out}/register.png`, fullPage: true });
const entries = await (await api("front-office/entries")).json();
const walkIn = entries.find((e) => e.name === `${RUN} Parent`);
if (!walkIn?.leadId) problems.push("Walk-in has no admissions lead after hand-off");

// 2. Appointment tomorrow 11:30 IST with "Principal".
const tomorrow = new Date(Date.now() + 86400000 + 5.5 * 3600000).toISOString().slice(0, 10);
await page.getByRole("button", { name: "Appointments" }).click();
await page.getByRole("button", { name: "Book appointment" }).click();
await page.getByLabel("Visitor *").fill(`${RUN} Visitor`);
await page.getByLabel("Meeting with *").fill("Principal");
await page.getByLabel("Date").fill(tomorrow);
await page.getByLabel("Time").fill("11:30");
await page.locator("form").getByRole("button", { name: "Book", exact: true }).click();
await page
  .getByText("Appointment booked")
  .waitFor({ timeout: 10000 })
  .catch(() => problems.push("Booking did not confirm"));
await page.waitForTimeout(800);
await page.screenshot({ path: `${out}/appointments.png`, fullPage: true });
const appts = await (await api(`front-office/appointments?from=${tomorrow}&to=${tomorrow}`)).json();
const appt = appts.find((a) => a.visitorName === `${RUN} Visitor`);
if (!appt) problems.push("Appointment not returned by the API");
else if (!appt.startsAt.startsWith(`${tomorrow}T06:00`))
  problems.push(`Appointment time is ${appt.startsAt}, expected 11:30 IST`);

// 3. Inward post, then hand over.
await page.getByRole("button", { name: /^Post & couriers/ }).click();
await page.getByRole("button", { name: "Received" }).click();
await page.getByLabel("From (sender) *").fill(`${RUN} Sender`);
await page.getByLabel("For (staff / office)").fill("Principal");
await page.locator("form").getByRole("button", { name: "Log", exact: true }).click();
await page
  .getByText("Logged")
  .first()
  .waitFor({ timeout: 10000 })
  .catch(() => problems.push("Logging post did not confirm"));
await page.waitForTimeout(800);
await page.getByRole("button", { name: "Hand over…" }).first().click();
await page.getByLabel("Handed to *").fill("Principal's office");
await page.getByRole("button", { name: "Save", exact: true }).click();
await page
  .getByText("Handed over")
  .waitFor({ timeout: 10000 })
  .catch(() => problems.push("Hand over did not confirm"));
const post = (await (await api("front-office/post")).json()).find(
  (p) => p.counterparty === `${RUN} Sender`,
);
if (post?.status !== "HANDED_OVER") problems.push(`Post status is ${post?.status}`);

// 4. Lost & found with a photo.
await page.getByRole("button", { name: /^Lost & found/ }).click();
await page.getByRole("button", { name: "Found item" }).click();
await page.getByLabel("What is it? *").fill(`${RUN} green umbrella`);
await page
  .locator('input[type="file"]')
  .setInputFiles({ name: "umbrella.png", mimeType: "image/png", buffer: png });
await page.getByRole("button", { name: "Log item" }).click();
await page
  .getByText("Logged")
  .first()
  .waitFor({ timeout: 10000 })
  .catch(() => problems.push("Logging a found item did not confirm"));
await page.waitForTimeout(1500);
await page.screenshot({ path: `${out}/lost-found.png`, fullPage: true });
const lost = (await (await api("front-office/lost-found")).json()).find(
  (l) => l.description === `${RUN} green umbrella`,
);
if (!lost?.hasPhoto) problems.push("Found item has no photo");
else if (!(await page.locator(`img[alt="${RUN} green umbrella"]`).count()))
  problems.push("Found item photo not shown");

// Phone layout.
const phone = await browser.newContext({
  ...devices["iPhone 13"],
  defaultBrowserType: undefined,
  reducedMotion: "reduce",
});
const ppage = await signIn(phone, "reception");
await ppage.goto(`${studio}/front-office`, { waitUntil: "networkidle" });
await ppage.waitForTimeout(1200);
const overflow = await ppage.evaluate(
  () => document.documentElement.scrollWidth - window.innerWidth,
);
if (overflow > 1) problems.push(`Phone: /front-office scrolls sideways by ${overflow}px`);
await ppage.screenshot({ path: `${out}/phone.png`, fullPage: true });
await phone.close();

// Clean up everything this run created.
const del = async (p) => {
  const r = await api(p, { method: "DELETE" });
  if (r.status() !== 204) problems.push(`Cleanup ${p}: ${r.status()}`);
};
const after = await (await api("front-office/entries")).json();
for (const e of after.filter((x) => x.name.startsWith(RUN)))
  await del(`front-office/entries/${e.id}`);
if (appt) await del(`front-office/appointments/${appt.id}`);
if (post) await del(`front-office/post/${post.id}`);
if (lost) await del(`front-office/lost-found/${lost.id}`);
if (walkIn?.leadId) {
  const admin = await browser.newContext();
  await signIn(admin, "admin");
  const r = await admin.request.fetch(`${studio}/api/backend/admissions/leads/${walkIn.leadId}`, {
    method: "DELETE",
    headers: { "X-Studio": "1" },
  });
  if (r.status() !== 204) problems.push(`Cleanup lead: ${r.status()}`);
  await admin.close();
}
await browser.close();
console.log(
  problems.length
    ? `PROBLEMS (${problems.length}):\n- ${problems.join("\n- ")}`
    : "All checks passed.",
);
process.exit(problems.length ? 1 : 0);
