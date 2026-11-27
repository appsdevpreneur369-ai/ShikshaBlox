// End-to-end check of homework (M10) on staging: the class teacher posts homework with a rubric
// and an attachment, the demo parent opens it on a phone, downloads the attachment and hands in
// work with a photo, the teacher marks it with the rubric and the parent sees the mark; date and
// class rules, permissions. Deletes what it created.
// Usage: SEED_PASSWORD=... node homework-check.mjs <site url> <studio url> [outDir]
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [site, studio, out = "homework-shots"] = process.argv.slice(2).map((s) => s?.replace(/\/$/, ""));
if (!site || !studio) throw new Error("usage: node homework-check.mjs <site url> <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const RUN = `qa${Date.now().toString().slice(-6)}`;
const problems = [];
const browser = await chromium.launch();
const ist = (d = new Date()) => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(d);
const TODAY = ist();
const plus = (n) => {
  const d = new Date(`${TODAY}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};

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

const teacher = await signIn(await browser.newContext({ ...devices["Galaxy S8"] }), "teacher@greenfield.test");
const cleanup = async () => {
  for (const h of (await teacher.api("homework")).body ?? [])
    if (h.title?.startsWith("qa")) await teacher.api(`homework/${h.id}`, { method: "DELETE" });
};
await cleanup();
const ov = (await teacher.api("homework/overview")).body;
const c1a = ov?.sections?.find((s) => s.label === "Class 1 A");
if (!c1a || ov.sections.some((s) => s.label === "Class 3 A")) problems.push("Teacher's classes are wrong");
const eng = ov?.subjects?.find((s) => s.grade === "Class 1" && s.name === "English");
const sections = (await (await signIn(await browser.newContext(), "admin@greenfield.test")).api("homework/overview")).body?.sections ?? [];
const c3a = sections.find((s) => s.label === "Class 3 A");

const base = { subjectId: eng?.id, title: `${RUN} Spelling list`, details: "Learn these ten words.", allowSubmissions: true, rubric: [{ criterion: "Spelling", points: 5 }, { criterion: "Handwriting", points: 5 }], latePolicy: "ACCEPT", notifyParents: true };
const past = await teacher.api("homework", { method: "POST", data: { ...base, sectionIds: [c1a?.id], dueOn: plus(-1) } });
if (past.status !== 400) problems.push(`Past due date returned ${past.status}`);
const other = await teacher.api("homework", { method: "POST", data: { ...base, sectionIds: [c3a?.id], dueOn: plus(2) } });
if (other.status !== 403) problems.push(`Posting to another class returned ${other.status}`);
const made = await teacher.api("homework", { method: "POST", data: { ...base, sectionIds: [c1a?.id], dueOn: plus(2) } });
const hw = made.body?.[0];
if (made.status !== 201 || !hw?.id) problems.push(`Post homework: ${made.status}`);
const up = await teacher.api(`homework/${hw?.id}/files`, {
  method: "POST",
  multipart: { file: { name: "spelling-list.txt", mimeType: "text/plain", buffer: Buffer.from("cat, bat, hat, mat, rat, sat, pat, fat, vat, chat") } },
});
if (up.body?.files?.length !== 1) problems.push(`Attachment: ${up.status} ${JSON.stringify(up.body)?.slice(0, 120)}`);

// ---------------------------------------------------------------- parent on a phone
const parent = await browser.newContext({ ...devices["Galaxy S8"], reducedMotion: "reduce" });
const login = await parent.request.post(`${site}/api/portal/login`, { headers: { "X-Portal": "1" }, data: { phone: "+91 90000 50001", pin: "2468" } });
if (!login.ok()) problems.push(`Demo parent sign-in: ${login.status()}`);
else {
  const pp = await parent.newPage();
  await pp.goto(`${site}/parent`, { waitUntil: "networkidle" });
  const item = pp.getByRole("button", { name: new RegExp(`${RUN} Spelling list`) });
  await item.waitFor({ timeout: 15000 }).catch(() => problems.push("Portal does not show the homework"));
  await item.click();
  const fileId = up.body?.files?.[0]?.id;
  const link = await parent.request.get(`${site}/api/portal/homework/files/${fileId}`);
  const url = link.ok() ? (await link.json()).url : null;
  if (!url) problems.push(`Attachment link: ${link.status()}`);
  else if (!(await parent.request.get(url)).ok()) problems.push("Attachment download failed");
  const form = pp.locator("li", { has: item }).locator("form");
  await form.locator('textarea[name="text"]').fill(`${RUN} cat bat hat mat rat sat pat fat vat chat`);
  await form.locator('input[name="file"]').setInputFiles({ name: "work.png", mimeType: "image/png", buffer: fs.readFileSync(new URL("./fixtures/tiny.png", import.meta.url)) }).catch(async () => {
    // no fixture: hand in text only
  });
  await form.getByRole("button", { name: "Hand in" }).click();
  await pp.getByText("Handed in. The teacher will see it.").waitFor({ timeout: 15000 }).catch(() => problems.push("Hand-in failed"));
  const ow = await pp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (ow > 1) problems.push(`Portal scrolls sideways by ${ow}px`);
  await pp.screenshot({ path: `${out}/portal-homework.png`, fullPage: true });

  // ---------------------------------------------------------------- teacher marks
  const subs = (await teacher.api(`homework/${hw?.id}/submissions`)).body ?? [];
  const aarav = subs.find((s) => s.studentName === "Aarav Sharma");
  if (aarav?.status !== "SUBMITTED") problems.push(`Submission status: ${aarav?.status}`);
  const over = await teacher.api(`homework/submissions/${aarav?.id}/grade`, { method: "POST", data: { rubricScores: { Spelling: 6 }, feedback: "x", returnForRedo: false } });
  if (over.status !== 400) problems.push(`Score above rubric max returned ${over.status}`);
  const g = (await teacher.api(`homework/submissions/${aarav?.id}/grade`, { method: "POST", data: { rubricScores: { Spelling: 4, Handwriting: 5 }, feedback: "Well done! Watch 'chat'.", returnForRedo: false } })).body;
  if (g?.status !== "GRADED" || g?.points !== 9) problems.push(`Grade: ${g?.status} ${g?.points}`);
  await pp.reload({ waitUntil: "networkidle" });
  if (!(await pp.getByText("Marked 9").count())) problems.push("Parent does not see the mark");
}

// ---------------------------------------------------------------- screens and permissions
const tp = teacher.page;
await tp.goto(`${studio}/homework`, { waitUntil: "networkidle" });
await tp.getByRole("button", { name: new RegExp(`${RUN} Spelling list`) }).click().catch(() => problems.push("Teacher list lacks the homework"));
await tp.getByText("Aarav Sharma").first().waitFor({ timeout: 10000 }).catch(() => problems.push("Submissions view did not open"));
const ow2 = await tp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
if (ow2 > 1) problems.push(`Teacher view scrolls sideways by ${ow2}px`);
await tp.screenshot({ path: `${out}/teacher-submissions.png`, fullPage: true });
await tp.goto(`${studio}/homework`, { waitUntil: "networkidle" });
await tp.getByRole("button", { name: "New homework" }).click();
await tp.getByLabel("Title").waitFor({ timeout: 10000 }).catch(() => problems.push("New homework form did not open"));
const reception = await signIn(await browser.newContext(), "reception@greenfield.test");
if ((await reception.api("homework")).status !== 403) problems.push("Reception could open homework");

await cleanup();
await browser.close();
if (problems.length) {
  console.log("PROBLEMS:\n- " + problems.join("\n- "));
  process.exit(1);
}
console.log("All checks passed.");
