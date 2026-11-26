// End-to-end check of the communication hub (M13) on staging: Studio composes a circular to a
// throwaway contact, the dispatcher sends it (test mode), the parent opens the personal link on a
// phone, answers and acknowledges, then stops messages. Cleans up after itself.
// Usage: SEED_PASSWORD=... node comms-check.mjs <studio url> <outDir>
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [studio, out = "comms-shots"] = process.argv.slice(2).map((s) => s?.replace(/\/$/, ""));
if (!studio) throw new Error("usage: node comms-check.mjs <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const RUN = `qa${Date.now().toString().slice(-6)}`;
const problems = [];
const browser = await chromium.launch();

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

// Leftovers of interrupted runs.
for (const b of (await api("comms/broadcasts")).body ?? []) {
  if (/^qa\d{6} /.test(b.title)) {
    if (b.status === "SCHEDULED") await api(`comms/broadcasts/${b.id}/cancel`, { method: "POST" });
    await api(`comms/broadcasts/${b.id}`, { method: "DELETE" });
  }
}
for (const c of (await api("comms/contacts?q=qa")).body ?? []) {
  if (/^qa\d{6} /.test(c.name)) await api(`comms/contacts/${c.id}`, { method: "DELETE" });
}

const contact = await api("comms/contacts", {
  method: "POST",
  data: {
    name: `${RUN} Parent`,
    phone: "+91 90000 59999",
    email: "qa.parent@example.test",
    language: "hi",
    tags: [RUN],
    whatsappOptIn: true,
    emailOptIn: true,
    consentNote: "e2e test",
  },
});
if (contact.status !== 201)
  problems.push(`Create contact: ${contact.status} ${JSON.stringify(contact.body)}`);

const bc = await api("comms/broadcasts", {
  method: "POST",
  data: {
    title: `${RUN} Sports day permission`,
    details: "Sports day is on Friday. Please confirm your child can take part.",
    templateName: "school_circular",
    channels: ["WHATSAPP", "EMAIL"],
    audienceTags: [RUN],
    ackRequired: true,
    consentQuestion: "May your child take part in the races?",
    emergency: true,
  },
});
if (bc.status !== 201) problems.push(`Create broadcast: ${bc.status} ${JSON.stringify(bc.body)}`);
const id = bc.body?.id;

let link = null;
if (id) {
  const pv = await api(`comms/broadcasts/${id}/preview`);
  if (pv.body?.byChannel?.WHATSAPP !== 1)
    problems.push(
      `Preview: expected 1 WhatsApp recipient, got ${JSON.stringify(pv.body?.byChannel)}`,
    );
  if (!pv.body?.sample?.includes("प्रिय"))
    problems.push("Preview: the Hindi template was not chosen for a Hindi-speaking parent");
  if (!pv.body?.testMode) problems.push("Preview: expected test mode on staging");
  await sp.goto(`${studio}/messages`, { waitUntil: "networkidle" });
  await sp.getByRole("button", { name: new RegExp(`^${RUN} Sports day`) }).click();
  await sp
    .getByText("Before you send")
    .waitFor({ timeout: 15000 })
    .catch(() => problems.push("Studio: review step not shown"));
  await sp.screenshot({ path: `${out}/review.png`, fullPage: true });
  await sp.getByRole("button", { name: /^Send to 1/ }).click();
  await sp
    .getByText("Queued for sending")
    .waitFor({ timeout: 10000 })
    .catch(() => problems.push("Studio: send did not confirm"));

  // The dispatcher runs every 30 s.
  let status = null;
  for (let i = 0; i < 16 && status !== "SENT"; i++) {
    await new Promise((r) => setTimeout(r, 7500));
    status = (await api(`comms/broadcasts/${id}`)).body?.status;
  }
  if (status !== "SENT") problems.push(`Broadcast did not reach SENT (status ${status})`);
  const dl = await api(`comms/broadcasts/${id}/deliveries`);
  const d = dl.body?.[0];
  if (d?.channel !== "WHATSAPP" || d?.status !== "SENT" || !d?.testMode)
    problems.push(`Delivery: ${JSON.stringify(d)}`);
  link = d?.link ?? null;
}

// Parent opens the personal link on a phone.
if (link) {
  const parent = await browser.newContext({ ...devices["Galaxy S8"], reducedMotion: "reduce" });
  const pp = await parent.newPage();
  // A link-preview bot (no JavaScript) must not count as a read.
  const bot = await parent.request.get(link);
  if (bot.status() !== 200) problems.push(`Circular page returned ${bot.status()}`);
  if ((await api(`comms/broadcasts/${id}`)).body?.stats?.read !== 0)
    problems.push("A link preview (no JavaScript) counted as read");
  await pp.goto(link, { waitUntil: "networkidle" });
  await pp
    .getByRole("heading", { name: new RegExp(`${RUN} Sports day`) })
    .waitFor({ timeout: 15000 })
    .catch(() => problems.push("Circular page: title not shown"));
  const o = await pp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (o > 1) problems.push(`Circular page scrolls sideways by ${o}px`);
  await pp.getByRole("radio", { name: "Yes" }).check();
  await pp.getByRole("button", { name: "I have read this" }).click();
  await pp
    .getByText(/Acknowledged on/)
    .waitFor({ timeout: 10000 })
    .catch(() => problems.push("Circular page: acknowledgement not confirmed"));
  await pp.screenshot({ path: `${out}/circular-phone.png`, fullPage: true });
  const s = (await api(`comms/broadcasts/${id}`)).body?.stats;
  if (s?.read !== 1 || s?.acknowledged !== 1 || s?.consentYes !== 1)
    problems.push(`Stats after acknowledgement: ${JSON.stringify(s)}`);
  await pp.getByRole("button", { name: "Don't want these messages?" }).click();
  await pp.getByRole("button", { name: "Yes, stop" }).click();
  await pp
    .getByText(/will not receive further messages/)
    .waitFor({ timeout: 10000 })
    .catch(() => problems.push("Stop messages not confirmed"));
  const c = await api(`comms/contacts/${contact.body?.id}`);
  if (c.body?.contact?.whatsappOptIn || c.body?.contact?.emailOptIn)
    problems.push("Contact still opted in after stopping");
  if (!c.body?.consent?.some((e) => e.source === "LINK" && !e.optedIn))
    problems.push("Consent history has no opt-out from the link");
  await parent.close();
}

// Studio screens.
if (id) {
  await sp.goto(`${studio}/messages`, { waitUntil: "networkidle" });
  await sp.getByRole("button", { name: new RegExp(`^${RUN} Sports day`) }).click();
  await sp.waitForTimeout(1500);
  await sp.screenshot({ path: `${out}/report.png`, fullPage: true });
}
await sp.goto(`${studio}/messages`, { waitUntil: "networkidle" });
await sp.getByRole("button", { name: "Contacts", exact: true }).click();
await sp.waitForTimeout(1200);
await sp.screenshot({ path: `${out}/contacts.png`, fullPage: true });
const ph = await browser.newContext({
  ...devices["iPhone 13"],
  defaultBrowserType: undefined,
  storageState: await staff.storageState(),
});
const php = await ph.newPage();
await php.goto(`${studio}/messages`, { waitUntil: "networkidle" });
await php.waitForTimeout(1200);
const so = await php.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
if (so > 1) problems.push(`Studio /messages scrolls sideways by ${so}px on a phone`);
await php.screenshot({ path: `${out}/studio-phone.png`, fullPage: true });
await ph.close();

// Clean up.
if (id) {
  const r = await api(`comms/broadcasts/${id}`, { method: "DELETE" });
  if (r.status !== 204) problems.push(`Cleanup broadcast: ${r.status}`);
}
if (contact.body?.id) {
  const r = await api(`comms/contacts/${contact.body.id}`, { method: "DELETE" });
  if (r.status !== 204) problems.push(`Cleanup contact: ${r.status}`);
}
await browser.close();
console.log(
  problems.length
    ? `PROBLEMS (${problems.length}):\n- ${problems.join("\n- ")}`
    : "All checks passed.",
);
process.exit(problems.length ? 1 : 0);
