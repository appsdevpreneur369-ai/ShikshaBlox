// End-to-end check of attendance & leave (M07) on staging: the demo teacher takes today's roll
// call on a phone (only their class), an absence alert reaches the hub, a past-day change becomes
// a correction the office approves, the parent sees the absence and applies for leave in the
// portal, the teacher approves it (absence turns into leave), registers and CSV, chronic-absence
// list, and permissions. Cleans up its sample student afterwards.
// Usage: SEED_PASSWORD=... node attendance-check.mjs <site url> <studio url> [outDir]
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [site, studio, out = "attendance-shots"] = process.argv
  .slice(2)
  .map((s) => s?.replace(/\/$/, ""));
if (!site || !studio)
  throw new Error("usage: node attendance-check.mjs <site url> <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const RUN = `qa${Date.now().toString().slice(-6)}`;
const PHONE = `+91 90000 8${String(Date.now() % 10000).padStart(4, "0")}`;
const PIN = "2580";
const problems = [];
const ist = (d = new Date()) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(d);
const TODAY = ist();
const shift = (iso, days) => {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
};
let PAST = shift(TODAY, -1);
if (new Date(`${PAST}T12:00:00Z`).getUTCDay() === 0) PAST = shift(PAST, -1);
const browser = await chromium.launch();

async function signIn(ctx, email) {
  const p = await ctx.newPage();
  await p.goto(`${studio}/login`);
  await p.getByLabel("Email").fill(email);
  await p.getByLabel("Password").fill(process.env.SEED_PASSWORD ?? "");
  await p.getByRole("button", { name: "Sign in" }).click();
  await p.waitForURL(/dashboard/, { timeout: 30000 });
  const api = async (path, o = {}) => {
    const r = await ctx.request.fetch(`${studio}/api/backend/${path}`, {
      headers: { "X-Studio": "1" },
      ...o,
    });
    const text = r.status() === 204 ? "" : await r.text();
    let body = null;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = text;
    }
    return { status: r.status(), body };
  };
  return { page: p, api };
}

const office = await signIn(await browser.newContext(), "admin@greenfield.test");
const api = office.api;
const cleanup = async () => {
  for (const s of (await api("sis/students?status=ALL&q=qa")).body ?? [])
    if (/^qa\d{6} /.test(s.name)) await api(`sis/students/${s.id}`, { method: "DELETE" });
  for (const c of (await api("comms/contacts?q=qa")).body ?? [])
    if (/^qa\d{6} /.test(c.name)) await api(`comms/contacts/${c.id}`, { method: "DELETE" });
};
await cleanup();

// A sample child in Class 1 A whose parent has opted in to WhatsApp.
const sections = (await api("sis/sections")).body ?? [];
const c1a = sections.find((s) => s.label === "Class 1 A");
const c3a = sections.find((s) => s.label === "Class 3 A");
if (!c1a || !c3a) problems.push("Demo classes missing");
const st = await api("sis/students", {
  method: "POST",
  data: {
    name: `${RUN} Kid`,
    gender: "MALE",
    sectionId: c1a?.id,
    guardians: [{ name: `${RUN} Parent`, phone: PHONE, relation: "FATHER" }],
  },
});
const sid = st.body?.student?.id;
const gid = st.body?.guardians?.[0]?.guardianId;
if (!sid || !gid) problems.push(`Create student: ${st.status}`);
const contact = ((await api(`comms/contacts?q=${RUN}`)).body ?? []).find(
  (c) => c.name === `${RUN} Parent`,
);
if (contact)
  await api(`comms/contacts/${contact.id}`, {
    method: "PATCH",
    data: { ...contact, whatsappOptIn: true, consentNote: "QA check" },
  });
else problems.push("Guardian not synced to message contacts");

// ---------------------------------------------------------------- teacher on a phone
const tctx = await browser.newContext({ ...devices["Galaxy S8"], reducedMotion: "reduce" });
const teacher = await signIn(tctx, "teacher@greenfield.test");
const tp = teacher.page;
await tp.goto(`${studio}/attendance`, { waitUntil: "networkidle" });
const tiles = await tp.locator("ul li button").allTextContents();
if (!tiles.some((t) => t.includes("Class 1 A"))) problems.push("Teacher does not see Class 1 A");
if (tiles.some((t) => t.includes("Class 3 A"))) problems.push("Teacher sees a class not theirs");
await tp.getByRole("button", { name: /Class 1 A/ }).click();
await tp.getByText(`${RUN} Kid`).waitFor({ timeout: 15000 }).catch(() => problems.push("Roll call lacks the new child"));
const o = await tp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
if (o > 1) problems.push(`Roll call scrolls sideways by ${o}px on a phone`);
await tp.getByRole("radiogroup", { name: `Attendance for ${RUN} Kid` }).getByRole("radio", { name: "Absent" }).click();
await tp.screenshot({ path: `${out}/roll-call-phone.png`, fullPage: true });
const t0 = Date.now();
await tp.getByRole("button", { name: /Save (attendance|changes)/ }).click();
await tp.getByText(/marked on/).waitFor({ timeout: 15000 }).catch(() => problems.push("Save did not return to the class list"));
const saveSecs = (Date.now() - t0) / 1000;
if (saveSecs > 10) problems.push(`Saving took ${saveSecs}s`);

let sheet = (await teacher.api(`attendance/sheet?sectionId=${c1a?.id}&date=${TODAY}`)).body;
let row = sheet?.rows?.find((r) => r.studentId === sid);
if (row?.status !== "A") problems.push(`Saved status is ${row?.status}`);
if (!row?.alert || row.alert.startsWith("Not sent")) problems.push(`Alert: ${row?.alert}`);
// The hub dispatcher runs every 30 s.
for (let i = 0; i < 9 && row?.alert !== "Alert sent (test mode)" && row?.alert !== "Alert sent"; i++) {
  await new Promise((r) => setTimeout(r, 10000));
  sheet = (await teacher.api(`attendance/sheet?sectionId=${c1a?.id}&date=${TODAY}`)).body;
  row = sheet?.rows?.find((r) => r.studentId === sid);
}
if (!/^Alert sent/.test(row?.alert ?? "")) problems.push(`Alert not sent after 90 s: ${row?.alert}`);

// Saving again doesn't send a second alert.
const again = await teacher.api("attendance/sheet", {
  method: "PUT",
  data: {
    sectionId: c1a?.id,
    date: TODAY,
    period: 0,
    marks: sheet.rows.map((r) => ({ studentId: r.studentId, status: r.status ?? "P" })),
  },
});
if (again.body?.alerts !== 0) problems.push(`Re-save sent ${again.body?.alerts} alerts`);

// Not their class; not office features.
if ((await teacher.api(`attendance/sheet?sectionId=${c3a?.id}`)).status !== 403)
  problems.push("Teacher could open another class");
if ((await teacher.api("attendance/settings", { method: "PUT", data: { alertLate: true } })).status !== 403)
  problems.push("Teacher could change settings");
if ((await teacher.api("attendance/teachers")).status !== 403) problems.push("Teacher could manage teachers");
if ((await teacher.api("sis/students")).status !== 403) problems.push("Teacher could open student records");

// A past, already-marked day: the teacher's change becomes a correction.
const past = (await teacher.api(`attendance/sheet?sectionId=${c1a?.id}&date=${PAST}`)).body;
if (past?.editMode !== "CORRECTION") problems.push(`Past day edit mode: ${past?.editMode}`);
const aarav = past?.rows?.find((r) => r.name === "Aarav Sharma");
const corr = await teacher.api("attendance/sheet", {
  method: "PUT",
  data: {
    sectionId: c1a?.id,
    date: PAST,
    period: 0,
    marks: [{ studentId: sid, status: "P" }],
    correctionReason: `${RUN} joined that day`,
  },
});
if (corr.body?.corrections !== 1) problems.push(`Correction request: ${corr.status} ${JSON.stringify(corr.body)?.slice(0, 200)}`);
const pending = (await api("attendance/corrections?status=PENDING")).body ?? [];
const mine = pending.find((c) => c.studentId === sid);
if (!mine) problems.push("Office does not see the correction");
else {
  const ok = await api(`attendance/corrections/${mine.id}/approve`, { method: "POST" });
  if (ok.status !== 204) problems.push(`Approve correction: ${ok.status}`);
  const after = (await api(`attendance/sheet?sectionId=${c1a?.id}&date=${PAST}`)).body;
  if (after?.rows?.find((r) => r.studentId === sid)?.status !== "P")
    problems.push("Approved correction not applied");
}
if (!aarav) problems.push("Demo data: Aarav missing from the past roll call");

// ---------------------------------------------------------------- parent portal
const inv = gid ? await api(`portal/guardians/${gid}/invite`, { method: "POST" }) : { body: null };
const link = inv.body?.link;
const parent = await browser.newContext({ ...devices["Galaxy S8"], reducedMotion: "reduce" });
const pp = await parent.newPage();
if (link) {
  await pp.goto(link, { waitUntil: "networkidle" });
  await pp.getByLabel("Mobile number").fill(PHONE);
  await pp.getByLabel("Choose a PIN (4–6 digits)").fill(PIN);
  await pp.getByLabel("Repeat the PIN").fill(PIN);
  await pp.getByRole("button", { name: "Create account" }).click();
  await pp.waitForURL(/\/parent$/, { timeout: 15000 }).catch(() => problems.push("Portal setup failed"));
  await pp.getByRole("heading", { name: "Attendance & leave" }).waitFor({ timeout: 15000 }).catch(() => problems.push("Portal has no attendance section"));
  if (!(await pp.getByText(/: Absent/).count())) problems.push("Portal does not show today's absence");
  await pp.getByLabel("From").fill(TODAY);
  await pp.getByLabel("To (optional)").fill(shift(TODAY, 1));
  await pp.getByLabel("Reason", { exact: true }).fill(`${RUN} fever`);
  await pp.getByRole("button", { name: "Send to class teacher" }).click();
  await pp.getByText("Sent to the class teacher").waitFor({ timeout: 10000 }).catch(() => problems.push("Leave not sent"));
  if (!(await pp.getByText("Waiting for the class teacher").count())) problems.push("Leave status not shown");
  const ov = await pp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (ov > 1) problems.push(`Portal scrolls sideways by ${ov}px`);
  await pp.screenshot({ path: `${out}/portal-leave-phone.png`, fullPage: true });
  // Overlapping request refused.
  const dup = await parent.request.post(`${site}/api/portal/leave`, {
    headers: { "X-Portal": "1" },
    data: { studentId: sid, fromDate: TODAY, toDate: TODAY, reason: "dup" },
  });
  if (dup.status() !== 409) problems.push(`Overlapping leave returned ${dup.status()}`);
} else problems.push("No invite link");

// The teacher approves; today's absence becomes leave.
const leaves = (await teacher.api("attendance/leave?status=PENDING")).body ?? [];
const req = leaves.find((l) => l.studentId === sid);
if (!req) problems.push("Teacher does not see the leave request");
else {
  const ap = await teacher.api(`attendance/leave/${req.id}/approve`, { method: "POST", data: {} });
  if (ap.body?.status !== "APPROVED") problems.push(`Approve leave: ${ap.status}`);
  const s2 = (await teacher.api(`attendance/sheet?sectionId=${c1a?.id}&date=${TODAY}`)).body;
  if (s2?.rows?.find((r) => r.studentId === sid)?.status !== "LV")
    problems.push("Approved leave did not turn the absence into leave");
  await pp.reload({ waitUntil: "networkidle" });
  if (!(await pp.getByText("Approved", { exact: true }).count())) problems.push("Parent does not see the approval");
}
// Declining needs a note.
const demoPending = leaves.find((l) => l.studentName === "Tara Sharma");
if (demoPending) {
  const r = await teacher.api(`attendance/leave/${demoPending.id}/reject`, { method: "POST", data: {} });
  if (r.status !== 400) problems.push(`Declining without a note returned ${r.status}`);
}

// ---------------------------------------------------------------- registers and lists
const month = TODAY.slice(0, 7);
const reg = (await teacher.api(`attendance/register?sectionId=${c1a?.id}&month=${month}`)).body;
if (!reg?.days?.length || !reg?.rows?.length) problems.push("Register is empty");
if (reg?.rows?.some((r) => r.yearPercent != null && (r.yearPercent < 0 || r.yearPercent > 100)))
  problems.push("Register % out of range");
const csv = await teacher.api(`attendance/register.csv?sectionId=${c1a?.id}&month=${month}`);
if (csv.status !== 200 || !String(csv.body).includes("Roll no,Student")) problems.push(`Register CSV: ${csv.status}`);
const concerns = (await api("attendance/concerns")).body ?? [];
if (!concerns.some((c) => c.name === "Neel Gupta")) problems.push("Chronic absentee not listed");
const ovw = (await api(`attendance/overview?date=${TODAY}`)).body;
if (!ovw?.canManage || (ovw?.sections?.length ?? 0) < 5) problems.push("Office overview incomplete");

// Office screens render.
const op = office.page;
for (const tab of ["Leave", "Corrections", "Registers", "Needs attention", "Teachers & settings"]) {
  await op.goto(`${studio}/attendance`, { waitUntil: "networkidle" });
  await op.getByRole("button", { name: new RegExp(`^${tab}`) }).click();
  await op.waitForTimeout(1500);
  const err = await op.getByText(/Something went wrong|You do not have permission/).count();
  if (err) problems.push(`Office tab "${tab}" shows an error`);
  await op.screenshot({ path: `${out}/office-${tab.replace(/\W+/g, "-").toLowerCase()}.png`, fullPage: true });
}

await cleanup();
await browser.close();
if (problems.length) {
  console.log("PROBLEMS:\n- " + problems.join("\n- "));
  process.exit(1);
}
console.log(`All checks passed (save took ${saveSecs.toFixed(1)}s).`);
