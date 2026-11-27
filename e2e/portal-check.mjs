// End-to-end check of the parent portal (M12) on staging: invite -> setup on a phone -> child,
// preferences (consent), a circular arriving in the portal, sign-out/in, lockout after wrong
// PINs, access removal, and the demo parent. Cleans up after itself.
// Usage: SEED_PASSWORD=... node portal-check.mjs <site url> <studio url> <outDir>
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [site, studio, out = "portal-shots"] = process.argv
  .slice(2)
  .map((s) => s?.replace(/\/$/, ""));
if (!site || !studio)
  throw new Error("usage: node portal-check.mjs <site url> <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const RUN = `qa${Date.now().toString().slice(-6)}`;
// A fresh number per run, so the per-phone sign-in rate limit of earlier runs doesn't apply.
const PHONE = `+91 90000 7${String(Date.now() % 10000).padStart(4, "0")}`;
const PIN = "2580";
const problems = [];
const browser = await chromium.launch();

const staff = await browser.newContext();
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
const cleanup = async () => {
  for (const b of (await api("comms/broadcasts")).body ?? [])
    if (/^qa\d{6} /.test(b.title)) await api(`comms/broadcasts/${b.id}`, { method: "DELETE" });
  for (const s of (await api("sis/students?status=ALL&q=qa")).body ?? [])
    if (/^qa\d{6} /.test(s.name)) await api(`sis/students/${s.id}`, { method: "DELETE" });
  for (const c of (await api("comms/contacts?q=qa")).body ?? [])
    if (/^qa\d{6} /.test(c.name)) await api(`comms/contacts/${c.id}`, { method: "DELETE" });
};
await cleanup();

const sections = (await api("sis/sections")).body ?? [];
const c3 = sections.find((s) => s.label === "Class 3 A");
const st = await api("sis/students", {
  method: "POST",
  data: {
    name: `${RUN} Kid`,
    gender: "FEMALE",
    sectionId: c3?.id,
    guardians: [{ name: `${RUN} Parent`, phone: PHONE, relation: "MOTHER" }],
  },
});
const sid = st.body?.student?.id;
const gid = st.body?.guardians?.[0]?.guardianId;
if (!sid || !gid) problems.push(`Create student: ${st.status}`);
const inv = gid ? await api(`portal/guardians/${gid}/invite`, { method: "POST" }) : { body: null };
const link = inv.body?.link;
if (!link?.startsWith(`${site}/parent/setup?code=`)) problems.push(`Invite link: ${link}`);

// Parent on a phone.
const parent = await browser.newContext({ ...devices["Galaxy S8"], reducedMotion: "reduce" });
const pp = await parent.newPage();
// Next adds its own (empty) role=alert route announcer; read the form's message.
const alertText = async () =>
  (await pp.locator("form [role=alert]").first().textContent({ timeout: 8000 }).catch(() => "")) ?? "";
if (link) {
  await pp.goto(link, { waitUntil: "networkidle" });
  await pp.getByLabel("Mobile number").fill("+91 90000 11111");
  await pp.getByLabel("Choose a PIN (4–6 digits)").fill(PIN);
  await pp.getByLabel("Repeat the PIN").fill(PIN);
  await pp.getByRole("button", { name: "Create account" }).click();
  if (!(await alertText()).includes("not the mobile number"))
    problems.push("Setup accepted a different phone number");
  await pp.getByLabel("Mobile number").fill(PHONE);
  await pp.getByLabel("Choose a PIN (4–6 digits)").fill("1234");
  await pp.getByLabel("Repeat the PIN").fill("1234");
  await pp.getByRole("button", { name: "Create account" }).click();
  if (!(await alertText()).toLowerCase().includes("pin"))
    problems.push("Setup accepted a running-number PIN");
  await pp.getByLabel("Choose a PIN (4–6 digits)").fill(PIN);
  await pp.getByLabel("Repeat the PIN").fill(PIN);
  await pp.getByRole("button", { name: "Create account" }).click();
  await pp
    .waitForURL(/\/parent$/, { timeout: 15000 })
    .catch(() => problems.push("Setup did not open the portal"));
  await pp
    .getByText(`${RUN} Kid`)
    .first()
    .waitFor({ timeout: 15000 })
    .catch(() => problems.push("Portal does not show the child"));
  if (!(await pp.getByText("Class 3 A").count())) problems.push("Portal does not show the class");
  const o = await pp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (o > 1) problems.push(`Portal scrolls sideways by ${o}px on a phone`);
  // The invite works once.
  const again = await parent.request.post(`${site}/api/portal/setup`, {
    headers: { "X-Portal": "1" },
    data: { code: link.split("code=")[1], phone: PHONE, pin: "3691" },
  });
  if (again.status() !== 404) problems.push(`Reusing the invite returned ${again.status()}`);

  // Preferences: opt in to WhatsApp (a consent event).
  await pp.getByLabel("WhatsApp messages").check();
  await pp.getByRole("button", { name: "Save preferences" }).click();
  await pp
    .getByText("Saved.")
    .waitFor({ timeout: 10000 })
    .catch(() => problems.push("Preferences not saved"));
  const contact = ((await api(`comms/contacts?q=${RUN}`)).body ?? []).find(
    (c) => c.name === `${RUN} Parent`,
  );
  if (!contact?.whatsappOptIn) problems.push("WhatsApp opt-in from the portal not stored");
  const log = contact ? ((await api(`comms/contacts/${contact.id}`)).body?.consent ?? []) : [];
  if (!log.some((e) => e.source === "PORTAL" && e.optedIn && e.channel === "WHATSAPP"))
    problems.push("No PORTAL consent event");

  // A circular to this class reaches the portal.
  const b = await api("comms/broadcasts", {
    method: "POST",
    data: {
      title: `${RUN} Fee reminder`,
      details: "QA circular",
      templateName: "school_circular",
      channels: ["WHATSAPP"],
      audienceTags: ["Class 3 A"],
      ackRequired: true,
      emergency: true,
    },
  });
  if (b.body?.id) {
    // Only this parent: restrict to the QA contact by tagging it uniquely.
    await api(`comms/contacts/${contact.id}`, {
      method: "PATCH",
      data: { tags: ["Class 3 A", RUN] },
    });
    await api(`comms/broadcasts/${b.body.id}`, {
      method: "PATCH",
      data: {
        title: `${RUN} Fee reminder`,
        details: "QA circular",
        templateName: "school_circular",
        channels: ["WHATSAPP"],
        audienceTags: [RUN],
        ackRequired: true,
        emergency: true,
      },
    });
    await api(`comms/broadcasts/${b.body.id}/send`, { method: "POST" });
    let status = null;
    for (let i = 0; i < 16 && status !== "SENT"; i++) {
      await new Promise((r) => setTimeout(r, 7500));
      status = (await api(`comms/broadcasts/${b.body.id}`)).body?.status;
    }
    await pp.reload({ waitUntil: "networkidle" });
    await pp
      .getByText(`${RUN} Fee reminder`)
      .waitFor({ timeout: 15000 })
      .catch(() => problems.push(`Circular not in the portal (broadcast ${status})`));
    if (!(await pp.getByText("Please acknowledge").count()))
      problems.push("Portal does not ask to acknowledge");
  } else problems.push(`Create broadcast: ${b.status}`);
  await pp.screenshot({ path: `${out}/portal-phone.png`, fullPage: true });

  // Sign out and in.
  await pp.getByRole("button", { name: "Sign out" }).click();
  await pp
    .getByRole("button", { name: "Sign in" })
    .waitFor({ timeout: 10000 })
    .catch(() => problems.push("Sign out did not show the sign-in form"));
  await pp.getByLabel("Mobile number").fill(PHONE);
  await pp.getByLabel("PIN").fill("9999");
  await pp.getByRole("button", { name: "Sign in" }).click();
  if (!(await alertText()).includes("not right")) problems.push("Wrong PIN not rejected");
  await pp.getByLabel("PIN").fill(PIN);
  await pp.getByRole("button", { name: "Sign in" }).click();
  await pp
    .getByText(`${RUN} Kid`)
    .first()
    .waitFor({ timeout: 15000 })
    .catch(() => problems.push("Sign in with the right PIN failed"));

  // Removing access signs the parent out.
  await api(`portal/guardians/${gid}/disable`, { method: "POST" });
  const me = await parent.request.get(`${site}/api/portal/me`);
  if (me.status() !== 401) problems.push(`After removal /me returned ${me.status()}`);

  // Lockout: re-invite (new account state), set up, then 5 wrong PINs lock even the right one.
  const inv2 = await api(`portal/guardians/${gid}/invite`, { method: "POST" });
  const code2 = inv2.body?.link?.split("code=")[1];
  const fresh = await browser.newContext();
  const setup2 = await fresh.request.post(`${site}/api/portal/setup`, {
    headers: { "X-Portal": "1" },
    data: { code: code2, phone: PHONE, pin: PIN },
  });
  if (!setup2.ok()) problems.push(`Second setup: ${setup2.status()}`);
  for (let i = 0; i < 5; i++)
    await fresh.request.post(`${site}/api/portal/login`, {
      headers: { "X-Portal": "1" },
      data: { phone: PHONE, pin: "7777" },
    });
  const locked = await fresh.request.post(`${site}/api/portal/login`, {
    headers: { "X-Portal": "1" },
    data: { phone: PHONE, pin: PIN },
  });
  if (locked.status() !== 429)
    problems.push(`After 5 wrong PINs the right PIN returned ${locked.status()} (expected locked)`);
  await fresh.close();
}
await parent.close();

// The demo parent.
const demo = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const dp = await demo.newPage();
await dp.goto(`${site}/parent`, { waitUntil: "networkidle" });
await dp.getByLabel("Mobile number").fill("+91 90000 50001");
await dp.getByLabel("PIN").fill("2468");
await dp.getByRole("button", { name: "Sign in" }).click();
await dp
  .getByText("Aarav Sharma")
  .waitFor({ timeout: 15000 })
  .catch(() => problems.push("Demo parent does not see Aarav"));
if (!(await dp.getByText("Tara Sharma").count())) problems.push("Demo parent does not see Tara");
await dp.screenshot({ path: `${out}/portal-demo.png`, fullPage: true });
await demo.close();

await cleanup();
await browser.close();
console.log(
  problems.length
    ? `PROBLEMS (${problems.length}):\n- ${problems.join("\n- ")}`
    : "All checks passed.",
);
process.exit(problems.length ? 1 : 0);
