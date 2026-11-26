// End-to-end check of M26 (PTM, helpdesk, surveys): the parent side on the school website and
// the staff side in Studio. Creates its own PTM and survey, removes everything afterwards.
// Usage: SEED_PASSWORD=... node engagement-check.mjs <site url> <studio url> <outDir>
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [site, studio, out = "engagement-shots"] = process.argv
  .slice(2)
  .map((s) => s?.replace(/\/$/, ""));
if (!site || !studio)
  throw new Error("usage: node engagement-check.mjs <site url> <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const RUN = `qa${Date.now().toString().slice(-6)}`;
const PHONE = "+91 90000 49999";
const problems = [];
const browser = await chromium.launch();

// ---- Staff session (API + Studio screens)
const staff = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  reducedMotion: "reduce",
});
const sp = await staff.newPage();
await sp.goto(`${studio}/login`);
await sp.getByLabel("Email").fill("admin@greenfield.test");
await sp.getByLabel("Password").fill(process.env.SEED_PASSWORD ?? "");
await sp.getByRole("button", { name: "Sign in" }).click();
await sp.waitForURL(/dashboard/, { timeout: 30000 });
const api = async (p, o = {}) => {
  const r = await staff.request.fetch(`${studio}/api/backend/${p}`, {
    headers: { "X-Studio": "1" },
    ...o,
  });
  return { status: r.status(), body: r.status() === 204 ? null : await r.json().catch(() => null) };
};

// Remove leftovers of earlier runs that stopped before their cleanup.
for (const e of (await api("engagement/ptm")).body ?? []) {
  if (/^qa\d{6} PTM$/.test(e.title)) await api(`engagement/ptm/${e.id}`, { method: "DELETE" });
}
for (const x of (await api("engagement/surveys")).body ?? []) {
  if (/^qa\d{6}-feedback$/.test(x.slug))
    await api(`engagement/surveys/${x.id}`, { method: "DELETE" });
}
for (const t of (await api("engagement/helpdesk?view=all&q=qa")).body ?? []) {
  if (/^qa\d{6} bus timing$/.test(t.subject))
    await api(`engagement/helpdesk/${t.id}`, { method: "DELETE" });
}

const tomorrow = new Date(Date.now() + 86400000 + 5.5 * 3600000).toISOString().slice(0, 10);
const ev = await api("engagement/ptm", {
  method: "POST",
  data: {
    title: `${RUN} PTM`,
    eventDate: tomorrow,
    startTime: "15:00",
    endTime: "15:30",
    slotMinutes: 10,
    bookingOpen: true,
    hosts: [{ name: `${RUN} Teacher`, label: "Class 9A", room: "Room 1" }],
  },
});
if (ev.status !== 201) problems.push(`Create PTM: ${ev.status} ${JSON.stringify(ev.body)}`);
const sv = await api("engagement/surveys", {
  method: "POST",
  data: {
    title: `${RUN} feedback`,
    slug: `${RUN}-feedback`,
    questions: [
      { type: "RATING", label: "How was it?", options: [], required: true },
      { type: "CHOICE", label: "Again next year?", options: ["Yes", "No"], required: false },
      { type: "TEXT", label: "Suggestions", options: [], required: false },
    ],
    open: true,
    anonymous: true,
  },
});
if (sv.status !== 201) problems.push(`Create survey: ${sv.status} ${JSON.stringify(sv.body)}`);

// ---- Parent on a phone
const parent = await browser.newContext({ ...devices["Galaxy S8"], reducedMotion: "reduce" });
const pp = await parent.newPage();
const overflow = async (name) => {
  const o = await pp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (o > 1) problems.push(`${name}: scrolls sideways by ${o}px on a phone`);
};

// Helpdesk: raise
await pp.goto(`${site}/help`, { waitUntil: "networkidle" });
await overflow("/help");
await pp.getByLabel("Your name *").fill(`${RUN} Parent`);
await pp.getByLabel("Mobile number *").fill(PHONE);
await pp.locator('select[name="category"]').selectOption("TRANSPORT");
await pp.getByLabel("Subject *").fill(`${RUN} bus timing`);
await pp.getByLabel("Details *").fill("The bus came late today.");
await pp.getByRole("checkbox").check();
await pp.getByRole("button", { name: "Send request" }).click();
let ticketNo = null;
try {
  await pp.getByRole("status").waitFor({ timeout: 20000 });
  ticketNo = (await pp.getByRole("status").textContent())?.match(/HD-\d{5}/)?.[0] ?? null;
} catch {
  problems.push(
    `Helpdesk: raising failed: ${await pp
      .locator("[role=alert]")
      .first()
      .textContent()
      .catch(() => "?")}`,
  );
}
if (!ticketNo) problems.push("Helpdesk: no request number shown");
await pp.screenshot({ path: `${out}/help-raised.png`, fullPage: true });

// Staff reply (public) -> parent sees it when tracking
let ticketId = null;
if (ticketNo) {
  const list = await api(`engagement/helpdesk?view=all&q=${ticketNo}`);
  ticketId = list.body?.[0]?.id ?? null;
  if (!ticketId) problems.push("Helpdesk: staff can't find the new request");
  else {
    const r = await api(`engagement/helpdesk/${ticketId}/reply`, {
      method: "POST",
      data: { body: "We have informed the transport team.", isPublic: true },
    });
    if (r.status !== 200) problems.push(`Helpdesk reply: ${r.status}`);
    await api(`engagement/helpdesk/${ticketId}/reply`, {
      method: "POST",
      data: { body: "INTERNAL-ONLY note", isPublic: false },
    });
  }
  await pp.goto(`${site}/help`, { waitUntil: "networkidle" });
  await pp.getByRole("tab", { name: "Track a request" }).click();
  await pp.getByLabel("Request number").fill(ticketNo.toLowerCase());
  await pp.getByLabel("Mobile number").fill("9000049999");
  await pp.getByRole("button", { name: "Show status" }).click();
  await pp
    .getByText("We have informed the transport team.")
    .waitFor({ timeout: 15000 })
    .catch(() => problems.push("Helpdesk: parent does not see the staff reply"));
  if (await pp.getByText("INTERNAL-ONLY note").count())
    problems.push("Helpdesk: internal note visible to the parent!");
  await pp.getByLabel("Add a message").fill("Thank you.");
  await pp.getByRole("button", { name: "Send", exact: true }).click();
  await pp
    .getByText("Thank you.")
    .waitFor({ timeout: 15000 })
    .catch(() => problems.push("Helpdesk: parent reply not shown"));
  await pp.screenshot({ path: `${out}/help-tracked.png`, fullPage: true });
  // Wrong phone must not reveal the ticket.
  const wrong = await parent.request.post(`${site}/api/engage/helpdesk/lookup`, {
    data: { ticketNo, phone: "+91 90000 11111" },
  });
  if (wrong.status() !== 404)
    problems.push(`Helpdesk: lookup with the wrong phone returned ${wrong.status()}`);
}

// PTM booking
let code = null;
if (ev.body?.id) {
  await pp.goto(`${site}/ptm`, { waitUntil: "networkidle" });
  await overflow("/ptm");
  const evCard = pp.getByRole("radio", { name: new RegExp(`^${RUN} PTM`) });
  if (await evCard.count()) await evCard.check();
  await pp.getByRole("radio", { name: new RegExp(`^${RUN} Teacher`) }).check();
  await pp.getByRole("button", { name: "3:10 pm" }).click();
  await pp.getByLabel("Parent / guardian name *").fill(`${RUN} Parent`);
  await pp.getByLabel("Mobile number *").fill(PHONE);
  await pp.getByLabel("Student's name *").fill(`${RUN} Child`);
  await pp.getByRole("button", { name: /^Book 3:10 pm/ }).click();
  try {
    await pp.getByRole("status").waitFor({ timeout: 20000 });
    code =
      (await pp.getByRole("status").textContent())?.match(/code\s*([A-Z0-9]{6})/i)?.[1] ?? null;
  } catch {
    problems.push(
      `PTM: booking failed: ${await pp
        .locator("[role=alert]")
        .first()
        .textContent()
        .catch(() => "?")}`,
    );
  }
  if (!code) problems.push("PTM: no booking code shown");
  await pp.screenshot({ path: `${out}/ptm-booked.png`, fullPage: true });
  const sched = await api(`engagement/ptm/${ev.body.id}`);
  const booked = sched.body?.hosts?.[0]?.slots?.find((s) => s.start.startsWith("15:10"))?.booking;
  if (!booked) problems.push("PTM: staff schedule does not show the booking");
  // Same parent, same teacher again -> refused.
  const again = await parent.request.post(`${site}/api/engage/ptm/${ev.body.id}/book`, {
    data: {
      hostId: sched.body?.hosts?.[0]?.id,
      slotStart: "15:20",
      parentName: "x",
      phone: PHONE,
      studentName: "x",
    },
  });
  if (again.status() !== 409) problems.push(`PTM: double booking returned ${again.status()}`);
  if (code) {
    const c = await parent.request.post(`${site}/api/engage/ptm/cancel`, {
      data: { code, phone: PHONE },
    });
    if (c.status() !== 204) problems.push(`PTM: cancel returned ${c.status()}`);
  }
}

// Survey
if (sv.body?.slug) {
  await pp.goto(`${site}/survey/${sv.body.slug}`, { waitUntil: "networkidle" });
  await overflow("/survey");
  await pp.getByRole("button", { name: "4 out of 5" }).click();
  await pp.getByRole("radio", { name: "Yes" }).check();
  await pp.getByRole("textbox", { name: "Suggestions" }).fill("More shade please");
  await pp.getByRole("button", { name: "Submit" }).click();
  await pp
    .getByText("Thank you!")
    .waitFor({ timeout: 15000 })
    .catch(() => problems.push("Survey: submission not confirmed"));
  const res = await api(`engagement/surveys/${sv.body.id}/results`);
  const q1 = res.body?.questions?.[0];
  if (q1?.average !== 4) problems.push(`Survey: rating average is ${q1?.average}, expected 4`);
}
await parent.close();

// ---- Studio screens
await sp.goto(`${studio}/parents`, { waitUntil: "networkidle" });
await sp.waitForTimeout(1200);
if (ticketNo && !(await sp.getByText(ticketNo).count()))
  problems.push("Studio: request not in the helpdesk list");
await sp.screenshot({ path: `${out}/studio-helpdesk.png`, fullPage: true });
await sp.getByRole("button", { name: "PTM", exact: true }).click();
await sp.waitForTimeout(1000);
await sp.screenshot({ path: `${out}/studio-ptm.png`, fullPage: true });
await sp.getByRole("button", { name: "Surveys", exact: true }).click();
await sp.waitForTimeout(1000);
await sp.screenshot({ path: `${out}/studio-surveys.png`, fullPage: true });

const phoneStaff = await browser.newContext({
  ...devices["iPhone 13"],
  defaultBrowserType: undefined,
  storageState: await staff.storageState(),
});
const psp = await phoneStaff.newPage();
await psp.goto(`${studio}/parents`, { waitUntil: "networkidle" });
await psp.waitForTimeout(1200);
const so = await psp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
if (so > 1) problems.push(`Studio /parents scrolls sideways by ${so}px on a phone`);
await psp.screenshot({ path: `${out}/studio-phone.png`, fullPage: true });
await phoneStaff.close();

// ---- Clean up
const del = async (p) => {
  const r = await api(p, { method: "DELETE" });
  if (r.status !== 204) problems.push(`Cleanup ${p}: ${r.status}`);
};
if (ticketId) await del(`engagement/helpdesk/${ticketId}`);
if (ev.body?.id) await del(`engagement/ptm/${ev.body.id}`);
if (sv.body?.id) await del(`engagement/surveys/${sv.body.id}`);
await browser.close();
console.log(
  problems.length
    ? `PROBLEMS (${problems.length}):\n- ${problems.join("\n- ")}`
    : "All checks passed.",
);
process.exit(problems.length ? 1 : 0);
