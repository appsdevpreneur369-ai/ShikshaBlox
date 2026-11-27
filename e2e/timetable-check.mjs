// End-to-end check of timetables (M08) on staging: generated demo timetables have no teacher in
// two places, clash checks for teachers and rooms (with a clear message), the generator refills
// cleared periods, substitutions (teacher away, free-teacher suggestions, assign, back), the
// teacher's own week, the parent view, bell-schedule validation. Restores what it changes.
// Usage: SEED_PASSWORD=... node timetable-check.mjs <site url> <studio url> [outDir]
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [site, studio, out = "timetable-shots"] = process.argv.slice(2).map((s) => s?.replace(/\/$/, ""));
if (!site || !studio) throw new Error("usage: node timetable-check.mjs <site url> <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const problems = [];
const browser = await chromium.launch();
const ist = (d = new Date()) => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(d);
let D = new Date(`${ist()}T12:00:00Z`);
D.setUTCDate(D.getUTCDate() + 2);
if (D.getUTCDay() === 0) D.setUTCDate(D.getUTCDate() + 1);
const DATE = D.toISOString().slice(0, 10);

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

const admin = await signIn(await browser.newContext(), "admin@greenfield.test");
const api = admin.api;
const ov = (await api("timetable/overview")).body;
const sec = Object.fromEntries((ov?.sections ?? []).map((s) => [s.label, s]));
const anita = ov?.staff?.find((s) => s.name === "Anita Desai");
if (!sec["Class 1 A"] || !sec["Class 3 A"] || !anita) problems.push("Demo classes or teacher missing");
const g1 = (await api(`timetable/sections/${sec["Class 1 A"]?.id}`)).body;
if ((g1?.slots?.length ?? 0) < 30) problems.push(`Class 1 A has only ${g1?.slots?.length} periods`);
const aSlots = (await api(`timetable/teacher?teacherId=${anita?.id}`)).body ?? [];
const seen = new Set();
for (const s of aSlots) {
  const k = `${s.day}:${s.period}`;
  if (seen.has(k)) problems.push(`Anita double-booked at ${k}`);
  seen.add(k);
}
if (!aSlots.some((s) => s.section === "Class 1 B")) problems.push("Anita's Class 1 B periods missing");

// Teacher clash, with the reason in words.
const busy = aSlots.find((s) => s.section === "Class 1 A");
const g3 = (await api(`timetable/sections/${sec["Class 3 A"]?.id}`)).body;
const orig3 = g3?.slots?.find((s) => s.day === busy?.day && s.period === busy?.period);
const clash = await api(`timetable/sections/${sec["Class 3 A"]?.id}/slots`, { method: "PUT", data: { day: busy?.day, period: busy?.period, subjectId: g3?.loads?.[0]?.subjectId, teacherId: anita?.id } });
if (clash.status !== 409 || !String(clash.body?.detail).includes("already teaches Class 1 A")) problems.push(`Teacher clash: ${clash.status} ${clash.body?.detail}`);
// Room clash.
const r1 = await api(`timetable/sections/${sec["Class 3 A"]?.id}/slots`, { method: "PUT", data: { day: busy?.day, period: busy?.period, subjectId: orig3?.subjectId ?? g3?.loads?.[0]?.subjectId, label: orig3?.label ?? null, teacherId: orig3?.teacherId ?? null, room: "QA Lab" } });
if (r1.status !== 200) problems.push(`Set room: ${r1.status} ${r1.body?.detail}`);
const g5 = (await api(`timetable/sections/${sec["Class 5 A"]?.id}`)).body;
const orig5 = g5?.slots?.find((s) => s.day === busy?.day && s.period === busy?.period);
const r2 = await api(`timetable/sections/${sec["Class 5 A"]?.id}/slots`, { method: "PUT", data: { day: busy?.day, period: busy?.period, subjectId: orig5?.subjectId ?? g5?.loads?.[0]?.subjectId, label: orig5?.label ?? null, teacherId: orig5?.teacherId ?? null, room: "qa lab" } });
if (r2.status !== 409 || !String(r2.body?.detail).includes("Class 3 A")) problems.push(`Room clash: ${r2.status} ${r2.body?.detail}`);
// Put Class 3 A back.
if (orig3) await api(`timetable/sections/${sec["Class 3 A"]?.id}/slots`, { method: "PUT", data: { day: orig3.day, period: orig3.period, subjectId: orig3.subjectId, label: orig3.label, teacherId: orig3.teacherId, room: orig3.room } });
else await api(`timetable/sections/${sec["Class 3 A"]?.id}/slots?day=${busy?.day}&period=${busy?.period}`, { method: "DELETE" });

// Generator refills cleared periods of Class 2 A.
const g2 = (await api(`timetable/sections/${sec["Class 2 A"]?.id}`)).body;
const before = g2?.slots?.length ?? 0;
const victims = (g2?.slots ?? []).filter((s) => s.subjectId).slice(0, 3);
for (const v of victims) await api(`timetable/sections/${sec["Class 2 A"]?.id}/slots?day=${v.day}&period=${v.period}`, { method: "DELETE" });
const gen = (await api("timetable/generate", { method: "POST", data: { sectionIds: [sec["Class 2 A"]?.id] } })).body;
if (gen?.placed !== victims.length || gen?.unplaced?.length) problems.push(`Generator: placed ${gen?.placed}, unplaced ${gen?.unplaced}`);
if (((await api(`timetable/sections/${sec["Class 2 A"]?.id}`)).body?.slots?.length ?? 0) !== before) problems.push("Generator did not restore the period count");

// Substitutions.
let day = (await api("timetable/substitutions/away", { method: "POST", data: { date: DATE, userId: anita?.id, note: "QA", away: true } })).body;
const need = day?.needs?.[0];
if (!need || !need.suggestions?.length) problems.push(`Substitution needs: ${day?.needs?.length}`);
else {
  if (need.suggestions.some((s) => s.id === anita?.id)) problems.push("The absent teacher was suggested");
  day = (await api("timetable/substitutions/assign", { method: "POST", data: { date: DATE, sectionId: need.sectionId, period: need.period, substituteId: need.suggestions[0].id } })).body;
  const n2 = day?.needs?.find((n) => n.sectionId === need.sectionId && n.period === need.period);
  if (n2?.substituteId !== need.suggestions[0].id) problems.push("Substitute not saved");
  const busyAssign = await api("timetable/substitutions/assign", { method: "POST", data: { date: DATE, sectionId: need.sectionId, period: need.period, substituteId: anita?.id } });
  if (busyAssign.status !== 409) problems.push(`Assigning the absent teacher returned ${busyAssign.status}`);
}
await admin.page.goto(`${studio}/timetable`, { waitUntil: "networkidle" });
await admin.page.getByRole("button", { name: "Substitutions" }).click();
await admin.page.getByLabel("Date").fill(DATE);
await admin.page.getByText("Usually Anita Desai").first().waitFor({ timeout: 10000 }).catch(() => problems.push("Substitutions screen lacks the periods"));
await admin.page.screenshot({ path: `${out}/substitutions.png`, fullPage: true });
day = (await api("timetable/substitutions/away", { method: "POST", data: { date: DATE, userId: anita?.id, away: false } })).body;
if (day?.needs?.length) problems.push("Periods still listed after the teacher is back");

// Bell schedule validation.
const bad = await api("timetable/settings", { method: "PUT", data: { periods: [{ label: "P1", start: "09:00", end: "08:00", break: false }] } });
if (bad.status !== 400) problems.push(`Bad bell schedule returned ${bad.status}`);

// Screens.
for (const tab of ["Class timetable", "Teacher timetable", "Loads & generator", "Bell schedule"]) {
  await admin.page.goto(`${studio}/timetable`, { waitUntil: "networkidle" });
  await admin.page.getByRole("button", { name: tab, exact: true }).click();
  await admin.page.waitForTimeout(1200);
  if (await admin.page.getByText(/Something went wrong|You do not have permission/).count()) problems.push(`Tab "${tab}" shows an error`);
  await admin.page.screenshot({ path: `${out}/office-${tab.replace(/\W+/g, "-").toLowerCase()}.png`, fullPage: true });
}

// The teacher's own week, on a phone.
const t = await signIn(await browser.newContext({ ...devices["Galaxy S8"] }), "teacher@greenfield.test");
if (((await t.api("timetable/teacher")).body?.length ?? 0) < 10) problems.push("Teacher's own timetable is empty");
if ((await t.api(`timetable/teacher?teacherId=${ov?.staff?.find((s) => s.id !== anita?.id)?.id}`)).status !== 403) problems.push("Teacher could see another teacher's week");
if ((await t.api(`timetable/sections/${sec["Class 1 A"]?.id}/slots`, { method: "PUT", data: { day: 1, period: 0, label: "x" } })).status !== 403) problems.push("Teacher could change the timetable");
await t.page.goto(`${studio}/timetable`, { waitUntil: "networkidle" });
await t.page.getByRole("table").waitFor({ timeout: 10000 }).catch(() => problems.push("My timetable did not render"));
await t.page.screenshot({ path: `${out}/teacher-phone.png`, fullPage: true });

// Parent view.
const parent = await browser.newContext({ ...devices["Galaxy S8"] });
const login = await parent.request.post(`${site}/api/portal/login`, { headers: { "X-Portal": "1" }, data: { phone: "+91 90000 50001", pin: "2468" } });
if (login.ok()) {
  const pp = await parent.newPage();
  await pp.goto(`${site}/parent`, { waitUntil: "networkidle" });
  await pp.getByRole("heading", { name: "Timetable" }).waitFor({ timeout: 15000 }).catch(() => problems.push("Portal has no timetable"));
  const ow = await pp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (ow > 1) problems.push(`Portal scrolls sideways by ${ow}px`);
} else problems.push(`Demo parent sign-in: ${login.status()}`);

await browser.close();
if (problems.length) {
  console.log("PROBLEMS:\n- " + problems.join("\n- "));
  process.exit(1);
}
console.log("All checks passed.");
