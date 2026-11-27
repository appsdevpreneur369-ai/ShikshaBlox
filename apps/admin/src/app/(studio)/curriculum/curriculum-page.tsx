"use client";

import { useCallback, useEffect, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
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

type SectionRef = { id: string; label: string; grade: string };
type SubjectRef = { id: string; grade: string; name: string };
type Overview = {
  sections: SectionRef[];
  subjects: SubjectRef[];
  staff: { id: string; name: string }[];
  criteria: string[];
  canManage: boolean;
  lessonPlans: boolean;
  observations: boolean;
};
type Topic = { id: string; title: string; coveredOn?: string | null; coveredBy?: string | null };
type Unit = {
  id: string;
  title: string;
  plannedMonth?: number | null;
  behind: boolean;
  topics: Topic[];
};
type Coverage = {
  subjectId: string;
  subject: string;
  sectionId?: string | null;
  section?: string | null;
  topics: number;
  covered: number;
  percent?: number | null;
  behindUnits: number;
  units: Unit[];
};
type Cell = {
  sectionId: string;
  section: string;
  subjectId: string;
  subject: string;
  topics: number;
  covered: number;
  percent?: number | null;
  behindUnits: number;
};
type Plan = {
  id: string;
  sectionId: string;
  section: string;
  subjectId?: string | null;
  subject?: string | null;
  topicId?: string | null;
  topic?: string | null;
  plannedFor: string;
  title: string;
  objectives?: string | null;
  activities?: string | null;
  resources?: string | null;
  assessment?: string | null;
  status: "DRAFT" | "SUBMITTED" | "REVIEWED";
  reviewNote?: string | null;
  reviewedBy?: string | null;
  createdBy?: string | null;
};
type Observation = {
  id: string;
  teacherId: string;
  teacher: string;
  section?: string | null;
  subject?: string | null;
  observedOn: string;
  scores: Record<string, number>;
  average?: number | null;
  strengths?: string | null;
  suggestions?: string | null;
  observer?: string | null;
  acknowledgedAt?: string | null;
  reflection?: string | null;
};

const MONTHS = [
  "",
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];
const SCHOOL_MONTHS = [4, 5, 6, 7, 8, 9, 10, 11, 12, 1, 2, 3];
const todayIst = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
const day = (d: string) =>
  new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" }).format(
    new Date(`${d}T00:00:00`),
  );

type Tab = "syllabus" | "overview" | "plans" | "obs";

export function CurriculumPage() {
  const [ov, setOv] = useState<Overview | null>(null);
  const [tab, setTab] = useState<Tab>("syllabus");
  const [blocked, setBlocked] = useState(false);
  useEffect(() => {
    const t = setTimeout(async () => {
      try {
        setOv(await sb<Overview>("curriculum/overview"));
      } catch (e) {
        if (e instanceof StudioError && e.status === 403) setBlocked(true);
      }
    }, 0);
    return () => clearTimeout(t);
  }, []);
  if (blocked)
    return (
      <div>
        <PageHeader title="Curriculum" />
        <Card className="max-w-2xl">
          <h2 className="text-lg font-semibold">
            Curriculum planning is not part of your plan yet
          </h2>
          <p className="text-ink-muted mt-2 text-sm">
            Upgrade to ShikshaBlox Lite or Standard for the syllabus and coverage meter.
          </p>
        </Card>
      </div>
    );
  if (!ov) return <Loading />;
  const tabs: [Tab, string][] = [["syllabus", ov.canManage ? "Syllabus & coverage" : "Coverage"]];
  if (ov.canManage) tabs.push(["overview", "Completion"]);
  if (ov.lessonPlans) tabs.push(["plans", "Lesson plans"]);
  if (ov.observations) tabs.push(["obs", "Observations"]);
  return (
    <div>
      <PageHeader
        title="Curriculum"
        subtitle="Syllabus, coverage, lesson plans and classroom observations"
      />
      <div className="mb-5 flex flex-wrap gap-2">
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
      {ov.sections.length === 0 ? (
        <Empty>You don&apos;t teach any class yet.</Empty>
      ) : tab === "syllabus" ? (
        <SyllabusView ov={ov} />
      ) : tab === "overview" ? (
        <CompletionView />
      ) : tab === "plans" ? (
        <PlansView ov={ov} />
      ) : (
        <ObservationsView ov={ov} />
      )}
    </div>
  );
}

function Meter({ percent }: { percent?: number | null }) {
  const p = percent ?? 0;
  return (
    <span className="inline-flex items-center gap-2">
      <span className="h-2 w-24 overflow-hidden rounded-full bg-black/10" aria-hidden>
        <span className="bg-primary block h-full" style={{ width: `${p}%` }} />
      </span>
      <span className="text-sm tabular-nums">{percent == null ? "–" : `${p}%`}</span>
    </span>
  );
}

function SyllabusView({ ov }: { ov: Overview }) {
  const first =
    ov.sections.find((s) => ov.subjects.some((x) => x.grade === s.grade)) ?? ov.sections[0]!;
  const [sectionId, setSectionId] = useState(first.id);
  const grade = ov.sections.find((s) => s.id === sectionId)?.grade;
  const subjects = ov.subjects.filter((s) => s.grade === grade);
  const [subjectId, setSubjectId] = useState(subjects[0]?.id ?? "");
  const [cov, setCov] = useState<Coverage | null>(null);
  const [unit, setUnit] = useState({ title: "", month: "" });
  const [topicText, setTopicText] = useState<Record<string, string>>({});
  const { busy, run } = useAction();
  const sid = subjects.some((s) => s.id === subjectId) ? subjectId : (subjects[0]?.id ?? "");
  const [version, setVersion] = useState(0);
  const load = () => setVersion((v) => v + 1);
  useEffect(() => {
    const t = setTimeout(async () => {
      setCov(
        sid
          ? await sb<Coverage>(`curriculum/coverage?subjectId=${sid}&sectionId=${sectionId}`)
          : null,
      );
    }, 0);
    return () => clearTimeout(t);
  }, [sid, sectionId, version]);
  const reload = async (p: Promise<unknown>) => {
    const r = await run("x", () => p);
    if (r !== undefined) void load();
  };
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
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
        <select
          aria-label="Subject"
          className={`${inputCls} w-auto!`}
          value={sid}
          onChange={(e) => setSubjectId(e.target.value)}
        >
          {subjects.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </div>
      {!sid ? (
        <Empty>No subjects for this class yet (set them under Exams → Subjects).</Empty>
      ) : !cov ? (
        <Loading />
      ) : (
        <>
          <Card className="flex flex-wrap items-center gap-4">
            <span className="font-semibold">
              {cov.subject} · {cov.section}
            </span>
            <Meter percent={cov.percent} />
            <span className="text-ink-muted text-sm">
              {cov.covered} of {cov.topics} topics covered
            </span>
            {cov.behindUnits > 0 && (
              <Badge tone="amber">{cov.behindUnits} unit(s) behind plan</Badge>
            )}
          </Card>
          {cov.units.length === 0 && (
            <Empty>
              {ov.canManage
                ? "No syllabus yet. Add the first unit below."
                : "The coordinator has not added the syllabus yet."}
            </Empty>
          )}
          {cov.units.map((u) => (
            <Card key={u.id} className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-semibold">
                  {u.title}
                  {u.plannedMonth ? (
                    <span className="text-ink-muted font-normal"> · {MONTHS[u.plannedMonth]}</span>
                  ) : null}
                </h3>
                <span className="flex items-center gap-2">
                  {u.behind && <Badge tone="amber">Behind plan</Badge>}
                  {ov.canManage && (
                    <Btn
                      size="sm"
                      variant="ghost"
                      aria-label={`Delete ${u.title}`}
                      onClick={() =>
                        void reload(sb(`curriculum/units/${u.id}`, { method: "DELETE" }))
                      }
                    >
                      <Trash2 className="size-4" aria-hidden />
                    </Btn>
                  )}
                </span>
              </div>
              <ul className="space-y-1">
                {u.topics.map((t) => (
                  <li key={t.id} className="flex items-center gap-2 text-sm">
                    <label className="flex flex-1 items-center gap-2">
                      <input
                        type="checkbox"
                        checked={!!t.coveredOn}
                        onChange={(e) =>
                          void reload(
                            sb("curriculum/coverage", {
                              method: "PUT",
                              body: { sectionId, topicId: t.id, covered: e.target.checked },
                            }),
                          )
                        }
                      />
                      <span className={t.coveredOn ? "" : ""}>{t.title}</span>
                    </label>
                    {t.coveredOn && (
                      <span className="text-ink-muted text-xs">
                        {day(t.coveredOn)}
                        {t.coveredBy ? ` · ${t.coveredBy}` : ""}
                      </span>
                    )}
                    {ov.canManage && (
                      <button
                        type="button"
                        aria-label={`Delete ${t.title}`}
                        className="text-ink-muted hover:text-ink px-1"
                        onClick={() =>
                          void reload(sb(`curriculum/topics/${t.id}`, { method: "DELETE" }))
                        }
                      >
                        ×
                      </button>
                    )}
                  </li>
                ))}
              </ul>
              {ov.canManage && (
                <div className="flex flex-wrap items-end gap-2">
                  <textarea
                    rows={2}
                    aria-label={`Add topics to ${u.title}`}
                    placeholder="Add topics, one per line"
                    className={`${inputCls} max-w-md`}
                    value={topicText[u.id] ?? ""}
                    onChange={(e) => setTopicText({ ...topicText, [u.id]: e.target.value })}
                  />
                  <Btn
                    size="sm"
                    disabled={!(topicText[u.id] ?? "").trim()}
                    onClick={async () => {
                      await reload(
                        sb(`curriculum/units/${u.id}/topics`, {
                          body: { titles: (topicText[u.id] ?? "").split("\n") },
                        }),
                      );
                      setTopicText({ ...topicText, [u.id]: "" });
                    }}
                  >
                    Add topics
                  </Btn>
                </div>
              )}
            </Card>
          ))}
          {ov.canManage && (
            <Card className="flex flex-wrap items-end gap-2">
              <Field label="New unit">
                <input
                  className={inputCls}
                  value={unit.title}
                  maxLength={120}
                  onChange={(e) => setUnit({ ...unit, title: e.target.value })}
                />
              </Field>
              <Field label="Planned for">
                <select
                  className={`${inputCls} w-auto!`}
                  value={unit.month}
                  onChange={(e) => setUnit({ ...unit, month: e.target.value })}
                >
                  <option value="">—</option>
                  {SCHOOL_MONTHS.map((m) => (
                    <option key={m} value={m}>
                      {MONTHS[m]}
                    </option>
                  ))}
                </select>
              </Field>
              <Btn
                variant="primary"
                busy={busy === "x"}
                disabled={!unit.title.trim()}
                onClick={async () => {
                  await reload(
                    sb("curriculum/units", {
                      body: {
                        subjectId: sid,
                        title: unit.title,
                        plannedMonth: unit.month ? Number(unit.month) : null,
                      },
                    }),
                  );
                  setUnit({ title: "", month: "" });
                }}
              >
                <Plus className="size-4" aria-hidden /> Add unit
              </Btn>
            </Card>
          )}
        </>
      )}
    </div>
  );
}

function CompletionView() {
  const [rows, setRows] = useState<Cell[] | null>(null);
  useEffect(() => {
    const t = setTimeout(() => void sb<Cell[]>("curriculum/coverage/overview").then(setRows), 0);
    return () => clearTimeout(t);
  }, []);
  if (!rows) return <Loading />;
  if (!rows.length) return <Empty>No syllabus has been added yet.</Empty>;
  return (
    <ul className="divide-y divide-black/5 rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
      {rows.map((r) => (
        <li
          key={r.sectionId + r.subjectId}
          className="flex flex-wrap items-center gap-3 px-4 py-2.5"
        >
          <span className="min-w-[12rem] flex-1 text-sm">
            <span className="font-medium">{r.section}</span> · {r.subject}
          </span>
          <Meter percent={r.percent} />
          {r.behindUnits > 0 && <Badge tone="amber">{r.behindUnits} behind</Badge>}
        </li>
      ))}
    </ul>
  );
}

function PlansView({ ov }: { ov: Overview }) {
  const [rows, setRows] = useState<Plan[] | null>(null);
  const [edit, setEdit] = useState<Plan | "new" | null>(null);
  const [note, setNote] = useState<Record<string, string>>({});
  const { busy, run } = useAction();
  const load = useCallback(async () => setRows(await sb<Plan[]>("curriculum/plans")), []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);
  if (edit)
    return (
      <PlanEditor
        ov={ov}
        plan={edit === "new" ? null : edit}
        onDone={() => {
          setEdit(null);
          void load();
        }}
      />
    );
  if (!rows) return <Loading />;
  return (
    <div className="space-y-4">
      <Btn variant="primary" onClick={() => setEdit("new")}>
        <Plus className="size-4" aria-hidden /> New lesson plan
      </Btn>
      {rows.length === 0 ? (
        <Empty>No lesson plans yet.</Empty>
      ) : (
        <ul className="space-y-3">
          {rows.map((p) => (
            <li key={p.id}>
              <Card className="space-y-2">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold">{p.title}</p>
                    <p className="text-ink-muted text-xs">
                      {p.section}
                      {p.subject ? ` · ${p.subject}` : ""}
                      {p.topic ? ` · ${p.topic}` : ""} · {day(p.plannedFor)}
                      {p.createdBy ? ` · by ${p.createdBy}` : ""}
                    </p>
                  </div>
                  <Badge
                    tone={
                      p.status === "REVIEWED"
                        ? "green"
                        : p.status === "SUBMITTED"
                          ? "amber"
                          : "gray"
                    }
                  >
                    {p.status === "REVIEWED"
                      ? "Reviewed"
                      : p.status === "SUBMITTED"
                        ? "Waiting for review"
                        : "Draft"}
                  </Badge>
                </div>
                {p.objectives && (
                  <p className="text-sm">
                    <span className="font-medium">Objectives: </span>
                    {p.objectives}
                  </p>
                )}
                {p.activities && (
                  <p className="text-sm whitespace-pre-line">
                    <span className="font-medium">Activities: </span>
                    {p.activities}
                  </p>
                )}
                {p.reviewNote && (
                  <p className="rounded-xl bg-emerald-50 px-3 py-2 text-sm">
                    {p.reviewedBy}: {p.reviewNote}
                  </p>
                )}
                <div className="flex flex-wrap items-center gap-2">
                  {p.status !== "REVIEWED" && !ov.canManage && (
                    <Btn size="sm" onClick={() => setEdit(p)}>
                      Edit
                    </Btn>
                  )}
                  {ov.canManage && p.status === "SUBMITTED" && (
                    <>
                      <input
                        aria-label="Review note"
                        placeholder="Note for the teacher (optional)"
                        className={`${inputCls} max-w-sm`}
                        value={note[p.id] ?? ""}
                        onChange={(e) => setNote({ ...note, [p.id]: e.target.value })}
                      />
                      <Btn
                        size="sm"
                        variant="success"
                        busy={busy === p.id}
                        onClick={async () => {
                          const r = await run(
                            p.id,
                            () =>
                              sb(`curriculum/plans/${p.id}/review`, {
                                body: { note: note[p.id] ?? "" },
                              }),
                            "Reviewed.",
                          );
                          if (r) void load();
                        }}
                      >
                        Mark reviewed
                      </Btn>
                    </>
                  )}
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function PlanEditor({ ov, plan, onDone }: { ov: Overview; plan: Plan | null; onDone: () => void }) {
  const [f, setF] = useState({
    sectionId: plan?.sectionId ?? ov.sections[0]!.id,
    subjectId: plan?.subjectId ?? "",
    topicId: plan?.topicId ?? "",
    plannedFor: plan?.plannedFor ?? todayIst(),
    title: plan?.title ?? "",
    objectives: plan?.objectives ?? "",
    activities: plan?.activities ?? "",
    resources: plan?.resources ?? "",
    assessment: plan?.assessment ?? "",
  });
  const [topics, setTopics] = useState<Topic[]>([]);
  const { busy, run } = useAction();
  const grade = ov.sections.find((s) => s.id === f.sectionId)?.grade;
  useEffect(() => {
    if (!f.subjectId) return;
    const t = setTimeout(
      () =>
        void sb<Coverage>(`curriculum/coverage?subjectId=${f.subjectId}`)
          .then((c) => setTopics(c.units.flatMap((u) => u.topics)))
          .catch(() => setTopics([])),
      0,
    );
    return () => clearTimeout(t);
  }, [f.subjectId]);
  async function save(submit: boolean) {
    const body = { ...f, subjectId: f.subjectId || null, topicId: f.topicId || null, submit };
    const r = await run(
      submit ? "submit" : "save",
      () =>
        plan
          ? sb(`curriculum/plans/${plan.id}`, { method: "PUT", body })
          : sb("curriculum/plans", { body }),
      submit ? "Sent for review." : "Saved.",
    );
    if (r) onDone();
  }
  return (
    <Card className="max-w-3xl space-y-3">
      <div className="grid gap-3 sm:grid-cols-3">
        <Field label="Class">
          <select
            className={inputCls}
            value={f.sectionId}
            onChange={(e) => setF({ ...f, sectionId: e.target.value, subjectId: "", topicId: "" })}
          >
            {ov.sections.map((s) => (
              <option key={s.id} value={s.id}>
                {s.label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Subject">
          <select
            className={inputCls}
            value={f.subjectId}
            onChange={(e) => setF({ ...f, subjectId: e.target.value, topicId: "" })}
          >
            <option value="">—</option>
            {ov.subjects
              .filter((s) => s.grade === grade)
              .map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
          </select>
        </Field>
        <Field label="Date">
          <input
            type="date"
            className={inputCls}
            value={f.plannedFor}
            onChange={(e) => setF({ ...f, plannedFor: e.target.value })}
          />
        </Field>
      </div>
      {topics.length > 0 && (
        <Field label="Syllabus topic">
          <select
            className={inputCls}
            value={f.topicId}
            onChange={(e) => setF({ ...f, topicId: e.target.value })}
          >
            <option value="">—</option>
            {topics.map((t) => (
              <option key={t.id} value={t.id}>
                {t.title}
              </option>
            ))}
          </select>
        </Field>
      )}
      <Field label="Title">
        <input
          className={inputCls}
          value={f.title}
          maxLength={160}
          onChange={(e) => setF({ ...f, title: e.target.value })}
        />
      </Field>
      <Field label="Learning objectives">
        <textarea
          rows={2}
          className={inputCls}
          value={f.objectives}
          maxLength={2000}
          onChange={(e) => setF({ ...f, objectives: e.target.value })}
        />
      </Field>
      <Field label="Activities">
        <textarea
          rows={4}
          className={inputCls}
          value={f.activities}
          maxLength={4000}
          onChange={(e) => setF({ ...f, activities: e.target.value })}
        />
      </Field>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Resources">
          <input
            className={inputCls}
            value={f.resources}
            maxLength={1000}
            onChange={(e) => setF({ ...f, resources: e.target.value })}
          />
        </Field>
        <Field label="How you'll check learning">
          <input
            className={inputCls}
            value={f.assessment}
            maxLength={1000}
            onChange={(e) => setF({ ...f, assessment: e.target.value })}
          />
        </Field>
      </div>
      <div className="flex gap-2">
        <Btn busy={busy === "save"} disabled={!f.title.trim()} onClick={() => void save(false)}>
          Save draft
        </Btn>
        <Btn
          variant="primary"
          busy={busy === "submit"}
          disabled={!f.title.trim()}
          onClick={() => void save(true)}
        >
          Send for review
        </Btn>
        <Btn variant="ghost" onClick={onDone}>
          Cancel
        </Btn>
      </div>
    </Card>
  );
}

function ObservationsView({ ov }: { ov: Overview }) {
  const [rows, setRows] = useState<Observation[] | null>(null);
  const [form, setForm] = useState<{
    teacherId: string;
    sectionId: string;
    observedOn: string;
    scores: Record<string, number>;
    strengths: string;
    suggestions: string;
  } | null>(null);
  const [refl, setRefl] = useState<Record<string, string>>({});
  const { busy, run } = useAction();
  const load = useCallback(
    async () => setRows(await sb<Observation[]>("curriculum/observations")),
    [],
  );
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);
  if (!rows) return <Loading />;
  return (
    <div className="space-y-4">
      {ov.canManage && !form && (
        <Btn
          variant="primary"
          onClick={() =>
            setForm({
              teacherId: ov.staff[0]?.id ?? "",
              sectionId: "",
              observedOn: todayIst(),
              scores: {},
              strengths: "",
              suggestions: "",
            })
          }
        >
          <Plus className="size-4" aria-hidden /> Record an observation
        </Btn>
      )}
      {form && (
        <Card className="max-w-3xl space-y-3">
          <div className="grid gap-3 sm:grid-cols-3">
            <Field label="Teacher">
              <select
                className={inputCls}
                value={form.teacherId}
                onChange={(e) => setForm({ ...form, teacherId: e.target.value })}
              >
                {ov.staff.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Class">
              <select
                className={inputCls}
                value={form.sectionId}
                onChange={(e) => setForm({ ...form, sectionId: e.target.value })}
              >
                <option value="">—</option>
                {ov.sections.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Date">
              <input
                type="date"
                className={inputCls}
                max={todayIst()}
                value={form.observedOn}
                onChange={(e) => setForm({ ...form, observedOn: e.target.value })}
              />
            </Field>
          </div>
          <fieldset className="grid gap-2 sm:grid-cols-2">
            <legend className="mb-1 text-sm font-medium">
              Scores (1 = needs support, 4 = excellent)
            </legend>
            {ov.criteria.map((c) => (
              <label key={c} className="flex items-center justify-between gap-2 text-sm">
                {c}
                <select
                  aria-label={c}
                  className={`${inputCls} w-20! py-1`}
                  value={form.scores[c] ?? ""}
                  onChange={(e) =>
                    setForm({ ...form, scores: { ...form.scores, [c]: Number(e.target.value) } })
                  }
                >
                  <option value="">–</option>
                  {[1, 2, 3, 4].map((n) => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
              </label>
            ))}
          </fieldset>
          <Field label="Strengths">
            <textarea
              rows={2}
              className={inputCls}
              value={form.strengths}
              onChange={(e) => setForm({ ...form, strengths: e.target.value })}
            />
          </Field>
          <Field label="Suggestions">
            <textarea
              rows={2}
              className={inputCls}
              value={form.suggestions}
              onChange={(e) => setForm({ ...form, suggestions: e.target.value })}
            />
          </Field>
          <div className="flex gap-2">
            <Btn
              variant="primary"
              busy={busy === "obs"}
              onClick={async () => {
                const r = await run(
                  "obs",
                  () =>
                    sb("curriculum/observations", {
                      body: { ...form, sectionId: form.sectionId || null },
                    }),
                  "Observation saved.",
                );
                if (r) {
                  setForm(null);
                  void load();
                }
              }}
            >
              Save
            </Btn>
            <Btn variant="ghost" onClick={() => setForm(null)}>
              Cancel
            </Btn>
          </div>
        </Card>
      )}
      {rows.length === 0 ? (
        <Empty>No observations yet.</Empty>
      ) : (
        <ul className="space-y-3">
          {rows.map((o) => (
            <li key={o.id}>
              <Card className="space-y-2">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold">
                      {o.teacher}
                      {o.section ? ` · ${o.section}` : ""}
                    </p>
                    <p className="text-ink-muted text-xs">
                      {day(o.observedOn)}
                      {o.observer ? ` · observed by ${o.observer}` : ""}
                    </p>
                  </div>
                  <Badge tone={o.acknowledgedAt ? "green" : "amber"}>
                    {o.acknowledgedAt ? "Acknowledged" : "Not yet acknowledged"}
                  </Badge>
                </div>
                <p className="text-sm">
                  {Object.entries(o.scores)
                    .map(([k, v]) => `${k} ${v}/4`)
                    .join(" · ")}
                  {o.average != null ? ` · average ${o.average}` : ""}
                </p>
                {o.strengths && (
                  <p className="text-sm">
                    <span className="font-medium">Strengths: </span>
                    {o.strengths}
                  </p>
                )}
                {o.suggestions && (
                  <p className="text-sm">
                    <span className="font-medium">Suggestions: </span>
                    {o.suggestions}
                  </p>
                )}
                {o.reflection && (
                  <p className="rounded-xl bg-black/[0.03] px-3 py-2 text-sm">
                    Teacher&apos;s reflection: {o.reflection}
                  </p>
                )}
                {!ov.canManage && !o.acknowledgedAt && (
                  <div className="flex flex-wrap items-end gap-2">
                    <textarea
                      rows={2}
                      aria-label="Your reflection"
                      placeholder="Your reflection (optional)"
                      className={`${inputCls} max-w-md`}
                      value={refl[o.id] ?? ""}
                      onChange={(e) => setRefl({ ...refl, [o.id]: e.target.value })}
                    />
                    <Btn
                      size="sm"
                      variant="primary"
                      busy={busy === o.id}
                      onClick={async () => {
                        const r = await run(
                          o.id,
                          () =>
                            sb(`curriculum/observations/${o.id}/acknowledge`, {
                              body: { note: refl[o.id] ?? "" },
                            }),
                          "Acknowledged.",
                        );
                        if (r) void load();
                      }}
                    >
                      Acknowledge
                    </Btn>
                  </div>
                )}
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
