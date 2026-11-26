// Checks that assigning people works everywhere it is offered (these paths look up staff inside a
// transaction): admissions lead -> counsellor, helpdesk request -> staff, compliance item -> owner.
// Restores the lead and request; the compliance item keeps its new owner. Usage: SEED_PASSWORD=... node assignment-check.mjs <studio url>
import { chromium } from "@playwright/test";

const [studio] = process.argv.slice(2).map((s) => s?.replace(/\/$/, ""));
if (!studio) throw new Error("usage: node assignment-check.mjs <studio url>");
const problems = [];
const browser = await chromium.launch();
const ctx = await browser.newContext();
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
  return { status: r.status(), body: await r.json().catch(() => null) };
};

// Admissions: assign an open lead to the counsellor, then back to unassigned is not supported, so
// restore by assigning to whoever it had (or leave the counsellor if it had nobody).
const counsellors = (await api("admissions/counsellors")).body ?? [];
const counsellor = counsellors.find((c) => c.email?.startsWith("counsellor@"));
const leads = (await api("admissions/leads")).body ?? [];
const lead = leads.find((l) => !["ADMITTED", "LOST"].includes(l.stage));
if (!counsellor || !lead) problems.push("Admissions: no counsellor or open lead to test with");
else {
  const r = await api(`admissions/leads/${lead.id}`, {
    method: "PATCH",
    data: { assignedTo: counsellor.id },
  });
  if (r.status !== 200 || r.body?.assignedTo !== counsellor.id)
    problems.push(`Admissions assign: ${r.status} ${JSON.stringify(r.body)}`);
  if (lead.assignedTo && lead.assignedTo !== counsellor.id)
    await api(`admissions/leads/${lead.id}`, {
      method: "PATCH",
      data: { assignedTo: lead.assignedTo },
    });
}

// Helpdesk: assign and unassign a demo request.
const staff = (await api("engagement/staff")).body ?? [];
const tickets = (await api("engagement/helpdesk?view=open")).body ?? [];
const ticket = tickets[0];
if (!staff.length || !ticket) problems.push("Helpdesk: no staff or open request to test with");
else {
  const r = await api(`engagement/helpdesk/${ticket.id}`, {
    method: "PATCH",
    data: { assignedTo: staff[0].id },
  });
  if (r.status !== 200 || r.body?.ticket?.assignedTo !== staff[0].id)
    problems.push(`Helpdesk assign: ${r.status} ${JSON.stringify(r.body)}`);
  const back = ticket.assignedTo ? { assignedTo: ticket.assignedTo } : { unassign: true };
  await api(`engagement/helpdesk/${ticket.id}`, { method: "PATCH", data: back });
}

// Compliance: set an owner while editing an item, then clear it again.
const people = (await api("compliance/people")).body ?? [];
const items = (await api("compliance/items")).body ?? [];
const item = items.find((i) => !i.ownerId);
if (!people.length || !item) problems.push("Compliance: no people or unowned item to test with");
else {
  const r = await api(`compliance/items/${item.id}`, {
    method: "PATCH",
    data: { ownerId: people[0].id },
  });
  if (r.status !== 200 || r.body?.ownerId !== people[0].id)
    problems.push(`Compliance owner: ${r.status} ${JSON.stringify(r.body)}`);
}

await browser.close();
console.log(
  problems.length
    ? `PROBLEMS (${problems.length}):\n- ${problems.join("\n- ")}`
    : "All checks passed.",
);
process.exit(problems.length ? 1 : 0);
