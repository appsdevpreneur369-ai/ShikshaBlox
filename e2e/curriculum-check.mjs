// End-to-end check of curriculum (M09) on staging: the seeded syllabus and coverage (with a unit
// behind plan), the coordinator edits the syllabus, the teacher marks topics covered on a phone,
// writes and submits a lesson plan which the coordinator reviews, an observation is recorded and
// acknowledged, the completion overview, permissions. Removes what it created.
// Usage: SEED_PASSWORD=... node curriculum-check.mjs <site url> <studio url> [outDir]
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [, studio, out = "curriculum-shots"] = process.argv.slice(2).map((s) => s?.replace(/\/$/, ""));
if (!studio) throw new Error("usage: node curriculum-check.mjs <site url> <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const RUN = `qa${Date.now().toString().slice(-6)}`;
const problems = [];
const browser = await chromium.launch();
const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
const month = Number(today.slice(5, 7));

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
const teacher = await signIn(await browser.newContext({ ...devices["Galaxy S8"] }), "teacher@greenfield.test");
const ov = (await cell.api("curriculum/overview")).body;
const c1a = ov?.sections?.find((s) => s.label === "Class 1 A");
const eng = ov?.subjects?.find((s) => s.grade === "Class 1" && s.name === "English");
const anita = ov?.staff?.find((s) => s.name === "Anita Desai");
const cleanup = async () => {
  const c = (await cell.api(`curriculum/coverage?subjectId=${eng?.id}`)).body;
  for (const u of c?.units ?? []) if (u.title.startsWith("qa")) await cell.api(`curriculum/units/${u.id}`, { method: "DELETE" });
  for (const p of (await teacher.api("curriculum/plans")).body ?? []) if (p.title.startsWith("qa")) await teacher.api(`curriculum/plans/${p.id}`, { method: "DELETE" });
  for (const o of (await cell.api("curriculum/observations")).body ?? []) if (o.strengths?.startsWith("qa")) await cell.api(`curriculum/observations/${o.id}`, { method: "DELETE" });
};
await cleanup();

const seeded = (await teacher.api(`curriculum/coverage?subjectId=${eng?.id}&sectionId=${c1a?.id}`)).body;
if ((seeded?.units?.length ?? 0) < 5) problems.push(`Seeded syllabus: ${seeded?.units?.length} units`);
if (!(seeded?.percent > 0 && seeded?.percent < 100)) problems.push(`Seeded coverage: ${seeded?.percent}%`);
if (month !== 4 && seeded?.behindUnits < 1) problems.push("No unit behind plan in the demo");

// Coordinator edits the syllabus.
let c = (await cell.api("curriculum/units", { method: "POST", data: { subjectId: eng?.id, title: `${RUN} Unit`, plannedMonth: month } })).body;
const unit = c?.units?.find((u) => u.title === `${RUN} Unit`);
if (!unit) problems.push("Unit not added");
c = (await cell.api(`curriculum/units/${unit?.id}/topics`, { method: "POST", data: { titles: [`${RUN} topic one`, "", `${RUN} topic two`] } })).body;
const topics = c?.units?.find((u) => u.id === unit?.id)?.topics ?? [];
if (topics.length !== 2) problems.push(`Topics added: ${topics.length}`);
if ((await teacher.api("curriculum/units", { method: "POST", data: { subjectId: eng?.id, title: "x" } })).status !== 403) problems.push("Teacher could edit the syllabus");

// Teacher marks coverage.
const before = seeded?.covered ?? 0;
c = (await teacher.api("curriculum/coverage", { method: "PUT", data: { sectionId: c1a?.id, topicId: topics[0]?.id, covered: true } })).body;
if (c?.covered !== before + 1) problems.push(`Coverage after marking: ${c?.covered} (was ${before})`);
const future = await teacher.api("curriculum/coverage", { method: "PUT", data: { sectionId: c1a?.id, topicId: topics[1]?.id, covered: true, date: "2099-01-01" } });
if (future.status !== 400) problems.push(`Future coverage date returned ${future.status}`);
const c3a = (await cell.api("curriculum/overview")).body?.sections?.find((s) => s.label === "Class 3 A");
if ((await teacher.api("curriculum/coverage", { method: "PUT", data: { sectionId: c3a?.id, topicId: topics[0]?.id, covered: true } })).status !== 403)
  problems.push("Teacher could mark another class");
const tp = teacher.page;
await tp.goto(`${studio}/curriculum`, { waitUntil: "networkidle" });
await tp.getByLabel("Subject").selectOption({ label: "English" });
await tp.getByText(`${RUN} topic one`).waitFor({ timeout: 10000 }).catch(() => problems.push("Coverage screen lacks the new topic"));
const ow = await tp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
if (ow > 1) problems.push(`Coverage screen scrolls sideways by ${ow}px`);
await tp.screenshot({ path: `${out}/coverage-phone.png`, fullPage: true });

// Lesson plan: write, submit, review.
const plan = (await teacher.api("curriculum/plans", { method: "POST", data: { sectionId: c1a?.id, subjectId: eng?.id, topicId: topics[1]?.id, plannedFor: today, title: `${RUN} Plan`, objectives: "Read aloud", activities: "Pairs", submit: true } })).body;
if (plan?.status !== "SUBMITTED") problems.push(`Plan submit: ${plan?.status}`);
const waiting = (await cell.api("curriculum/plans?status=SUBMITTED")).body ?? [];
if (!waiting.some((p) => p.id === plan?.id)) problems.push("Coordinator does not see the plan");
const rev = (await cell.api(`curriculum/plans/${plan?.id}/review`, { method: "POST", data: { note: "Good plan" } })).body;
if (rev?.status !== "REVIEWED" || rev?.reviewNote !== "Good plan") problems.push(`Review: ${rev?.status}`);
const edit = await teacher.api(`curriculum/plans/${plan?.id}`, { method: "PUT", data: { sectionId: c1a?.id, plannedFor: today, title: "changed" } });
if (edit.status !== 409) problems.push(`Editing a reviewed plan returned ${edit.status}`);

// Observation.
const obs = (await cell.api("curriculum/observations", { method: "POST", data: { teacherId: anita?.id, sectionId: c1a?.id, observedOn: today, scores: { Planning: 4, Engagement: 3 }, strengths: `${RUN} clear instructions`, suggestions: "More pair work" } })).body;
if (obs?.average !== 3.5) problems.push(`Observation average: ${obs?.average}`);
const bad = await cell.api("curriculum/observations", { method: "POST", data: { teacherId: anita?.id, observedOn: today, scores: { Planning: 5 } } });
if (bad.status !== 400) problems.push(`Score 5 returned ${bad.status}`);
if ((await teacher.api("curriculum/observations", { method: "POST", data: { teacherId: anita?.id, observedOn: today, scores: { Planning: 3 } } })).status !== 403)
  problems.push("Teacher could record an observation");
const ack = (await teacher.api(`curriculum/observations/${obs?.id}/acknowledge`, { method: "POST", data: { note: "Will try exit cards" } })).body;
if (!ack?.acknowledgedAt || ack?.reflection !== "Will try exit cards") problems.push("Acknowledge failed");

// Completion overview and screens.
const cells = (await cell.api("curriculum/coverage/overview")).body ?? [];
if (!cells.some((x) => x.section === "Class 1 A" && x.subject === "English")) problems.push("Completion overview lacks Class 1 A English");
if ((await teacher.api("curriculum/coverage/overview")).status !== 403) problems.push("Teacher could open the completion overview");
for (const tab of ["Syllabus & coverage", "Completion", "Lesson plans", "Observations"]) {
  await cell.page.goto(`${studio}/curriculum`, { waitUntil: "networkidle" });
  await cell.page.getByRole("button", { name: tab, exact: true }).click();
  await cell.page.waitForTimeout(1200);
  if (await cell.page.getByText(/Something went wrong|You do not have permission/).count()) problems.push(`Tab "${tab}" shows an error`);
  await cell.page.screenshot({ path: `${out}/coordinator-${tab.replace(/\W+/g, "-").toLowerCase()}.png`, fullPage: true });
}

await cleanup();
await browser.close();
if (problems.length) {
  console.log("PROBLEMS:\n- " + problems.join("\n- "));
  process.exit(1);
}
console.log("All checks passed.");
