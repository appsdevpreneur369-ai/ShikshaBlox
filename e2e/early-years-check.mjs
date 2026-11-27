// End-to-end check of early years (M31) on staging: the class teacher fills LKG A's diary,
// moves a milestone, adds an observation with a photo and a pickup adult, prints the learning
// story; the demo parent sees today's diary and the moment in the portal; validation and
// permissions. Removes what it created.
// Usage: SEED_PASSWORD=... node early-years-check.mjs <site url> <studio url> [outDir]
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [site, studio, out = "early-years-shots"] = process.argv.slice(2).map((s) => s?.replace(/\/$/, ""));
if (!site || !studio) throw new Error("usage: node early-years-check.mjs <site url> <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const RUN = `qa${Date.now().toString().slice(-6)}`;
const problems = [];
const browser = await chromium.launch();
const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
const png = fs.readFileSync(new URL("./fixtures/tiny.png", import.meta.url));

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
  return { ctx, page: p, api };
}

const t = await signIn(await browser.newContext({ ...devices["Galaxy S8"] }), "teacher@greenfield.test");
const ov = (await t.api("early-years/overview")).body;
const lkg = ov?.sections?.find((s) => s.label === "LKG A");
if (!lkg || ov.sections.some((s) => /\d/.test(s.label))) problems.push(`Teacher's pre-primary classes: ${ov?.sections?.map((s) => s.label)}`);
if ((ov?.milestones?.length ?? 0) < 10) problems.push("Milestones missing");

// Diary.
const rows = (await t.api(`early-years/diary?sectionId=${lkg?.id}&date=${today}`)).body ?? [];
const tara = rows.find((r) => r.name === "Tara Sharma");
if (!tara) problems.push("Tara not in LKG A diary");
const saved = await t.api(`early-years/diary?sectionId=${lkg?.id}&date=${today}`, {
  method: "PUT",
  data: [{ studentId: tara?.studentId, arrival: "08:45", meals: { Snack: "ALL", Lunch: "SOME" }, napFrom: "12:45", napTo: "13:20", toilet: 2, mood: "HAPPY", activities: "Clay modelling", note: `${RUN} lovely day` }],
});
if (saved.body?.find?.((r) => r.studentId === tara?.studentId)?.mood !== "HAPPY") problems.push(`Diary save: ${saved.status}`);
const badMeal = await t.api(`early-years/diary?sectionId=${lkg?.id}&date=${today}`, { method: "PUT", data: [{ studentId: tara?.studentId, meals: { Lunch: "LOTS" } }] });
if (badMeal.status !== 400) problems.push(`Bad meal value returned ${badMeal.status}`);
const future = await t.api(`early-years/diary?sectionId=${lkg?.id}&date=2099-01-01`, { method: "PUT", data: [] });
if (future.status !== 400) problems.push(`Future diary returned ${future.status}`);

// Milestone.
const m = ov?.milestones?.find((x) => x.domain === "Language and literacy");
await t.api("early-years/milestones", { method: "PUT", data: { studentId: tara?.studentId, milestoneId: m?.id, status: "ACHIEVED" } });
const board = (await t.api(`early-years/milestones?sectionId=${lkg?.id}`)).body;
if (board?.children?.find((c) => c.studentId === tara?.studentId)?.status?.[m?.id] !== "ACHIEVED") problems.push("Milestone not saved");

// Observation with photo.
const obs = await t.api("early-years/observations", {
  method: "POST",
  multipart: { studentId: tara?.studentId, note: `${RUN} built a tall tower and counted the blocks`, domain: "Cognitive", date: today, share: "true", photo: { name: "tower.png", mimeType: "image/png", buffer: png } },
});
if (obs.status !== 201 || !obs.body?.photoUrl) problems.push(`Observation: ${obs.status} ${JSON.stringify(obs.body)?.slice(0, 160)}`);
else if (!(await t.ctx.request.get(obs.body.photoUrl)).ok()) problems.push("Observation photo does not load");

// Pickup adult with photo.
const pick = await t.api("early-years/pickups", {
  method: "POST",
  multipart: { studentId: tara?.studentId, name: `${RUN} Uncle`, relation: "Uncle", phone: "+91 90000 50099", photo: { name: "uncle.png", mimeType: "image/png", buffer: png } },
});
const added = pick.body?.find?.((p) => p.name === `${RUN} Uncle`);
if (!added?.hasPhoto) problems.push(`Pickup: ${pick.status}`);

// Learning story.
const story = (await t.api(`early-years/story?studentId=${tara?.studentId}`)).body;
if (!story?.observations?.some((o) => o.note.startsWith(RUN))) problems.push("Story lacks the observation");
if (!Object.values(story?.achieved ?? {}).flat().length) problems.push("Story lacks achieved milestones");
const tp = t.page;
await tp.goto(`${studio}/early-years`, { waitUntil: "networkidle" });
await tp.getByRole("button", { name: "Child: story & pickup" }).click();
await tp.getByText("learning story").first().waitFor({ timeout: 15000 }).catch(() => problems.push("Story screen did not render"));
const ow = await tp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
if (ow > 1) problems.push(`Story screen scrolls sideways by ${ow}px`);
await tp.screenshot({ path: `${out}/story-phone.png`, fullPage: true });
for (const tab of ["Daily diary", "Milestones", "Observations"]) {
  await tp.getByRole("button", { name: tab, exact: true }).click();
  await tp.waitForTimeout(1200);
  if (await tp.getByText(/Something went wrong|You do not have permission/).count()) problems.push(`Tab "${tab}" shows an error`);
  await tp.screenshot({ path: `${out}/${tab.replace(/\W+/g, "-").toLowerCase()}.png`, fullPage: true });
}

// Permissions.
const nursery = (await (await signIn(await browser.newContext(), "admin@greenfield.test")).api("early-years/overview")).body?.sections?.find((s) => s.label === "Nursery A");
if ((await t.api(`early-years/diary?sectionId=${nursery?.id}`)).status !== 403) problems.push("Teacher could open another class's diary");
const rec = await signIn(await browser.newContext(), "reception@greenfield.test");
if ((await rec.api("early-years/overview")).status !== 403) problems.push("Reception could open early years");

// Parent sees the feed.
const parent = await browser.newContext({ ...devices["Galaxy S8"] });
const login = await parent.request.post(`${site}/api/portal/login`, { headers: { "X-Portal": "1" }, data: { phone: "+91 90000 50001", pin: "2468" } });
if (login.ok()) {
  const pp = await parent.newPage();
  await pp.goto(`${site}/parent`, { waitUntil: "networkidle" });
  const sec = pp.getByRole("region", { name: "Today at school" });
  await sec.waitFor({ timeout: 15000 }).catch(() => problems.push("Portal has no early-years feed"));
  const text = (await sec.textContent().catch(() => "")) ?? "";
  for (const w of ["Tara Sharma", `${RUN} lovely day`, `${RUN} built a tall tower`]) if (!text.includes(w)) problems.push(`Feed lacks "${w}"`);
  const ow2 = await pp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (ow2 > 1) problems.push(`Portal scrolls sideways by ${ow2}px`);
  await sec.screenshot({ path: `${out}/portal-feed.png` }).catch(() => {});
} else problems.push(`Demo parent sign-in: ${login.status()}`);

// Clean up.
if (obs.body?.id) await t.api(`early-years/observations/${obs.body.id}`, { method: "DELETE" });
if (added?.id) await t.api(`early-years/pickups/${added.id}`, { method: "DELETE" });
await t.api(`early-years/diary?sectionId=${lkg?.id}&date=${today}`, { method: "PUT", data: [{ studentId: tara?.studentId, arrival: "08:45", meals: { Snack: "ALL", Lunch: "SOME" }, mood: "HAPPY", activities: "Clay modelling" }] });

await browser.close();
if (problems.length) {
  console.log("PROBLEMS:\n- " + problems.join("\n- "));
  process.exit(1);
}
console.log("All checks passed.");
