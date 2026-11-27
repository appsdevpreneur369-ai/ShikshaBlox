"use client";

import { useCallback, useEffect, useState } from "react";
import { Printer, Wand2, X } from "lucide-react";
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
  useToast,
} from "@/components/kit";

type Period = { label: string; start: string; end: string; break: boolean };
type Settings = {
  days: number[];
  periods: Period[];
  maxSamePerDay: number;
  generator: boolean;
  substitutions: boolean;
  canManage: boolean;
};
type Section = { id: string; label: string; grade: string };
type Staff = { id: string; name: string };
type Slot = {
  id: string;
  sectionId: string;
  section: string;
  day: number;
  period: number;
  subjectId?: string | null;
  subject?: string | null;
  label?: string | null;
  teacherId?: string | null;
  teacher?: string | null;
  room?: string | null;
};
type Load = {
  subjectId: string;
  subject: string;
  periodsPerWeek: number;
  teacherId?: string | null;
  teacher?: string | null;
  scheduled: number;
};
type Grid = { section: Section; settings: Settings; slots: Slot[]; loads: Load[] };
type Overview = { settings: Settings; sections: Section[]; staff: Staff[] };
type Need = {
  sectionId: string;
  section: string;
  period: number;
  periodLabel: string;
  subject?: string | null;
  absentTeacher?: string | null;
  substituteId?: string | null;
  substitute?: string | null;
  suggestions: { id: string; name: string; periodsToday: number }[];
};
type SubDay = {
  date: string;
  weekday: string;
  absences: { userId: string; name: string; note?: string | null }[];
  needs: Need[];
};

const DAY = ["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const todayIst = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());

type Tab = "class" | "teacher" | "loads" | "subs" | "bells" | "mine";

export function TimetablePage() {
  const [ov, setOv] = useState<Overview | null>(null);
  const [tab, setTab] = useState<Tab | null>(null);
  const [blocked, setBlocked] = useState(false);
  const load = useCallback(async () => {
    try {
      const o = await sb<Overview>("timetable/overview");
      setOv(o);
      setTab((t) => t ?? (o.settings.canManage ? "class" : "mine"));
    } catch (e) {
      if (e instanceof StudioError && e.status === 403) setBlocked(true);
    }
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);
  if (blocked)
    return (
      <div>
        <PageHeader title="Timetable" />
        <Card className="max-w-2xl">
          <h2 className="text-lg font-semibold">Timetables are not part of your plan yet</h2>
          <p className="text-ink-muted mt-2 text-sm">
            Upgrade to ShikshaBlox Lite or Standard for class and teacher timetables.
          </p>
        </Card>
      </div>
    );
  if (!ov || !tab) return <Loading />;
  const s = ov.settings;
  const tabs: [Tab, string][] = s.canManage
    ? [
        ["class", "Class timetable"],
        ["teacher", "Teacher timetable"],
        ...(s.generator ? [["loads", "Loads & generator"] as [Tab, string]] : []),
        ...(s.substitutions ? [["subs", "Substitutions"] as [Tab, string]] : []),
        ["bells", "Bell schedule"],
      ]
    : [["mine", "My timetable"]];
  return (
    <div>
      <PageHeader
        title="Timetable"
        subtitle={`${s.days.length} working days · ${s.periods.filter((p) => !p.break).length} periods a day`}
      />
      <div className="mb-5 flex flex-wrap gap-2 print:hidden">
        {tabs.map(([k, label]) => (
          <button
            key={k}
            type="button"
            onClick={() => setTab(k)}
            className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition ${tab === k ? "bg-primary text-on-primary" : "text-ink-muted hover:text-ink bg-white ring-1 ring-black/10"}`}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === "class" ? (
        <ClassView ov={ov} />
      ) : tab === "teacher" ? (
        <TeacherView ov={ov} />
      ) : tab === "loads" ? (
        <LoadsView ov={ov} />
      ) : tab === "subs" ? (
        <SubsView />
      ) : tab === "bells" ? (
        <BellsView settings={s} onSaved={() => void load()} />
      ) : (
        <MineView settings={s} />
      )}
    </div>
  );
}

/** A week grid: rows are periods (breaks shaded), columns are days. */
function WeekGrid({
  settings,
  slots,
  show,
  onCell,
}: {
  settings: Settings;
  slots: Slot[];
  show: "teacher" | "section";
  onCell?: (day: number, period: number) => void;
}) {
  const at = (d: number, p: number) => slots.find((s) => s.day === d && s.period === p);
  return (
    <div className="overflow-x-auto rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
      <table className="w-full min-w-[40rem] border-collapse text-sm">
        <thead>
          <tr className="text-ink-muted border-b border-black/5 text-xs">
            <th scope="col" className="w-28 px-2 py-2 text-left font-medium">
              Period
            </th>
            {settings.days.map((d) => (
              <th key={d} scope="col" className="px-2 py-2 text-left font-medium">
                {DAY[d]}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {settings.periods.map((p, i) =>
            p.break ? (
              <tr key={i} className="bg-black/[0.03] text-xs">
                <th scope="row" className="text-ink-muted px-2 py-1 text-left font-normal">
                  {p.label}
                </th>
                <td colSpan={settings.days.length} className="text-ink-muted px-2 py-1">
                  {p.start}–{p.end}
                </td>
              </tr>
            ) : (
              <tr key={i} className="border-b border-black/5 last:border-0">
                <th scope="row" className="px-2 py-1.5 text-left align-top font-medium">
                  {p.label}
                  <span className="text-ink-muted block text-[11px] font-normal">
                    {p.start}–{p.end}
                  </span>
                </th>
                {settings.days.map((d) => {
                  const s = at(d, i);
                  const content = s ? (
                    <>
                      <span className="block font-medium">{s.subject ?? s.label}</span>
                      <span className="text-ink-muted block text-[11px]">
                        {show === "teacher" ? (s.teacher ?? "") : s.section}
                        {s.room ? ` · ${s.room}` : ""}
                      </span>
                    </>
                  ) : (
                    <span className="text-ink-muted">{onCell ? "+" : ""}</span>
                  );
                  return (
                    <td key={d} className="px-1 py-1 align-top">
                      {onCell ? (
                        <button
                          type="button"
                          onClick={() => onCell(d, i)}
                          className="min-h-12 w-full rounded-lg px-1.5 py-1 text-left hover:bg-indigo-50"
                        >
                          {content}
                        </button>
                      ) : (
                        <div className="min-h-12 px-1.5 py-1">{content}</div>
                      )}
                    </td>
                  );
                })}
              </tr>
            ),
          )}
        </tbody>
      </table>
    </div>
  );
}

function ClassView({ ov }: { ov: Overview }) {
  const [sectionId, setSectionId] = useState(ov.sections[0]?.id ?? "");
  const [grid, setGrid] = useState<Grid | null>(null);
  const [edit, setEdit] = useState<{ day: number; period: number } | null>(null);
  const [form, setForm] = useState({ subjectId: "", label: "", teacherId: "", room: "" });
  const { busy, run } = useAction();
  const load = useCallback(
    async () => sectionId && setGrid(await sb<Grid>(`timetable/sections/${sectionId}`)),
    [sectionId],
  );
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);
  if (!ov.sections.length) return <Empty>Add classes under Students first.</Empty>;
  if (!grid) return <Loading />;
  function open(day: number, period: number) {
    const s = grid!.slots.find((x) => x.day === day && x.period === period);
    setForm({
      subjectId: s?.subjectId ?? "",
      label: s?.subjectId ? "" : (s?.label ?? ""),
      teacherId: s?.teacherId ?? "",
      room: s?.room ?? "",
    });
    setEdit({ day, period });
  }
  async function save() {
    const r = await run("save", () =>
      sb(`timetable/sections/${sectionId}/slots`, {
        method: "PUT",
        body: {
          day: edit!.day,
          period: edit!.period,
          subjectId: form.subjectId || null,
          label: form.label || null,
          teacherId: form.teacherId || null,
          room: form.room || null,
        },
      }),
    );
    if (r) {
      setEdit(null);
      void load();
    }
  }
  async function clear() {
    const r = await run("clear", () =>
      sb(`timetable/sections/${sectionId}/slots?day=${edit!.day}&period=${edit!.period}`, {
        method: "DELETE",
      }).then(() => true),
    );
    if (r) {
      setEdit(null);
      void load();
    }
  }
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2 print:hidden">
        <select
          aria-label="Class"
          className={`${inputCls} w-auto!`}
          value={sectionId}
          onChange={(e) => setSectionId(e.target.value)}
        >
          {ov.sections.map((s) => (
            <option key={s.id} value={s.id}>
              {s.label}
            </option>
          ))}
        </select>
        <Btn size="sm" onClick={() => window.print()}>
          <Printer className="size-4" aria-hidden /> Print
        </Btn>
      </div>
      <h2 className="hidden text-xl font-bold print:block">{grid.section.label}</h2>
      <WeekGrid settings={grid.settings} slots={grid.slots} show="teacher" onCell={open} />
      {edit && (
        <Card className="max-w-xl space-y-3 print:hidden">
          <div className="flex items-center justify-between">
            <h3 className="font-semibold">
              {DAY[edit.day]} · {grid.settings.periods[edit.period]?.label}
            </h3>
            <Btn size="sm" variant="ghost" aria-label="Close" onClick={() => setEdit(null)}>
              <X className="size-4" aria-hidden />
            </Btn>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Subject">
              <select
                className={inputCls}
                value={form.subjectId}
                onChange={(e) => setForm({ ...form, subjectId: e.target.value, label: "" })}
              >
                <option value="">— other —</option>
                {grid.loads.map((l) => (
                  <option key={l.subjectId} value={l.subjectId}>
                    {l.subject}
                  </option>
                ))}
              </select>
            </Field>
            {!form.subjectId && (
              <Field label="Name (e.g. Library, Games)">
                <input
                  className={inputCls}
                  value={form.label}
                  maxLength={60}
                  onChange={(e) => setForm({ ...form, label: e.target.value })}
                />
              </Field>
            )}
            <Field label="Teacher">
              <select
                className={inputCls}
                value={form.teacherId}
                onChange={(e) => setForm({ ...form, teacherId: e.target.value })}
              >
                <option value="">—</option>
                {ov.staff.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Room (optional)">
              <input
                className={inputCls}
                value={form.room}
                maxLength={30}
                onChange={(e) => setForm({ ...form, room: e.target.value })}
              />
            </Field>
          </div>
          <div className="flex gap-2">
            <Btn variant="primary" busy={busy === "save"} onClick={() => void save()}>
              Save
            </Btn>
            <Btn variant="ghost" busy={busy === "clear"} onClick={() => void clear()}>
              Clear period
            </Btn>
          </div>
        </Card>
      )}
    </div>
  );
}

function TeacherView({ ov }: { ov: Overview }) {
  const [teacherId, setTeacherId] = useState(ov.staff[0]?.id ?? "");
  const [slots, setSlots] = useState<Slot[] | null>(null);
  useEffect(() => {
    if (!teacherId) return;
    const t = setTimeout(
      () => void sb<Slot[]>(`timetable/teacher?teacherId=${teacherId}`).then(setSlots),
      0,
    );
    return () => clearTimeout(t);
  }, [teacherId]);
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2 print:hidden">
        <select
          aria-label="Teacher"
          className={`${inputCls} w-auto!`}
          value={teacherId}
          onChange={(e) => setTeacherId(e.target.value)}
        >
          {ov.staff.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
        {slots && <span className="text-ink-muted text-sm">{slots.length} periods a week</span>}
        <Btn size="sm" onClick={() => window.print()}>
          <Printer className="size-4" aria-hidden /> Print
        </Btn>
      </div>
      {!slots ? <Loading /> : <WeekGrid settings={ov.settings} slots={slots} show="section" />}
    </div>
  );
}

function MineView({ settings }: { settings: Settings }) {
  const [slots, setSlots] = useState<Slot[] | null>(null);
  const [subs, setSubs] = useState<Slot[]>([]);
  useEffect(() => {
    const t = setTimeout(async () => {
      setSlots(await sb<Slot[]>("timetable/teacher"));
      setSubs(await sb<Slot[]>("timetable/my-substitutions").catch(() => []));
    }, 0);
    return () => clearTimeout(t);
  }, []);
  if (!slots) return <Loading />;
  return (
    <div className="space-y-4">
      {subs.length > 0 && (
        <Card className="bg-amber-50!">
          <h2 className="font-semibold">Your substitute periods today</h2>
          <ul className="mt-1 text-sm">
            {subs.map((s) => (
              <li key={s.id}>
                {settings.periods[s.period]?.label}: {s.section}
                {s.subject ? ` · ${s.subject}` : ""}
                {s.room ? ` · ${s.room}` : ""}
              </li>
            ))}
          </ul>
        </Card>
      )}
      {slots.length === 0 ? (
        <Empty>You have no periods in the timetable yet.</Empty>
      ) : (
        <WeekGrid settings={settings} slots={slots} show="section" />
      )}
    </div>
  );
}

function LoadsView({ ov }: { ov: Overview }) {
  const toast = useToast();
  const [sectionId, setSectionId] = useState(ov.sections[0]?.id ?? "");
  const [grid, setGrid] = useState<Grid | null>(null);
  const [rows, setRows] = useState<Load[]>([]);
  const [pick, setPick] = useState<string[]>([]);
  const [result, setResult] = useState<{ placed: number; unplaced: string[] } | null>(null);
  const { busy, run } = useAction();
  const load = useCallback(async () => {
    if (!sectionId) return;
    const g = await sb<Grid>(`timetable/sections/${sectionId}`);
    setGrid(g);
    setRows(g.loads);
  }, [sectionId]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);
  if (!grid) return <Loading />;
  const capacity = grid.settings.days.length * grid.settings.periods.filter((p) => !p.break).length;
  const total = rows.reduce((n, r) => n + r.periodsPerWeek, 0);
  return (
    <div className="grid gap-6 lg:grid-cols-[3fr_2fr]">
      <Card className="space-y-3">
        <select
          aria-label="Class"
          className={`${inputCls} w-auto!`}
          value={sectionId}
          onChange={(e) => setSectionId(e.target.value)}
        >
          {ov.sections.map((s) => (
            <option key={s.id} value={s.id}>
              {s.label}
            </option>
          ))}
        </select>
        <p className="text-ink-muted text-sm">
          Periods a week per subject, and who teaches it. {total} of {capacity} periods planned.
        </p>
        <table className="w-full text-sm">
          <thead>
            <tr className="text-ink-muted text-left text-xs">
              <th scope="col" className="py-1 font-medium">
                Subject
              </th>
              <th scope="col" className="py-1 font-medium">
                A week
              </th>
              <th scope="col" className="py-1 font-medium">
                Teacher
              </th>
              <th scope="col" className="py-1 text-right font-medium">
                In grid
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={r.subjectId} className="border-t border-black/5">
                <td className="py-1.5">{r.subject}</td>
                <td className="py-1.5">
                  <input
                    aria-label={`${r.subject} periods a week`}
                    type="number"
                    min={0}
                    max={20}
                    className={`${inputCls} w-20! py-1`}
                    value={r.periodsPerWeek}
                    onChange={(e) =>
                      setRows(
                        rows.map((x, j) =>
                          j === i ? { ...x, periodsPerWeek: Number(e.target.value) } : x,
                        ),
                      )
                    }
                  />
                </td>
                <td className="py-1.5">
                  <select
                    aria-label={`${r.subject} teacher`}
                    className={`${inputCls} w-auto! py-1`}
                    value={r.teacherId ?? ""}
                    onChange={(e) =>
                      setRows(
                        rows.map((x, j) =>
                          j === i ? { ...x, teacherId: e.target.value || null } : x,
                        ),
                      )
                    }
                  >
                    <option value="">—</option>
                    {ov.staff.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                      </option>
                    ))}
                  </select>
                </td>
                <td className="py-1.5 text-right tabular-nums">{r.scheduled}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <Btn
          variant="primary"
          busy={busy === "save"}
          onClick={async () => {
            const r = await run(
              "save",
              () =>
                sb<Grid>(`timetable/sections/${sectionId}/loads`, {
                  method: "PUT",
                  body: rows.map((x) => ({
                    subjectId: x.subjectId,
                    periodsPerWeek: x.periodsPerWeek,
                    teacherId: x.teacherId ?? null,
                  })),
                }),
              "Loads saved.",
            );
            if (r) {
              setGrid(r);
              setRows(r.loads);
            }
          }}
        >
          Save loads
        </Btn>
      </Card>
      <Card className="space-y-3 self-start">
        <h2 className="text-lg font-semibold">Generate</h2>
        <p className="text-ink-muted text-sm">
          Fills the empty periods of the chosen classes from their loads. Periods already in the
          grid stay; no teacher is put in two places, and a subject appears at most{" "}
          {ov.settings.maxSamePerDay} times a day.
        </p>
        <div className="flex flex-wrap gap-2">
          {ov.sections.map((s) => (
            <label
              key={s.id}
              className="inline-flex items-center gap-1.5 rounded-full bg-black/[0.04] px-3 py-1 text-sm"
            >
              <input
                type="checkbox"
                checked={pick.includes(s.id)}
                onChange={(e) =>
                  setPick(e.target.checked ? [...pick, s.id] : pick.filter((x) => x !== s.id))
                }
              />
              {s.label}
            </label>
          ))}
        </div>
        <Btn
          variant="primary"
          disabled={!pick.length}
          busy={busy === "gen"}
          onClick={async () => {
            const r = await run("gen", () =>
              sb<{ placed: number; unplaced: string[] }>("timetable/generate", {
                body: { sectionIds: pick },
              }),
            );
            if (r) {
              setResult(r);
              toast(`${r.placed} periods placed.`);
              void load();
            }
          }}
        >
          <Wand2 className="size-4" aria-hidden /> Fill empty periods
        </Btn>
        {result && (
          <div className="text-sm">
            <p>{result.placed} periods placed.</p>
            {result.unplaced.length > 0 && (
              <ul className="mt-1 list-disc pl-5 text-amber-900">
                {result.unplaced.map((u) => (
                  <li key={u}>{u}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </Card>
    </div>
  );
}

function SubsView() {
  const [date, setDate] = useState(todayIst());
  const [day, setDay] = useState<SubDay | null>(null);
  const [staff, setStaff] = useState<Staff[]>([]);
  const [who, setWho] = useState("");
  const [note, setNote] = useState("");
  const { busy, run } = useAction();
  const load = useCallback(
    async () => setDay(await sb<SubDay>(`timetable/substitutions?date=${date}`)),
    [date],
  );
  useEffect(() => {
    const t = setTimeout(async () => {
      void load();
      setStaff((await sb<Overview>("timetable/overview")).staff);
    }, 0);
    return () => clearTimeout(t);
  }, [load]);
  if (!day) return <Loading />;
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3 print:hidden">
        <Field label="Date">
          <input
            type="date"
            className={`${inputCls} w-auto!`}
            value={date}
            onChange={(e) => e.target.value && setDate(e.target.value)}
          />
        </Field>
        <Field label="Teacher away">
          <select
            className={`${inputCls} w-auto!`}
            value={who}
            onChange={(e) => setWho(e.target.value)}
          >
            <option value="">Choose…</option>
            {staff
              .filter((s) => !day.absences.some((a) => a.userId === s.id))
              .map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
          </select>
        </Field>
        <Field label="Note">
          <input
            className={`${inputCls} w-48!`}
            value={note}
            maxLength={120}
            onChange={(e) => setNote(e.target.value)}
          />
        </Field>
        <Btn
          variant="primary"
          disabled={!who}
          busy={busy === "away"}
          onClick={async () => {
            const r = await run("away", () =>
              sb<SubDay>("timetable/substitutions/away", {
                body: { date, userId: who, note, away: true },
              }),
            );
            if (r) {
              setDay(r);
              setWho("");
              setNote("");
            }
          }}
        >
          Mark away
        </Btn>
        <Btn size="sm" onClick={() => window.print()}>
          <Printer className="size-4" aria-hidden /> Print
        </Btn>
      </div>
      <h2 className="text-lg font-semibold">
        Substitutions · {day.weekday}, {date}
      </h2>
      {day.absences.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {day.absences.map((a) => (
            <span
              key={a.userId}
              className="inline-flex items-center gap-1 rounded-full bg-amber-100 py-1 pr-1 pl-3 text-sm text-amber-900"
            >
              {a.name} away{a.note ? ` (${a.note})` : ""}
              <button
                type="button"
                aria-label={`${a.name} is back`}
                className="grid size-6 place-items-center rounded-full hover:bg-amber-200 print:hidden"
                onClick={async () => {
                  const r = await run(a.userId, () =>
                    sb<SubDay>("timetable/substitutions/away", {
                      body: { date, userId: a.userId, away: false },
                    }),
                  );
                  if (r) setDay(r);
                }}
              >
                ×
              </button>
            </span>
          ))}
        </div>
      )}
      {day.needs.length === 0 ? (
        <Empty>
          {day.absences.length
            ? "The teachers away have no periods on this day."
            : "No teacher is marked away on this day."}
        </Empty>
      ) : (
        <ul className="divide-y divide-black/5 rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
          {day.needs.map((n) => (
            <li key={n.sectionId + n.period} className="flex flex-wrap items-center gap-3 p-3">
              <span className="min-w-[12rem] flex-1">
                <span className="block font-medium">
                  {n.periodLabel} · {n.section}
                  {n.subject ? ` · ${n.subject}` : ""}
                </span>
                <span className="text-ink-muted text-xs">Usually {n.absentTeacher}</span>
              </span>
              {n.substitute ? (
                <Badge tone="green">{n.substitute}</Badge>
              ) : (
                <Badge tone="amber">Needs cover</Badge>
              )}
              <select
                aria-label={`Substitute for ${n.section} ${n.periodLabel}`}
                className={`${inputCls} w-auto! print:hidden`}
                value={n.substituteId ?? ""}
                onChange={async (e) => {
                  const r = await run(
                    n.sectionId + n.period,
                    () =>
                      sb<SubDay>("timetable/substitutions/assign", {
                        body: {
                          date,
                          sectionId: n.sectionId,
                          period: n.period,
                          substituteId: e.target.value || null,
                        },
                      }),
                    "Saved.",
                  );
                  if (r) setDay(r);
                }}
              >
                <option value="">— choose a free teacher —</option>
                {n.substituteId && <option value={n.substituteId}>{n.substitute}</option>}
                {n.suggestions
                  .filter((s) => s.id !== n.substituteId)
                  .map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name} ({s.periodsToday} periods today)
                    </option>
                  ))}
              </select>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function BellsView({ settings, onSaved }: { settings: Settings; onSaved: () => void }) {
  const [days, setDays] = useState(settings.days);
  const [periods, setPeriods] = useState(settings.periods);
  const [max, setMax] = useState(settings.maxSamePerDay);
  const { busy, run } = useAction();
  const set = (i: number, p: Partial<Period>) =>
    setPeriods(periods.map((x, j) => (j === i ? { ...x, ...p } : x)));
  return (
    <Card className="max-w-3xl space-y-4">
      <fieldset>
        <legend className="mb-1.5 text-sm font-medium">Working days</legend>
        <div className="flex flex-wrap gap-2">
          {[1, 2, 3, 4, 5, 6, 7].map((d) => (
            <label
              key={d}
              className="inline-flex items-center gap-1.5 rounded-full bg-black/[0.04] px-3 py-1 text-sm"
            >
              <input
                type="checkbox"
                checked={days.includes(d)}
                onChange={(e) =>
                  setDays(e.target.checked ? [...days, d].sort() : days.filter((x) => x !== d))
                }
              />
              {DAY[d]}
            </label>
          ))}
        </div>
      </fieldset>
      <fieldset className="space-y-2">
        <legend className="mb-1 text-sm font-medium">Bell schedule</legend>
        {periods.map((p, i) => (
          <div key={i} className="flex flex-wrap items-center gap-2">
            <input
              aria-label="Name"
              className={`${inputCls} w-40!`}
              value={p.label}
              maxLength={30}
              onChange={(e) => set(i, { label: e.target.value })}
            />
            <input
              aria-label="Starts"
              type="time"
              className={`${inputCls} w-auto!`}
              value={p.start}
              onChange={(e) => set(i, { start: e.target.value })}
            />
            <input
              aria-label="Ends"
              type="time"
              className={`${inputCls} w-auto!`}
              value={p.end}
              onChange={(e) => set(i, { end: e.target.value })}
            />
            <label className="inline-flex items-center gap-1 text-sm">
              <input
                type="checkbox"
                checked={p.break}
                onChange={(e) => set(i, { break: e.target.checked })}
              />{" "}
              Break
            </label>
            <Btn
              size="sm"
              variant="ghost"
              onClick={() => setPeriods(periods.filter((_, j) => j !== i))}
            >
              Remove
            </Btn>
          </div>
        ))}
        {periods.length < 16 && (
          <Btn
            size="sm"
            onClick={() =>
              setPeriods([
                ...periods,
                {
                  label: `Period ${periods.filter((x) => !x.break).length + 1}`,
                  start: periods.at(-1)?.end ?? "08:30",
                  end: periods.at(-1)?.end ?? "09:10",
                  break: false,
                },
              ])
            }
          >
            Add a row
          </Btn>
        )}
      </fieldset>
      <Field label="Most times a subject may appear in a day (generator)">
        <input
          type="number"
          min={1}
          max={4}
          className={`${inputCls} w-20!`}
          value={max}
          onChange={(e) => setMax(Number(e.target.value))}
        />
      </Field>
      <Btn
        variant="primary"
        busy={busy === "save"}
        onClick={async () => {
          const r = await run(
            "save",
            () =>
              sb("timetable/settings", {
                method: "PUT",
                body: { days, periods, maxSamePerDay: max },
              }),
            "Saved.",
          );
          if (r) onSaved();
        }}
      >
        Save
      </Btn>
    </Card>
  );
}
