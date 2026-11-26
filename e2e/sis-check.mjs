// End-to-end check of student information (M04) on staging. Uses throwaway "qa…" students,
// guardians, a lead and contacts, and removes them afterwards. Year-end promotion is only
// planned (not executed), because it moves the shared demo school into the next year.
// Usage: SEED_PASSWORD=... node sis-check.mjs <studio url> <outDir>
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [studio, out = "sis-shots"] = process.argv.slice(2).map((s) => s?.replace(/\/$/, ""));
if (!studio) throw new Error("usage: node sis-check.mjs <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const RUN = `qa${Date.now().toString().slice(-6)}`;
const PHONE = "+91 90000 69999";
const problems = [];
const browser = await chromium.launch();
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
const api = async (p, o = {}) => {
  const r = await ctx.request.fetch(`${studio}/api/backend/${p}`, {
    headers: { "X-Studio": "1" },
    ...o,
  });
  return { status: r.status(), body: r.status() === 204 ? null : await r.json().catch(() => null) };
};
const cleanup = async () => {
  for (const s of (await api("sis/students?status=ALL&q=qa")).body ?? []) {
    if (/^qa\d{6} /.test(s.name)) await api(`sis/students/${s.id}`, { method: "DELETE" });
  }
  for (const l of (await api("admissions/leads?q=qa")).body ?? []) {
    if (/^qa\d{6} /.test(l.guardianName))
      await api(`admissions/leads/${l.id}`, { method: "DELETE" });
  }
  for (const c of (await api("comms/contacts?q=qa")).body ?? []) {
    if (/^qa\d{6} /.test(c.name)) await api(`comms/contacts/${c.id}`, { method: "DELETE" });
  }
};
await cleanup();

const settings = (await api("sis/settings")).body;
const sections = (await api(`sis/sections?year=${settings?.currentYear}`)).body ?? [];
const c2 = sections.find((s) => s.label === "Class 2 A");
const c4 = sections.find((s) => s.label === "Class 4 A");
if (!c2 || !c4) problems.push("Demo sections Class 2 A / Class 4 A missing");

// 1. Student with a guardian -> contact in Messages tagged with the class.
const a = await api("sis/students", {
  method: "POST",
  data: {
    name: `${RUN} Asha`,
    dateOfBirth: "2018-08-10",
    gender: "FEMALE",
    sectionId: c2?.id,
    rollNo: 31,
    address: "1 QA Street",
    guardians: [{ name: `${RUN} Parent`, phone: PHONE, relation: "MOTHER" }],
  },
});
if (a.status !== 201) problems.push(`Create student: ${a.status} ${JSON.stringify(a.body)}`);
const aid = a.body?.student?.id;
if (
  a.body?.student?.admissionNo &&
  !a.body.student.admissionNo.startsWith(settings.admissionPrefix)
)
  problems.push(`Admission no ${a.body.student.admissionNo} ignores the prefix`);
const contacts = (await api(`comms/contacts?q=${RUN}`)).body ?? [];
const contact = contacts.find((c) => c.name === `${RUN} Parent`);
if (!contact) problems.push("Guardian was not added to Messages contacts");
else {
  if (!contact.tags.includes("Class 2 A"))
    problems.push(`Contact tags ${JSON.stringify(contact.tags)} lack Class 2 A`);
  if (contact.whatsappOptIn || contact.smsOptIn || contact.emailOptIn)
    problems.push("Guardian contact was opted in automatically");
}

// 2. Sibling sharing the guardian.
const b = await api("sis/students", {
  method: "POST",
  data: {
    name: `${RUN} Bala`,
    gender: "MALE",
    sectionId: c4?.id,
    guardians: [{ name: `${RUN} Parent`, phone: PHONE, relation: "MOTHER" }],
  },
});
const bid = b.body?.student?.id;
if (!b.body?.guardians?.[0]?.siblings?.includes(`${RUN} Asha`))
  problems.push(`Sibling link missing: ${JSON.stringify(b.body?.guardians)}`);
const c2tags =
  ((await api(`comms/contacts?q=${RUN}`)).body ?? []).find((c) => c.name === `${RUN} Parent`)
    ?.tags ?? [];
if (!c2tags.includes("Class 4 A") || !c2tags.includes("Class 2 A"))
  problems.push(`Contact should carry both classes, has ${JSON.stringify(c2tags)}`);

// 3. Move, withdraw, reinstate.
if (aid) {
  const m = await api(`sis/students/${aid}/section`, {
    method: "POST",
    data: { sectionId: c4?.id, rollNo: 32 },
  });
  if (m.body?.enrolments?.[0]?.sectionLabel !== "Class 4 A") problems.push("Move section failed");
  const w = await api(`sis/students/${aid}/withdraw`, {
    method: "POST",
    data: { reason: "QA test", alumni: false },
  });
  if (w.body?.student?.status !== "WITHDRAWN") problems.push(`Withdraw: ${w.status}`);
  const r = await api(`sis/students/${aid}/reinstate`, {
    method: "POST",
    data: { sectionId: c2?.id },
  });
  if (r.body?.student?.status !== "ENROLLED") problems.push(`Reinstate: ${r.status}`);
}

// 4. CSV import (Indian date format, new class auto-created? no: existing class).
const csv = `admission_no,name,dob,gender,class,section,roll_no,guardian_name,guardian_relation,guardian_phone\n${RUN}-1,${RUN} Chitra,05-01-2019,F,Class 2,A,40,${RUN} Imp Parent,father,+91 90000 69998\n${RUN}-2,${RUN} Dev,bad-date,M,Class 2,A,41,,,`;
const imp = await api("sis/students/import", { method: "POST", data: { csv } });
if (imp.body?.added !== 1 || imp.body?.skipped !== 1)
  problems.push(`Import: ${JSON.stringify(imp.body)}`);

// 5. Enrol from an admissions lead.
const lead = await api("admissions/leads", {
  method: "POST",
  data: {
    studentName: `${RUN} Esha`,
    guardianName: `${RUN} Lead Parent`,
    phone: "+91 90000 69997",
    gradeSought: "Class 2",
    source: "WALK_IN",
  },
});
if (lead.body?.id) {
  await api(`admissions/leads/${lead.body.id}/stage`, {
    method: "POST",
    data: { stage: "OFFERED" },
  });
  const e = await api(`sis/students/from-lead/${lead.body.id}`, {
    method: "POST",
    data: { sectionId: c2?.id },
  });
  if (e.status !== 201 || e.body?.student?.leadId !== lead.body.id)
    problems.push(`Enrol from lead: ${e.status} ${JSON.stringify(e.body).slice(0, 200)}`);
  const after = (await api(`admissions/leads/${lead.body.id}`)).body?.lead;
  if (after?.stage !== "ADMITTED") problems.push(`Lead stage after enrolment: ${after?.stage}`);
  const again = await api(`sis/students/from-lead/${lead.body.id}`, {
    method: "POST",
    data: { sectionId: c2?.id },
  });
  if (again.status !== 409) problems.push(`Enrolling the same lead twice returned ${again.status}`);
} else problems.push(`Create lead: ${lead.status}`);

// 6. Readiness and promotion plan (not executed).
const ready = (await api("sis/readiness")).body;
if (!(ready?.enrolled > 0)) problems.push(`Readiness: ${JSON.stringify(ready)}`);
const plan = (await api("sis/promotion")).body;
const c5 = plan?.sections?.find((s) => s.label === "Class 5 A");
const c1 = plan?.sections?.find((s) => s.label === "Class 1 A");
if (c1?.targetGrade !== "Class 2")
  problems.push(`Promotion plan: Class 1 A should go to Class 2, got ${c1?.targetGrade}`);
if (c5 && c5.targetGrade !== null && c5.targetGrade !== undefined)
  problems.push("Promotion plan: the top class should graduate by default");
if (
  plan?.toYear !==
  `${Number(settings.currentYear.slice(0, 4)) + 1}-${String((Number(settings.currentYear.slice(0, 4)) + 2) % 100).padStart(2, "0")}`
)
  problems.push(`Promotion target year ${plan?.toYear}`);

// 7. Studio screens on desktop and phone.
await page.goto(`${studio}/students`, { waitUntil: "networkidle" });
await page.getByPlaceholder("Name, admission no., parent, phone").fill(RUN);
await page.waitForTimeout(1500);
const row = page.getByRole("button", { name: `${RUN} Asha` });
if (await row.count()) {
  await row.click();
  await page.waitForTimeout(1200);
} else problems.push("Studio: student not in the list");
await page.screenshot({ path: `${out}/students.png`, fullPage: true });
await page.getByRole("button", { name: "Year-end promotion" }).click();
await page.waitForTimeout(1200);
await page.screenshot({ path: `${out}/promotion.png`, fullPage: true });
await page.getByRole("button", { name: "Import & settings" }).click();
await page.waitForTimeout(1200);
await page.screenshot({ path: `${out}/setup.png`, fullPage: true });
const phone = await browser.newContext({
  ...devices["iPhone 13"],
  defaultBrowserType: undefined,
  storageState: await ctx.storageState(),
});
const pp = await phone.newPage();
await pp.goto(`${studio}/students`, { waitUntil: "networkidle" });
await pp.waitForTimeout(1500);
const o = await pp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
if (o > 1) problems.push(`Studio /students scrolls sideways by ${o}px on a phone`);
await pp.screenshot({ path: `${out}/phone.png`, fullPage: true });
await phone.close();

await cleanup();
const left = ((await api("sis/students?status=ALL&q=qa")).body ?? []).filter((s) =>
  /^qa\d{6} /.test(s.name),
);
if (left.length) problems.push(`Cleanup left ${left.length} students`);
await browser.close();
void bid;
console.log(
  problems.length
    ? `PROBLEMS (${problems.length}):\n- ${problems.join("\n- ")}`
    : "All checks passed.",
);
process.exit(problems.length ? 1 : 0);
