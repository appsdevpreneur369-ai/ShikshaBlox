"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Download, Inbox, Plus, Search } from "lucide-react";
import { sb, StudioError } from "@/lib/studio";
import {
  Badge,
  Btn,
  Card,
  Empty,
  Field,
  Loading,
  PageHeader,
  inputCls,
  useAction,
} from "@/components/kit";
import { LeadPanel } from "./lead-panel";
import { ApplicationsView, FormSettingsView } from "./applications";
import {
  OPEN_STAGES,
  SOURCE_LABEL,
  STAGES,
  STAGE_LABEL,
  STAGE_TONE,
  dueState,
  fmtDay,
  fromDateInput,
  type Counsellor,
  type Funnel,
  type Lead,
  type Seat,
} from "./types";

type Meta = { stages: string[]; sources: string[]; academicYear: string };
type View = "pipeline" | "followups" | "applications" | "funnel" | "seats" | "form";
const VIEWS: View[] = ["pipeline", "followups", "applications", "funnel", "seats", "form"];

function yearsAround(y: string) {
  const start = Number(y.slice(0, 4));
  return [start - 1, start, start + 1].map((s) => `${s}-${String((s + 1) % 100).padStart(2, "0")}`);
}

export function AdmissionsPage({
  siteUrl,
  isAdmin = false,
}: {
  siteUrl?: string;
  isAdmin?: boolean;
}) {
  const [meta, setMeta] = useState<Meta | null>(null);
  const [blocked, setBlocked] = useState<string | null>(null);
  const [year, setYear] = useState("");
  const [view, setView] = useState<View>("pipeline");
  const [q, setQ] = useState("");
  const [leads, setLeads] = useState<Lead[] | null>(null);
  const [counsellors, setCounsellors] = useState<Counsellor[]>([]);
  const [sel, setSel] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [appSel, setAppSel] = useState<string | undefined>(undefined);
  const { busy, run } = useAction();

  useEffect(() => {
    sb<Meta>("admissions/meta")
      .then((m) => {
        const params = new URLSearchParams(window.location.search);
        const v = params.get("view") as View | null;
        if (params.get("due")) setView("followups");
        else if (v && VIEWS.includes(v)) setView(v);
        setMeta(m);
        setYear(m.academicYear);
      })
      .catch(() => {});
    sb<Counsellor[]>("admissions/counsellors")
      .then(setCounsellors)
      .catch(() => {});
  }, []);

  const load = useCallback(async () => {
    if (!year) return;
    const p = new URLSearchParams({ year });
    if (q) p.set("q", q);
    try {
      setLeads(await sb<Lead[]>(`admissions/leads?${p}`));
    } catch (e) {
      if (e instanceof StudioError && e.status === 403) setBlocked(e.message);
    }
  }, [year, q]);

  useEffect(() => {
    const t = setTimeout(() => void load(), 250);
    return () => clearTimeout(t);
  }, [load]);

  async function importEnquiries() {
    const r = await run("import", () =>
      sb<{ imported: number }>("admissions/import-enquiries", { method: "POST" }),
    );
    if (r) {
      void load();
      alertToast(r.imported);
    }
  }
  const [importMsg, setImportMsg] = useState<string | null>(null);
  function alertToast(n: number) {
    setImportMsg(
      n === 0
        ? "All website enquiries already have leads."
        : `${n} website ${n === 1 ? "enquiry" : "enquiries"} added as leads.`,
    );
    setTimeout(() => setImportMsg(null), 5000);
  }

  async function exportCsv() {
    const res = await fetch(`/api/backend/admissions/leads.csv?year=${encodeURIComponent(year)}`, {
      cache: "no-store",
    });
    if (!res.ok) return;
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = `admission-leads-${year}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  if (blocked) {
    return (
      <div>
        <PageHeader title="Admissions" />
        <Card className="max-w-2xl">
          <h2 className="text-lg font-semibold">The Admissions CRM is not part of your plan yet</h2>
          <p className="text-ink-muted mt-2 text-sm">
            Your website enquiries are still in{" "}
            <a className="text-primary font-medium underline" href="/enquiries">
              Enquiries
            </a>
            . Upgrade to ShikshaBlox Lite or Standard to track every lead through visits,
            applications and admission, with follow-up reminders for your team.
          </p>
        </Card>
      </div>
    );
  }

  const due = (leads ?? []).filter(
    (l) =>
      !["ADMITTED", "LOST"].includes(l.stage) &&
      ["overdue", "today"].includes(dueState(l.nextFollowUpAt) ?? ""),
  );

  return (
    <div>
      <PageHeader
        title="Admissions"
        subtitle="Every enquiry, walk-in and call, from first contact to admission."
        actions={
          <>
            <select
              className={`${inputCls} w-auto!`}
              value={year}
              onChange={(e) => setYear(e.target.value)}
              aria-label="Academic year"
            >
              {(meta ? yearsAround(meta.academicYear) : [year]).map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
            <Btn onClick={() => void importEnquiries()} busy={busy === "import"}>
              <Inbox className="size-4" aria-hidden /> Import website enquiries
            </Btn>
            <Btn onClick={() => void exportCsv()}>
              <Download className="size-4" aria-hidden /> CSV
            </Btn>
            <Btn variant="primary" onClick={() => setAdding(true)}>
              <Plus className="size-4" aria-hidden /> Add lead
            </Btn>
          </>
        }
      />
      {importMsg && (
        <p className="mb-4 rounded-xl bg-emerald-50 px-4 py-2 text-sm text-emerald-800">
          {importMsg}
        </p>
      )}

      <div className="mb-4 flex flex-wrap items-center gap-2">
        {(
          [
            ["pipeline", "Pipeline"],
            ["followups", `Follow-ups${due.length ? ` (${due.length})` : ""}`],
            ["applications", "Applications"],
            ["funnel", "Funnel"],
            ["seats", "Seats"],
            ["form", "Application form"],
          ] as [View, string][]
        ).map(([v, label]) => (
          <button
            key={v}
            type="button"
            onClick={() => {
              setView(v);
              setAppSel(undefined);
            }}
            className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition ${view === v ? "bg-primary text-on-primary" : "text-ink-muted hover:text-ink bg-white ring-1 ring-black/10"}`}
          >
            {label}
          </button>
        ))}
        {(view === "pipeline" || view === "followups") && (
          <div className="relative ml-auto w-full sm:w-64">
            <Search className="text-ink-muted absolute top-2.5 left-3 size-4" aria-hidden />
            <input
              className={`${inputCls} pl-9`}
              placeholder="Search name or phone"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              aria-label="Search leads"
            />
          </div>
        )}
      </div>

      {adding && (
        <AddLead
          year={year}
          counsellors={counsellors}
          onClose={() => setAdding(false)}
          onAdded={(l) => {
            setAdding(false);
            void load();
            setSel(l.id);
          }}
        />
      )}

      {view === "applications" ? (
        <ApplicationsView
          key={appSel ?? "all"}
          year={year}
          initial={appSel}
          onOpenLead={(id) => {
            setView("pipeline");
            setSel(id);
          }}
        />
      ) : view === "form" ? (
        <FormSettingsView siteUrl={siteUrl} />
      ) : (
        <div className={`grid gap-5 ${sel ? "lg:grid-cols-[1fr_400px]" : ""}`}>
          <div className="min-w-0">
            {view === "funnel" ? (
              <FunnelView year={year} />
            ) : view === "seats" ? (
              <SeatsView year={year} />
            ) : !leads ? (
              <Loading />
            ) : view === "followups" ? (
              <LeadList
                leads={due}
                empty="No follow-ups due today. Nice work!"
                onOpen={setSel}
                selected={sel}
              />
            ) : leads.length === 0 ? (
              <Empty>
                No leads for {year} yet. Add a walk-in or phone enquiry, or import the enquiries
                from your website.
              </Empty>
            ) : (
              <Pipeline leads={leads} onOpen={setSel} selected={sel} />
            )}
          </div>
          {sel && (
            <LeadPanel
              key={sel}
              id={sel}
              counsellors={counsellors}
              onClose={() => setSel(null)}
              onChanged={() => void load()}
              onDeleted={
                isAdmin
                  ? () => {
                      setSel(null);
                      void load();
                    }
                  : undefined
              }
              onOpenApplication={(id) => {
                setAppSel(id);
                setView("applications");
              }}
            />
          )}
        </div>
      )}
    </div>
  );
}

function LeadCard({
  l,
  onOpen,
  selected,
}: {
  l: Lead;
  onOpen: (id: string) => void;
  selected: boolean;
}) {
  const due = dueState(l.nextFollowUpAt);
  return (
    <button
      type="button"
      onClick={() => onOpen(l.id)}
      className={`block w-full rounded-xl bg-white p-3 text-left shadow-xs ring-1 transition hover:ring-black/20 ${selected ? "ring-primary ring-2" : "ring-black/5"}`}
    >
      <span className="block truncate text-sm font-semibold">
        {l.studentName || l.guardianName}
      </span>
      <span className="text-ink-muted block truncate text-xs">
        {[l.gradeSought, SOURCE_LABEL[l.source]].filter(Boolean).join(" · ")}
      </span>
      <span className="mt-2 flex flex-wrap items-center gap-1.5">
        {l.priority === "HIGH" && <Badge tone="red">High</Badge>}
        {due === "overdue" && <Badge tone="red">Overdue {fmtDay(l.nextFollowUpAt)}</Badge>}
        {due === "today" && <Badge tone="amber">Today</Badge>}
        {due === "later" && (
          <span className="text-ink-muted text-xs">Next: {fmtDay(l.nextFollowUpAt)}</span>
        )}
        {l.assignedName && (
          <span className="text-ink-muted ml-auto text-xs">{l.assignedName.split(" ")[0]}</span>
        )}
      </span>
    </button>
  );
}

function Pipeline({
  leads,
  onOpen,
  selected,
}: {
  leads: Lead[];
  onOpen: (id: string) => void;
  selected: string | null;
}) {
  const by = useMemo(() => {
    const m: Record<string, Lead[]> = {};
    STAGES.forEach((s) => (m[s] = []));
    leads.forEach((l) => m[l.stage]?.push(l));
    return m;
  }, [leads]);
  return (
    <div
      className="-mx-4 overflow-x-auto px-4 pb-2"
      role="region"
      aria-label="Admissions pipeline"
      tabIndex={0}
    >
      <div className="flex gap-3">
        {[...OPEN_STAGES, "ADMITTED", "LOST"].map((s) => (
          <section
            key={s}
            className="w-64 shrink-0 rounded-2xl bg-black/[0.03] p-2.5"
            aria-label={STAGE_LABEL[s]}
          >
            <h2 className="mb-2 flex items-center justify-between px-1 text-sm font-semibold">
              <span>{STAGE_LABEL[s]}</span>
              <Badge tone={STAGE_TONE[s]}>{by[s]?.length ?? 0}</Badge>
            </h2>
            <div className="space-y-2">
              {(by[s] ?? []).slice(0, 50).map((l) => (
                <LeadCard key={l.id} l={l} onOpen={onOpen} selected={selected === l.id} />
              ))}
              {(by[s]?.length ?? 0) > 50 && (
                <p className="text-ink-muted px-1 text-xs">
                  +{(by[s]?.length ?? 0) - 50} more (use search)
                </p>
              )}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}

function LeadList({
  leads,
  empty,
  onOpen,
  selected,
}: {
  leads: Lead[];
  empty: string;
  onOpen: (id: string) => void;
  selected: string | null;
}) {
  if (leads.length === 0) return <Empty>{empty}</Empty>;
  return (
    <div className="grid gap-2 sm:grid-cols-2">
      {leads.map((l) => (
        <div key={l.id}>
          <LeadCard l={l} onOpen={onOpen} selected={selected === l.id} />
        </div>
      ))}
    </div>
  );
}

function AddLead({
  year,
  counsellors,
  onClose,
  onAdded,
}: {
  year: string;
  counsellors: Counsellor[];
  onClose: () => void;
  onAdded: (l: Lead) => void;
}) {
  const [f, setF] = useState({
    studentName: "",
    guardianName: "",
    phone: "",
    email: "",
    gradeSought: "",
    source: "WALK_IN",
    assignedTo: "",
    followUp: "",
    notes: "",
  });
  const { busy, run } = useAction();
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF({ ...f, [k]: e.target.value });

  async function save(e: React.FormEvent) {
    e.preventDefault();
    const r = await run(
      "add",
      () =>
        sb<Lead>("admissions/leads", {
          body: {
            studentName: f.studentName || null,
            guardianName: f.guardianName,
            phone: f.phone,
            email: f.email || null,
            gradeSought: f.gradeSought || null,
            academicYear: year,
            source: f.source,
            assignedTo: f.assignedTo || null,
            nextFollowUpAt: fromDateInput(f.followUp) ?? null,
            notes: f.notes || null,
          },
        }),
      "Lead added",
    );
    if (r) onAdded(r);
  }

  return (
    <Card className="mb-5">
      <form onSubmit={(e) => void save(e)}>
        <h2 className="text-lg font-semibold">Add a lead · {year}</h2>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Parent / guardian name *">
            <input
              className={inputCls}
              required
              maxLength={120}
              value={f.guardianName}
              onChange={set("guardianName")}
            />
          </Field>
          <Field label="Phone *">
            <input
              className={inputCls}
              required
              type="tel"
              maxLength={20}
              value={f.phone}
              onChange={set("phone")}
            />
          </Field>
          <Field label="Student name">
            <input
              className={inputCls}
              maxLength={120}
              value={f.studentName}
              onChange={set("studentName")}
            />
          </Field>
          <Field label="Class sought">
            <input
              className={inputCls}
              maxLength={60}
              placeholder="e.g. Class 3"
              value={f.gradeSought}
              onChange={set("gradeSought")}
            />
          </Field>
          <Field label="Email">
            <input
              className={inputCls}
              type="email"
              maxLength={200}
              value={f.email}
              onChange={set("email")}
            />
          </Field>
          <Field label="Source">
            <select className={inputCls} value={f.source} onChange={set("source")}>
              {["WALK_IN", "PHONE", "WHATSAPP", "REFERRAL", "CAMPAIGN", "OTHER"].map((s) => (
                <option key={s} value={s}>
                  {SOURCE_LABEL[s]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Counsellor">
            <select className={inputCls} value={f.assignedTo} onChange={set("assignedTo")}>
              <option value="">Unassigned</option>
              {counsellors.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="First follow-up">
            <input className={inputCls} type="date" value={f.followUp} onChange={set("followUp")} />
          </Field>
          <Field label="Notes">
            <input className={inputCls} maxLength={4000} value={f.notes} onChange={set("notes")} />
          </Field>
        </div>
        <div className="mt-4 flex gap-2">
          <Btn type="submit" variant="primary" busy={busy === "add"}>
            Add lead
          </Btn>
          <Btn variant="ghost" onClick={onClose}>
            Cancel
          </Btn>
        </div>
      </form>
    </Card>
  );
}

function FunnelView({ year }: { year: string }) {
  const [f, setF] = useState<Funnel | null>(null);
  useEffect(() => {
    if (year) void sb<Funnel>(`admissions/funnel?year=${year}`).then(setF);
  }, [year]);
  if (!f) return <Loading />;
  const max = Math.max(1, ...Object.values(f.byStage));
  return (
    <div className="grid gap-5 lg:grid-cols-[1.4fr_1fr]">
      <Card>
        <div className="grid grid-cols-3 gap-3 text-center">
          {[
            ["Leads", f.total],
            ["Admitted", f.admitted],
            ["Conversion", f.conversionPercent == null ? "—" : `${f.conversionPercent}%`],
          ].map(([k, v]) => (
            <div key={k} className="rounded-xl bg-black/[0.03] p-3">
              <p className="text-2xl font-bold">{v}</p>
              <p className="text-ink-muted text-xs">{k}</p>
            </div>
          ))}
        </div>
        <h2 className="mt-5 mb-2 text-sm font-semibold">By stage</h2>
        <ul className="space-y-2">
          {STAGES.map((s) => (
            <li key={s} className="grid grid-cols-[7rem_1fr_2.5rem] items-center gap-2 text-sm">
              <span>{STAGE_LABEL[s]}</span>
              <span className="h-2.5 rounded-full bg-black/5">
                <span
                  className={`block h-full rounded-full ${s === "LOST" ? "bg-red-400" : s === "ADMITTED" ? "bg-emerald-500" : "bg-primary"}`}
                  style={{ width: `${((f.byStage[s] ?? 0) / max) * 100}%` }}
                />
              </span>
              <span className="text-right tabular-nums">{f.byStage[s] ?? 0}</span>
            </li>
          ))}
        </ul>
      </Card>
      <Card>
        <h2 className="mb-2 text-sm font-semibold">By source</h2>
        {Object.keys(f.bySource).length === 0 ? (
          <p className="text-ink-muted text-sm">No leads yet.</p>
        ) : (
          <ul className="divide-y divide-black/5 text-sm">
            {Object.entries(f.bySource).map(([s, n]) => (
              <li key={s} className="flex justify-between py-2">
                <span>{SOURCE_LABEL[s] ?? s}</span>
                <span className="tabular-nums">{n}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

function SeatsView({ year }: { year: string }) {
  const [rows, setRows] = useState<Seat[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const { busy, run } = useAction();
  useEffect(() => {
    if (!year) return;
    sb<Seat[]>(`admissions/seats?year=${year}`)
      .then(setRows)
      .catch((e: Error) => setErr(e.message));
  }, [year]);
  if (err) return <Empty>Seat planning is part of ShikshaBlox Standard. {err}</Empty>;
  if (!rows) return <Loading />;
  const update = (i: number, p: Partial<Seat>) =>
    setRows(rows.map((r, j) => (i === j ? { ...r, ...p } : r)));
  async function save() {
    const r = await run(
      "seats",
      () =>
        sb<Seat[]>("admissions/seats", {
          method: "PUT",
          body: {
            academicYear: year,
            seats: rows!.filter((x) => x.grade.trim()).map((x, i) => ({ ...x, sortOrder: i })),
          },
        }),
      "Seats saved",
    );
    if (r) setRows(r);
  }
  return (
    <Card>
      <p className="text-ink-muted mb-3 text-sm">
        Set the seats available per class. Moving a lead to Admitted is blocked once a class is
        full.
      </p>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-ink-muted text-left text-xs">
              <th className="py-2 pr-2 font-medium">Class</th>
              <th className="py-2 pr-2 font-medium">Seats</th>
              <th className="py-2 pr-2 font-medium">Admitted</th>
              <th className="py-2 pr-2 font-medium">Offered</th>
              <th className="py-2 font-medium">Left</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={i} className="border-t border-black/5">
                <td className="py-1.5 pr-2">
                  <input
                    className={inputCls}
                    value={r.grade}
                    maxLength={60}
                    onChange={(e) => update(i, { grade: e.target.value })}
                    aria-label="Class"
                  />
                </td>
                <td className="w-28 py-1.5 pr-2">
                  <input
                    className={inputCls}
                    type="number"
                    min={0}
                    max={5000}
                    value={r.capacity}
                    onChange={(e) => update(i, { capacity: Number(e.target.value) })}
                    aria-label={`Seats in ${r.grade || "class"}`}
                  />
                </td>
                <td className="py-1.5 pr-2 tabular-nums">{r.admitted}</td>
                <td className="py-1.5 pr-2 tabular-nums">{r.offered}</td>
                <td
                  className={`py-1.5 tabular-nums ${r.capacity - r.admitted <= 0 ? "font-semibold text-red-600" : ""}`}
                >
                  {Math.max(0, r.capacity - r.admitted)}
                </td>
                <td className="py-1.5 text-right">
                  <Btn
                    size="sm"
                    variant="ghost"
                    onClick={() => setRows(rows.filter((_, j) => j !== i))}
                    aria-label={`Remove ${r.grade || "row"}`}
                  >
                    Remove
                  </Btn>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="mt-3 flex gap-2">
        <Btn
          onClick={() =>
            setRows([
              ...rows,
              { grade: "", capacity: 40, sortOrder: rows.length, admitted: 0, offered: 0 },
            ])
          }
        >
          <Plus className="size-4" aria-hidden /> Add class
        </Btn>
        <Btn variant="primary" busy={busy === "seats"} onClick={() => void save()}>
          Save seats
        </Btn>
      </div>
    </Card>
  );
}
