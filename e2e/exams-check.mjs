// End-to-end check of exams & report cards (M11) on staging: the class teacher enters and submits
// marks on a phone, invalid marks are refused, the exam cell moderates and publishes (blocked
// while a class subject waits), parents see results in the portal, report cards and hall tickets
// print, analysis, permissions. Puts Periodic Test 2 back the way the demo seed left it.
// Usage: SEED_PASSWORD=... node exams-check.mjs <site url> <studio url> [outDir]
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [site, studio, out = "exams-shots"] = process.argv.slice(2).map((s) => s?.replace(/\/$/, ""));
if (!site || !studio) throw new Error("usage: node exams-check.mjs <site url> <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const problems = [];
process.on("uncaughtException", async (e) => {
  console.log("CRASHED:", e.message);
  process.exit(1);
});
const browser = await chromium.launch();

async function signIn(ctx, email) {
  const p = await ctx.newPage();
  await p.goto(`${studio}/login`);
  await p.getByLabel("Email").fill(email);
  await p.getByLabel("Password").fill(process.env.SEED_PASSWORD ?? "");
  await p.getByRole("button", { name: "Sign in" }).click();
  await p.waitForURL(/dashboard/, { timeout: 30000 });
  const api = async (path, o = {}) => {
    const r = await ctx.request.fetch(`${studio}/api/backend/${path}`, { headers: { "X-Studio": "1" }, ...o });
    const t = r.status() === 204 ? "" : await r.text();
    let body = null;
    try {
      body = t ? JSON.parse(t) : null;
    } catch {
      body = t;
    }
    return { status: r.status(), body };
  };
  return { page: p, api };
}

const cell = await signIn(await browser.newContext(), "exams@greenfield.test");
// Puts Periodic Test 2 back as seeded: open, Class 1 A English with one mark, Class 3 A Maths submitted.
async function restore(examId) {
  const p = (await cell.api(`exams/${examId}/progress`)).body ?? [];
  const e1 = p.find((t) => t.section === "Class 1 A" && t.subject === "English");
  const m3 = p.find((t) => t.section === "Class 3 A" && t.subject === "Mathematics");
  await cell.api(`exams/${examId}/status`, { method: "POST", data: { status: "MARKS_OPEN" } });
  for (const t of [e1, m3]) {
    if (t && t.status !== "OPEN")
      await cell.api(`exams/${examId}/marks/moderate?sectionId=${t.sectionId}&subjectId=${t.subjectId}`, {
        method: "POST",
        data: { approve: false, note: "Reopened by the exam cell" },
      });
  }
  if (e1) {
    const qq = `sectionId=${e1.sectionId}&subjectId=${e1.subjectId}`;
    const sh = (await cell.api(`exams/${examId}/marks?${qq}`)).body;
    await cell.api(`exams/${examId}/marks?${qq}`, {
      method: "PUT",
      data: (sh?.rows ?? []).map((r, i) => ({ studentId: r.studentId, marks: i === 0 ? { M: 17 } : {} })),
    });
  }
  if (m3) {
    const r = await cell.api(`exams/${examId}/marks/submit?sectionId=${m3.sectionId}&subjectId=${m3.subjectId}`, { method: "POST" });
    if (r.body?.entryStatus !== "SUBMITTED") problems.push(`Restore Class 3 A: ${r.status}`);
  }
}
let ov = (await cell.api("exams/overview")).body;
let pt2 = ov?.exams?.find((e) => e.name === "Periodic Test 2");
if (pt2 && pt2.status !== "MARKS_OPEN") {
  await restore(pt2.id); // an earlier run stopped part-way
  ov = (await cell.api("exams/overview")).body;
  pt2 = ov?.exams?.find((e) => e.name === "Periodic Test 2");
}
const hy = ov?.exams?.find((e) => e.name === "Half-Yearly Examination");
if (!pt2 || pt2.status !== "MARKS_OPEN") problems.push(`Periodic Test 2: ${pt2?.status}`);
if (hy?.status !== "PUBLISHED") problems.push("Half-Yearly not published");
const before = (await cell.api("exams/subjects")).body?.length ?? 0;
await cell.api("exams/subjects/preset", { method: "POST", data: { grades: ov?.grades } });
if (((await cell.api("exams/subjects")).body?.length ?? 0) !== before) problems.push("Preset added duplicate subjects");

// ---------------------------------------------------------------- class teacher on a phone
const tctx = await browser.newContext({ ...devices["Galaxy S8"], reducedMotion: "reduce" });
const teacher = await signIn(tctx, "teacher@greenfield.test");
const tasks = (await teacher.api(`exams/${pt2?.id}/progress`)).body ?? [];
const eng = tasks.find((t) => t.section === "Class 1 A" && t.subject === "English");
if (!eng) problems.push("Teacher has no Class 1 A English task");
if (tasks.some((t) => t.section !== "Class 1 A")) problems.push("Teacher sees other classes' tasks");
const tp = teacher.page;
await tp.goto(`${studio}/exams`, { waitUntil: "networkidle" });
await tp.getByRole("button", { name: /English/ }).first().click();
const firstInput = tp.getByRole("spinbutton").first();
await firstInput.waitFor({ timeout: 15000 }).catch(() => problems.push("Marks sheet did not open"));
const inputs = await tp.getByRole("spinbutton").all();
let v = 12;
for (const i of inputs) await i.fill(String((v = v >= 19 ? 11 : v + 1.5)));
const ow = await tp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
if (ow > 1) problems.push(`Marks page scrolls sideways by ${ow}px (the table should scroll on its own)`);
await tp.screenshot({ path: `${out}/marks-phone.png`, fullPage: true });
await tp.getByRole("button", { name: "Save marks" }).click();
await tp.getByText("Marks saved.").waitFor({ timeout: 10000 }).catch(() => problems.push("Marks not saved"));
const q = `sectionId=${eng?.sectionId}&subjectId=${eng?.subjectId}`;
const sheet = (await teacher.api(`exams/${pt2?.id}/marks?${q}`)).body;
if (sheet?.rows?.some((r) => r.marks?.M == null)) problems.push("Some marks not stored");
const bad = await teacher.api(`exams/${pt2?.id}/marks?${q}`, {
  method: "PUT",
  data: [{ studentId: sheet?.rows?.[0]?.studentId, marks: { M: 21 } }],
});
if (bad.status !== 400) problems.push(`Marks above maximum returned ${bad.status}`);
const sub = await teacher.api(`exams/${pt2?.id}/marks/submit?${q}`, { method: "POST" });
if (sub.body?.entryStatus !== "SUBMITTED") problems.push(`Submit: ${sub.status}`);
const locked = await teacher.api(`exams/${pt2?.id}/marks?${q}`, { method: "PUT", data: [] });
if (locked.status !== 409) problems.push(`Editing submitted marks returned ${locked.status}`);
const rem = await teacher.api(`exams/${pt2?.id}/remarks?sectionId=${eng?.sectionId}`);
if (rem.status !== 200) problems.push(`Remarks: ${rem.status}`);
const c3 = (await cell.api(`exams/${pt2?.id}/progress`)).body?.find((t) => t.section === "Class 3 A" && t.subject === "Mathematics");
if ((await teacher.api(`exams/${pt2?.id}/marks?sectionId=${c3?.sectionId}&subjectId=${c3?.subjectId}`)).status !== 403)
  problems.push("Teacher could open another class's marks");
if ((await teacher.api("exams/settings", { method: "PUT", data: { passPercent: 40 } })).status !== 403)
  problems.push("Teacher could change grading");
if ((await teacher.api(`exams/${pt2?.id}/status`, { method: "POST", data: { status: "PUBLISHED" } })).status !== 403)
  problems.push("Teacher could publish");

// ---------------------------------------------------------------- exam cell: moderation and publishing
const ap = await cell.api(`exams/${pt2?.id}/marks/moderate?${q}`, { method: "POST", data: { approve: true } });
if (ap.body?.entryStatus !== "APPROVED") problems.push(`Approve: ${ap.status}`);
const blocked = await cell.api(`exams/${pt2?.id}/status`, { method: "POST", data: { status: "PUBLISHED" } });
if (blocked.status !== 409) problems.push(`Publishing with a subject waiting returned ${blocked.status}`);
const q3 = `sectionId=${c3?.sectionId}&subjectId=${c3?.subjectId}`;
await cell.api(`exams/${pt2?.id}/marks/moderate?${q3}`, { method: "POST", data: { approve: true } });
const pub = await cell.api(`exams/${pt2?.id}/status`, { method: "POST", data: { status: "PUBLISHED" } });
if (pub.body?.status !== "PUBLISHED") problems.push(`Publish: ${pub.status} ${JSON.stringify(pub.body)?.slice(0, 160)}`);

// Parent (demo) sees the results.
const parent = await browser.newContext({ ...devices["Galaxy S8"], reducedMotion: "reduce" });
const login = await parent.request.post(`${site}/api/portal/login`, {
  headers: { "X-Portal": "1" },
  data: { phone: "+91 90000 50001", pin: "2468" },
});
if (!login.ok()) problems.push(`Demo parent sign-in: ${login.status()}`);
const pp = await parent.newPage();
await pp.goto(`${site}/parent`, { waitUntil: "networkidle" });
await pp.getByRole("heading", { name: "Results & report cards" }).waitFor({ timeout: 15000 }).catch(() => problems.push("Portal has no results section"));
const txt = (await pp.getByRole("region", { name: "Results & report cards" }).textContent().catch(() => "")) ?? "";
for (const w of ["Aarav Sharma", "Half-Yearly Examination", "Periodic Test 2"]) if (!txt.includes(w)) problems.push(`Portal results lack ${w}`);
const ow2 = await pp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
if (ow2 > 1) problems.push(`Portal scrolls sideways by ${ow2}px`);
await pp.screenshot({ path: `${out}/portal-results-phone.png`, fullPage: true });

// ---------------------------------------------------------------- printouts and analysis
await tp.goto(`${studio}/print/report-cards?sectionId=${eng?.sectionId}`, { waitUntil: "networkidle" });
await tp.locator("article").first().waitFor({ timeout: 20000 }).catch(() => problems.push("Report cards did not render"));
const cards = await tp.locator("article").count();
if (cards < 2) problems.push(`Only ${cards} report card(s) for Class 1 A`);
if (!(await tp.getByText("Class teacher's remark").count())) problems.push("Report card lacks remarks");
await tp.setViewportSize({ width: 900, height: 1200 });
await tp.screenshot({ path: `${out}/report-card.png`, fullPage: false });
const cp = cell.page;
await cp.goto(`${studio}/print/hall-tickets?examId=${pt2?.id}&sectionId=${eng?.sectionId}`, { waitUntil: "networkidle" });
await cp.getByText("Hall ticket · Periodic Test 2").first().waitFor({ timeout: 20000 }).catch(() => problems.push("Hall tickets did not render"));
await cp.screenshot({ path: `${out}/hall-tickets.png`, fullPage: false });
const an = (await cell.api(`exams/${hy?.id}/analysis`)).body ?? [];
const a1 = an.find((a) => a.section === "Class 1 A");
if (!a1?.subjects?.length || a1.classAverage == null) problems.push("Analysis empty for Class 1 A");
for (const tab of ["Exams & timetable", "Subjects & teachers", "Analysis", "Grading"]) {
  await cp.goto(`${studio}/exams`, { waitUntil: "networkidle" });
  await cp.getByRole("button", { name: tab, exact: true }).click();
  await cp.waitForTimeout(1500);
  if (await cp.getByText(/Something went wrong|You do not have permission/).count()) problems.push(`Tab "${tab}" shows an error`);
  await cp.screenshot({ path: `${out}/cell-${tab.replace(/\W+/g, "-").toLowerCase()}.png`, fullPage: true });
}

await restore(pt2?.id);
await browser.close();
if (problems.length) {
  console.log("PROBLEMS:\n- " + problems.join("\n- "));
  process.exit(1);
}
console.log("All checks passed.");
