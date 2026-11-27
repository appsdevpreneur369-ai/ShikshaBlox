// End-to-end check of certificates (M32) on staging: reception issues a bonafide (QR verification
// on the school site), the office issues a TC for a student who left (no-dues checks, one TC per
// student, duplicate, cancellation), approval flow, a parent request from the portal fulfilled
// by the office, permissions. Cancels and removes everything it created.
// Usage: SEED_PASSWORD=... node certificates-check.mjs <site url> <studio url> [outDir]
import { chromium, devices } from "@playwright/test";
import fs from "node:fs";

const [site, studio, out = "certificates-shots"] = process.argv
  .slice(2)
  .map((s) => s?.replace(/\/$/, ""));
if (!site || !studio)
  throw new Error("usage: node certificates-check.mjs <site url> <studio url> [outDir]");
fs.mkdirSync(out, { recursive: true });
const RUN = `qa${Date.now().toString().slice(-6)}`;
const problems = [];
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
const created = [];
const cleanup = async () => {
  await admin.api("certificates/settings", { method: "PUT", data: { requireApproval: false } });
  for (const c of (await admin.api("certificates?status=REQUESTED")).body ?? [])
    if (/^qa\d{6} /.test(c.purpose ?? ""))
      await admin.api(`certificates/${c.id}/decision`, { method: "POST", data: { approve: false, note: "QA clean-up" } });
  for (const c of (await admin.api(`certificates?q=${RUN}`)).body ?? [])
    if (c.status === "ISSUED")
      await admin.api(`certificates/${c.id}/cancel`, {
        method: "POST",
        data: { reason: "QA clean-up" },
      });
  for (const id of created) {
    for (const c of (await admin.api("certificates?status=ISSUED")).body ?? [])
      if (c.studentId === id)
        await admin.api(`certificates/${c.id}/cancel`, {
          method: "POST",
          data: { reason: "QA clean-up" },
        });
    await admin.api(`sis/students/${id}`, { method: "DELETE" });
  }
  for (const s of (await admin.api("sis/students?status=ALL&q=qa")).body ?? [])
    if (/^qa\d{6} /.test(s.name)) {
      for (const c of (await admin.api(`certificates?q=${encodeURIComponent(s.name)}`)).body ?? [])
        if (c.status === "ISSUED")
          await admin.api(`certificates/${c.id}/cancel`, {
            method: "POST",
            data: { reason: "QA clean-up" },
          });
      await admin.api(`sis/students/${s.id}`, { method: "DELETE" });
    }
};
await cleanup();
const templates = (await admin.api("certificates/templates")).body ?? [];
const T = Object.fromEntries(templates.map((t) => [t.kind, t]));
if (!T.TC || !T.BONAFIDE || !T.CHARACTER || !T.STUDY) problems.push("Standard templates missing");

// A sample student in Class 2 A who has left.
const sections = (await admin.api("sis/sections")).body ?? [];
const sec = sections.find((s) => s.label === "Class 2 A") ?? sections[0];
const st = await admin.api("sis/students", {
  method: "POST",
  data: {
    name: `${RUN} Kid`,
    gender: "FEMALE",
    dateOfBirth: "2018-07-21",
    sectionId: sec?.id,
    guardians: [
      {
        name: `${RUN} Mother`,
        phone: `+91 90000 9${String(Date.now() % 10000).padStart(4, "0")}`,
        relation: "MOTHER",
      },
    ],
  },
});
const sid = st.body?.student?.id;
if (!sid) problems.push(`Create student: ${st.status}`);
else created.push(sid);

// ---------------------------------------------------------------- reception: bonafide
const rec = await signIn(await browser.newContext(), "reception@greenfield.test");
const hits = (await rec.api(`certificates/students?q=${RUN}`)).body ?? [];
if (!hits.some((h) => h.id === sid)) problems.push("Reception cannot find the student");
const draft = (
  await rec.api(
    `certificates/draft?templateId=${T.BONAFIDE?.id}&studentId=${sid}&purpose=bank%20account`,
  )
).body;
if (!draft?.body?.includes(`${RUN} Kid, daughter of ${RUN} Mother`))
  problems.push(`Bonafide text: ${draft?.body?.slice(0, 120)}`);
if (!draft?.body?.includes("bank account")) problems.push("Bonafide lacks the purpose");
const bc = (
  await rec.api("certificates", {
    method: "POST",
    data: { templateId: T.BONAFIDE?.id, studentId: sid, purpose: "bank account" },
  })
).body;
if (bc?.status !== "ISSUED" || !/^BC\/\d{4}$/.test(bc?.number ?? ""))
  problems.push(`Bonafide issue: ${bc?.status} ${bc?.number}`);
const rp = rec.page;
await rp.goto(`${studio}/print/certificate?id=${bc?.id}`, { waitUntil: "networkidle" });
await rp
  .getByRole("img", { name: /QR code/ })
  .waitFor({ timeout: 15000 })
  .catch(() => problems.push("Printed certificate has no QR code"));
await rp.screenshot({ path: `${out}/bonafide.png`, fullPage: true });
const vp = await (await browser.newContext({ ...devices["Galaxy S8"] })).newPage();
await vp.goto(bc?.verifyUrl ?? `${site}/verify/NONE0000`, { waitUntil: "networkidle" });
if (!(await vp.getByText("Genuine certificate").count()))
  problems.push(`Verify page: ${bc?.verifyUrl}`);
await vp.screenshot({ path: `${out}/verify-phone.png`, fullPage: true });
await vp.goto(`${site}/verify/ZZZZZZZZZZ`, { waitUntil: "networkidle" });
if (!(await vp.getByText(/No certificate with this code/).count()))
  problems.push("Unknown code not reported");

// ---------------------------------------------------------------- TC
const enrolledTc = (await admin.api(`certificates/draft?templateId=${T.TC?.id}&studentId=${sid}`))
  .body;
if (!enrolledTc?.problems?.length) problems.push("TC allowed for an enrolled student");
await admin.api(`sis/students/${sid}/withdraw`, {
  method: "POST",
  data: {
    date: new Date().toISOString().slice(0, 10),
    reason: "Family moved to Pune",
    alumni: false,
  },
});
const tcd = (await admin.api(`certificates/draft?templateId=${T.TC?.id}&studentId=${sid}`)).body;
const f = Object.fromEntries((tcd?.fields ?? []).map((x) => [x.key, x.value]));
if (tcd?.problems?.length) problems.push(`TC problems: ${tcd.problems.join("; ")}`);
if (f.dob_words !== "Twenty-First July Two Thousand Eighteen")
  problems.push(`DOB in words: ${f.dob_words}`);
if (f.mother !== `${RUN} Mother` || f.reason !== "Family moved to Pune")
  problems.push("TC fields not pre-filled");
const noDues = await admin.api("certificates", {
  method: "POST",
  data: { templateId: T.TC?.id, studentId: sid, fields: f, noDues: [] },
});
if (noDues.status !== 400) problems.push(`TC without no-dues returned ${noDues.status}`);
const tc = (
  await admin.api("certificates", {
    method: "POST",
    data: {
      templateId: T.TC?.id,
      studentId: sid,
      fields: { ...f, conduct: "Very good" },
      noDues: tcd?.noDuesItems ?? [],
    },
  })
).body;
if (tc?.status !== "ISSUED" || !tc?.number?.startsWith("TC/"))
  problems.push(`TC issue: ${tc?.status}`);
const again = await admin.api("certificates", {
  method: "POST",
  data: { templateId: T.TC?.id, studentId: sid, fields: f, noDues: tcd?.noDuesItems ?? [] },
});
if (again.status !== 409) problems.push(`Second TC returned ${again.status}`);
const dup = (
  await admin.api(`certificates/${tc?.id}/duplicate`, {
    method: "POST",
    data: { reason: "Original lost" },
  })
).body;
if (dup?.number !== `${tc?.number}/D1`) problems.push(`Duplicate number: ${dup?.number}`);
await admin.page.goto(`${studio}/print/certificate?id=${tc?.id}`, { waitUntil: "networkidle" });
await admin.page
  .getByText("Date of birth (in words)")
  .waitFor({ timeout: 15000 })
  .catch(() => problems.push("Printed TC lacks fields"));
await admin.page.screenshot({ path: `${out}/tc.png`, fullPage: true });
await admin.api(`certificates/${tc?.id}/cancel`, { method: "POST", data: { reason: "QA check" } });
await vp.goto(tc?.verifyUrl ?? site, { waitUntil: "networkidle" });
if (!(await vp.getByText("cancelled").count()) || !(await vp.getByText("duplicate").count()))
  problems.push("Verify page doesn't show cancelled / duplicate");
if ((await admin.api(`sis/students/${sid}`, { method: "DELETE" })).status !== 409)
  problems.push("Student with issued certificates could be deleted");

// ---------------------------------------------------------------- approval
await admin.api("certificates/settings", { method: "PUT", data: { requireApproval: true } });
const pend = await rec.api("certificates", {
  method: "POST",
  data: {
    templateId: T.CHARACTER?.id,
    studentId: hits[0]?.id ?? sid,
    purpose: `${RUN} competition`,
    fields: { conduct: "good" },
  },
});
// the sample student has left, which is fine for a character certificate
if (pend.body?.status !== "PENDING_APPROVAL" || pend.body?.number)
  problems.push(`Approval needed: ${pend.status} ${pend.body?.status}`);
if (
  (
    await rec.api(`certificates/${pend.body?.id}/decision`, {
      method: "POST",
      data: { approve: true },
    })
  ).status !== 403
)
  problems.push("Reception could approve");
const appr = (
  await admin.api(`certificates/${pend.body?.id}/decision`, {
    method: "POST",
    data: { approve: true },
  })
).body;
if (appr?.status !== "ISSUED" || !appr?.number) problems.push(`Approve: ${appr?.status}`);
await admin.api("certificates/settings", { method: "PUT", data: { requireApproval: false } });

// ---------------------------------------------------------------- parent request
const parent = await browser.newContext({ ...devices["Galaxy S8"], reducedMotion: "reduce" });
const login = await parent.request.post(`${site}/api/portal/login`, {
  headers: { "X-Portal": "1" },
  data: { phone: "+91 90000 50001", pin: "2468" },
});
if (!login.ok()) problems.push(`Demo parent sign-in: ${login.status()}`);
if (login.ok()) {
  const pp = await parent.newPage();
  await pp.goto(`${site}/parent`, { waitUntil: "networkidle" });
  await pp
    .getByRole("heading", { name: "Certificates" })
    .waitFor({ timeout: 15000 })
    .catch(() => problems.push("Portal has no certificates section"));
  await pp.locator('form select[name="type"]').selectOption({ label: "Study certificate" });
  await pp.locator('form select[name="child"]').last().selectOption({ label: "Tara Sharma" });
  await pp.locator('form input[name="purpose"]').fill(`${RUN} school transfer abroad`);
  await pp.getByRole("button", { name: "Send request" }).click();
  await pp
    .getByText("Request sent.")
    .waitFor({ timeout: 10000 })
    .catch(() => problems.push("Parent request not sent"));
  const req = ((await admin.api("certificates?status=REQUESTED")).body ?? []).find(
    (c) => c.purpose === `${RUN} school transfer abroad`,
  );
  if (!req) problems.push("Office does not see the parent request");
  else {
    const done = (
      await admin.api("certificates", {
        method: "POST",
        data: {
          templateId: req.templateId,
          studentId: req.studentId,
          purpose: req.purpose,
          requestId: req.id,
        },
      })
    ).body;
    if (done?.status !== "ISSUED" || done?.id !== req.id)
      problems.push(`Fulfil request: ${done?.status}`);
    await pp.reload({ waitUntil: "networkidle" });
    const row = pp.getByRole("listitem").filter({ hasText: `${RUN} school transfer abroad` });
    await row
      .getByRole("button", { name: "View" })
      .click()
      .catch(() => problems.push("Parent cannot view the certificate"));
    await pp
      .getByText("Tara Sharma, daughter of Priya Sharma")
      .waitFor({ timeout: 10000 })
      .catch(() => problems.push("Parent view lacks the text"));
    const ow = await pp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    if (ow > 1) problems.push(`Portal scrolls sideways by ${ow}px`);
    await pp.screenshot({ path: `${out}/portal-certificate.png`, fullPage: true });
    await admin.api(`certificates/${req.id}/cancel`, {
      method: "POST",
      data: { reason: "QA clean-up" },
    });
  }
}

// ---------------------------------------------------------------- permissions and screens
const teacher = await signIn(await browser.newContext(), "teacher@greenfield.test");
if ((await teacher.api("certificates")).status !== 403)
  problems.push("Teacher could open certificates");
if (
  (await rec.api("certificates/settings", { method: "PUT", data: { requireApproval: true } }))
    .status !== 403
)
  problems.push("Reception could change settings");
for (const tab of ["Register", "New certificate", "Templates & settings"]) {
  await admin.page.goto(`${studio}/certificates`, { waitUntil: "networkidle" });
  await admin.page.getByRole("button", { name: tab, exact: true }).click();
  await admin.page.waitForTimeout(1200);
  if (await admin.page.getByText(/Something went wrong|You do not have permission/).count())
    problems.push(`Tab "${tab}" shows an error`);
  await admin.page.screenshot({
    path: `${out}/office-${tab.replace(/\W+/g, "-").toLowerCase()}.png`,
    fullPage: true,
  });
}
if (pend.body?.id)
  await admin.api(`certificates/${pend.body.id}/cancel`, {
    method: "POST",
    data: { reason: "QA clean-up" },
  });

await cleanup();
await browser.close();
if (problems.length) {
  console.log("PROBLEMS:\n- " + problems.join("\n- "));
  process.exit(1);
}
console.log("All checks passed.");
