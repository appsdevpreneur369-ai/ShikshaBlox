"use client";

import { useCallback, useEffect, useState } from "react";
import { ArrowLeft, Plus, Printer, Trash2 } from "lucide-react";
import { sb } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Field, Loading, inputCls, useAction } from "@/components/kit";
import {
  STATUS_LABEL,
  fmtDay,
  type Band,
  type Component,
  type Exam,
  type Overview,
  type Paper,
  type Settings,
  type Subject,
  type Teachers,
} from "./types";

const STATUS_TONE = { DRAFT: "gray", MARKS_OPEN: "blue", PUBLISHED: "green" } as const;

// ------------------------------------------------------------------ exams

export function ExamsSetup({ overview, onChanged }: { overview: Overview; onChanged: () => void }) {
  const [edit, setEdit] = useState<Exam | "new" | null>(null);
  const [papers, setPapers] = useState<Exam | null>(null);
  const { busy, run } = useAction();

  if (edit)
    return (
      <ExamEditor
        overview={overview}
        exam={edit === "new" ? null : edit}
        onDone={() => {
          setEdit(null);
          onChanged();
        }}
      />
    );
  if (papers) return <Timetable overview={overview} exam={papers} onBack={() => setPapers(null)} />;

  async function status(e: Exam, to: Exam["status"], ok: string) {
    const r = await run(e.id, () => sb(`exams/${e.id}/status`, { body: { status: to } }), ok);
    if (r) onChanged();
  }

  return (
    <div className="space-y-4">
      <Btn variant="primary" onClick={() => setEdit("new")}>
        <Plus className="size-4" aria-hidden /> New exam
      </Btn>
      {overview.exams.length === 0 ? (
        <Empty>
          No exams yet. Add subjects first, then create exams such as Periodic Test 1 or
          Half-Yearly.
        </Empty>
      ) : (
        <ul className="space-y-3">
          {overview.exams.map((e) => (
            <li key={e.id}>
              <Card className="space-y-3">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold">
                      {e.name}
                      {e.term && <span className="text-ink-muted font-normal"> · {e.term}</span>}
                    </p>
                    <p className="text-ink-muted text-sm">
                      {e.grades.join(", ")} ·{" "}
                      {e.components.map((c) => `${c.label} ${c.max}`).join(" + ")}
                      {e.weight ? ` · ${e.weight}% of the year` : ""}
                      {e.startDate
                        ? ` · ${fmtDay(e.startDate)}${e.endDate && e.endDate !== e.startDate ? ` – ${fmtDay(e.endDate)}` : ""}`
                        : ""}
                    </p>
                  </div>
                  <Badge tone={STATUS_TONE[e.status]}>{STATUS_LABEL[e.status]}</Badge>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Btn size="sm" onClick={() => setEdit(e)}>
                    Edit
                  </Btn>
                  {overview.settings.timetable && (
                    <Btn size="sm" onClick={() => setPapers(e)}>
                      Timetable & hall tickets
                    </Btn>
                  )}
                  {e.status === "DRAFT" && (
                    <Btn
                      size="sm"
                      variant="primary"
                      busy={busy === e.id}
                      onClick={() => void status(e, "MARKS_OPEN", "Teachers can now enter marks.")}
                    >
                      Open for marks
                    </Btn>
                  )}
                  {e.status === "MARKS_OPEN" && (
                    <Btn
                      size="sm"
                      variant="success"
                      busy={busy === e.id}
                      onClick={() => void status(e, "PUBLISHED", "Results published to parents.")}
                    >
                      Publish results
                    </Btn>
                  )}
                  {e.status === "PUBLISHED" && (
                    <Btn
                      size="sm"
                      variant="danger"
                      busy={busy === e.id}
                      onClick={() => void status(e, "MARKS_OPEN", "Results unpublished.")}
                    >
                      Unpublish
                    </Btn>
                  )}
                  {e.status === "DRAFT" && (
                    <Btn
                      size="sm"
                      variant="ghost"
                      onClick={async () => {
                        const r = await run(
                          `del-${e.id}`,
                          () => sb(`exams/${e.id}`, { method: "DELETE" }).then(() => true),
                          "Exam deleted.",
                        );
                        if (r) onChanged();
                      }}
                    >
                      <Trash2 className="size-4" aria-hidden /> Delete
                    </Btn>
                  )}
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}
      <p className="text-ink-muted text-xs">
        Publishing shows the results in the parent portal and messages parents (through Messages,
        using their opt-ins).
        {overview.settings.moderation ? " Every class subject must be approved first." : ""}
      </p>
    </div>
  );
}

function ExamEditor({
  overview,
  exam,
  onDone,
}: {
  overview: Overview;
  exam: Exam | null;
  onDone: () => void;
}) {
  const [name, setName] = useState(exam?.name ?? "");
  const [term, setTerm] = useState(exam?.term ?? "");
  const [grades, setGrades] = useState<string[]>(exam?.grades ?? overview.grades);
  const [parts, setParts] = useState<Component[]>(
    exam?.components ?? [{ key: "M", label: "Marks", max: 100 }],
  );
  const [weight, setWeight] = useState(exam?.weight ?? 0);
  const [start, setStart] = useState(exam?.startDate ?? "");
  const [end, setEnd] = useState(exam?.endDate ?? "");
  const { busy, run } = useAction();

  async function save() {
    const body = {
      name,
      term: term || null,
      grades,
      components: parts,
      weight,
      startDate: start || null,
      endDate: end || null,
    };
    const r = await run(
      "save",
      () => (exam ? sb(`exams/${exam.id}`, { method: "PUT", body }) : sb("exams", { body })),
      "Exam saved.",
    );
    if (r) onDone();
  }

  return (
    <Card className="max-w-2xl space-y-4">
      <button
        type="button"
        onClick={onDone}
        className="text-ink-muted hover:text-ink inline-flex items-center gap-1 text-sm"
      >
        <ArrowLeft className="size-4" aria-hidden /> Exams
      </button>
      <h2 className="text-lg font-semibold">{exam ? "Edit exam" : "New exam"}</h2>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Name">
          <input
            className={inputCls}
            value={name}
            maxLength={80}
            placeholder="Half-Yearly Examination"
            onChange={(e) => setName(e.target.value)}
          />
        </Field>
        <Field label="Term (optional)">
          <input
            className={inputCls}
            value={term}
            maxLength={40}
            placeholder="Term 1"
            onChange={(e) => setTerm(e.target.value)}
          />
        </Field>
      </div>
      <fieldset>
        <legend className="mb-1.5 text-sm font-medium">Classes</legend>
        <div className="flex flex-wrap gap-2">
          {overview.grades.map((g) => (
            <label
              key={g}
              className="inline-flex items-center gap-1.5 rounded-full bg-black/[0.04] px-3 py-1 text-sm"
            >
              <input
                type="checkbox"
                checked={grades.includes(g)}
                onChange={(e) =>
                  setGrades(e.target.checked ? [...grades, g] : grades.filter((x) => x !== g))
                }
              />
              {g}
            </label>
          ))}
        </div>
      </fieldset>
      <fieldset className="space-y-2">
        <legend className="mb-1 text-sm font-medium">
          Parts and maximum marks (same for every subject)
        </legend>
        {parts.map((p, i) => (
          <div key={i} className="flex flex-wrap items-center gap-2">
            <input
              aria-label="Part name"
              className={`${inputCls} max-w-xs`}
              value={p.label}
              maxLength={40}
              onChange={(e) =>
                setParts(parts.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))
              }
            />
            <input
              aria-label="Maximum marks"
              type="number"
              min={1}
              max={200}
              className={`${inputCls} w-24!`}
              value={p.max}
              onChange={(e) =>
                setParts(parts.map((x, j) => (j === i ? { ...x, max: Number(e.target.value) } : x)))
              }
            />
            {parts.length > 1 && (
              <Btn
                size="sm"
                variant="ghost"
                onClick={() => setParts(parts.filter((_, j) => j !== i))}
              >
                Remove
              </Btn>
            )}
          </div>
        ))}
        {parts.length < 4 && (
          <Btn
            size="sm"
            onClick={() =>
              setParts([
                ...parts,
                { key: `P${parts.length + 1}`, label: "Internal assessment", max: 20 },
              ])
            }
          >
            Add a part
          </Btn>
        )}
      </fieldset>
      <div className="grid gap-3 sm:grid-cols-3">
        <Field label="Weight in the year (%)" hint="0 = not counted">
          <input
            type="number"
            min={0}
            max={100}
            className={inputCls}
            value={weight}
            onChange={(e) => setWeight(Number(e.target.value))}
          />
        </Field>
        <Field label="Starts">
          <input
            type="date"
            className={inputCls}
            value={start}
            onChange={(e) => setStart(e.target.value)}
          />
        </Field>
        <Field label="Ends">
          <input
            type="date"
            className={inputCls}
            value={end}
            onChange={(e) => setEnd(e.target.value)}
          />
        </Field>
      </div>
      <Btn
        variant="primary"
        busy={busy === "save"}
        disabled={!name.trim() || grades.length === 0}
        onClick={() => void save()}
      >
        Save exam
      </Btn>
    </Card>
  );
}

function Timetable({
  overview,
  exam,
  onBack,
}: {
  overview: Overview;
  exam: Exam;
  onBack: () => void;
}) {
  const [rows, setRows] = useState<Paper[] | null>(null);
  const [sections, setSections] = useState<{ id: string; label: string; grade: string }[]>([]);
  const { busy, run } = useAction();
  useEffect(() => {
    const t = setTimeout(async () => {
      setRows(await sb<Paper[]>(`exams/${exam.id}/papers`));
      const t = await sb<Teachers>("exams/teachers").catch(() => null);
      setSections(
        (t?.sections ?? [])
          .filter((s) => exam.grades.includes(s.grade))
          .map((s) => ({ id: s.sectionId, label: s.label, grade: s.grade })),
      );
    }, 0);
    return () => clearTimeout(t);
  }, [exam]);
  if (!rows) return <Loading />;
  const set = (i: number, p: Partial<Paper>) =>
    setRows(rows.map((x, j) => (j === i ? { ...x, ...p } : x)));
  return (
    <div className="space-y-4">
      <button
        type="button"
        onClick={onBack}
        className="text-ink-muted hover:text-ink inline-flex items-center gap-1 text-sm"
      >
        <ArrowLeft className="size-4" aria-hidden /> Exams
      </button>
      <h2 className="text-xl font-bold">Timetable · {exam.name}</h2>
      {overview.grades
        .filter((g) => exam.grades.includes(g))
        .map((g) => (
          <Card key={g}>
            <h3 className="mb-2 font-semibold">{g}</h3>
            <div className="space-y-2">
              {rows.map((p, i) =>
                p.grade !== g || p.kind !== "SCHOLASTIC" ? null : (
                  <div key={p.subjectId} className="flex flex-wrap items-center gap-2">
                    <label className="inline-flex w-48 items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        checked={p.included}
                        onChange={(e) => set(i, { included: e.target.checked })}
                      />
                      {p.subject}
                    </label>
                    <input
                      aria-label={`${p.subject} date`}
                      type="date"
                      disabled={!p.included}
                      className={`${inputCls} w-auto!`}
                      value={p.date ?? ""}
                      onChange={(e) => set(i, { date: e.target.value || null })}
                    />
                    <input
                      aria-label={`${p.subject} time`}
                      type="time"
                      disabled={!p.included}
                      className={`${inputCls} w-auto!`}
                      value={p.startTime?.slice(0, 5) ?? ""}
                      onChange={(e) => set(i, { startTime: e.target.value || null })}
                    />
                    <input
                      aria-label={`${p.subject} minutes`}
                      type="number"
                      min={10}
                      max={300}
                      placeholder="min"
                      disabled={!p.included}
                      className={`${inputCls} w-24!`}
                      value={p.durationMin ?? ""}
                      onChange={(e) =>
                        set(i, { durationMin: e.target.value ? Number(e.target.value) : null })
                      }
                    />
                  </div>
                ),
              )}
            </div>
          </Card>
        ))}
      <div className="flex flex-wrap items-center gap-2">
        <Btn
          variant="primary"
          busy={busy === "save"}
          onClick={async () => {
            // Co-scholastic subjects are assessed without a paper; keep them part of the exam.
            const body = rows.map((p) => ({
              subjectId: p.subjectId,
              included: p.kind !== "SCHOLASTIC" || p.included,
              date: p.date || null,
              startTime: p.startTime || null,
              durationMin: p.durationMin ?? null,
            }));
            const r = await run(
              "save",
              () => sb<Paper[]>(`exams/${exam.id}/papers`, { method: "PUT", body }),
              "Timetable saved.",
            );
            if (r) setRows(r);
          }}
        >
          Save timetable
        </Btn>
        {overview.settings.hallTickets &&
          sections.map((s) => (
            <a
              key={s.id}
              href={`/print/hall-tickets?examId=${exam.id}&sectionId=${s.id}`}
              target="_blank"
              rel="noreferrer"
              className="inline-flex h-8 items-center gap-1 rounded-full bg-white px-3 text-xs font-medium ring-1 ring-black/10 hover:bg-black/[0.03]"
            >
              <Printer className="size-3.5" aria-hidden /> Hall tickets {s.label}
            </a>
          ))}
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ subjects & teachers

export function SubjectsSetup({ overview }: { overview: Overview }) {
  const [subjects, setSubjects] = useState<Subject[] | null>(null);
  const [teachers, setTeachers] = useState<Teachers | null>(null);
  const [grade, setGrade] = useState(overview.grades[0] ?? "");
  const [name, setName] = useState("");
  const [kind, setKind] = useState<Subject["kind"]>("SCHOLASTIC");
  const { busy, run } = useAction();

  const load = useCallback(async () => {
    setSubjects(await sb<Subject[]>("exams/subjects"));
    setTeachers(await sb<Teachers>("exams/teachers"));
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);
  if (!subjects || !teachers) return <Loading />;
  const mine = subjects.filter((s) => s.grade === grade);

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card className="space-y-4 self-start">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <label className="block">
            <span className="mb-1 block text-sm font-medium">Class</span>
            <select
              className={`${inputCls} w-auto!`}
              value={grade}
              onChange={(e) => setGrade(e.target.value)}
            >
              {overview.grades.map((g) => (
                <option key={g}>{g}</option>
              ))}
            </select>
          </label>
          <Btn
            size="sm"
            busy={busy === "preset"}
            onClick={async () => {
              const r = await run(
                "preset",
                () => sb<Subject[]>("exams/subjects/preset", { body: { grades: overview.grades } }),
                `Added the usual ${overview.settings.board} subjects.`,
              );
              if (r) void load();
            }}
          >
            Add usual {overview.settings.board} subjects to all classes
          </Btn>
        </div>
        {mine.length === 0 ? (
          <p className="text-ink-muted text-sm">
            No subjects for {grade} yet. (Pre-primary classes are assessed through early-years
            observations.)
          </p>
        ) : (
          <ul className="divide-y divide-black/5">
            {mine.map((s) => (
              <li key={s.id} className="flex items-center justify-between gap-2 py-2 text-sm">
                <span>
                  {s.name}
                  {s.kind === "CO_SCHOLASTIC" && (
                    <span className="text-ink-muted text-xs"> · co-scholastic</span>
                  )}
                </span>
                <Btn
                  size="sm"
                  variant="ghost"
                  aria-label={`Remove ${s.name}`}
                  onClick={async () => {
                    const r = await run(
                      s.id,
                      () => sb(`exams/subjects/${s.id}`, { method: "DELETE" }).then(() => true),
                      "Removed.",
                    );
                    if (r) void load();
                  }}
                >
                  <Trash2 className="size-4" aria-hidden />
                </Btn>
              </li>
            ))}
          </ul>
        )}
        <div className="flex flex-wrap items-end gap-2 border-t border-black/5 pt-3">
          <Field label="Add a subject">
            <input
              className={inputCls}
              value={name}
              maxLength={80}
              onChange={(e) => setName(e.target.value)}
            />
          </Field>
          <select
            aria-label="Kind"
            className={`${inputCls} w-auto!`}
            value={kind}
            onChange={(e) => setKind(e.target.value as Subject["kind"])}
          >
            <option value="SCHOLASTIC">Scholastic (marks)</option>
            <option value="CO_SCHOLASTIC">Co-scholastic (grade)</option>
          </select>
          <Btn
            variant="primary"
            disabled={!name.trim()}
            busy={busy === "add"}
            onClick={async () => {
              const r = await run(
                "add",
                () => sb<Subject[]>("exams/subjects", { body: { grade, name, kind } }),
                "Subject added.",
              );
              if (r) {
                setName("");
                void load();
              }
            }}
          >
            Add
          </Btn>
        </div>
      </Card>

      <Card className="space-y-3 self-start">
        <h2 className="text-lg font-semibold">Subject teachers</h2>
        <p className="text-ink-muted text-sm">
          Class teachers (set under Attendance) can enter every subject of their class. Add subject
          teachers here.
        </p>
        {teachers.sections
          .filter((s) => s.grade === grade)
          .map((s) => (
            <div key={s.sectionId}>
              <h3 className="mt-2 text-sm font-semibold">{s.label}</h3>
              <ul className="divide-y divide-black/5">
                {s.subjects.map((sub) => (
                  <li
                    key={sub.subjectId}
                    className="flex flex-wrap items-center gap-2 py-1.5 text-sm"
                  >
                    <span className="w-40 shrink-0">{sub.subject}</span>
                    {sub.teachers.map((t) => (
                      <span
                        key={t.id}
                        className="inline-flex items-center gap-1 rounded-full bg-black/[0.05] py-0.5 pr-1 pl-2.5 text-xs"
                      >
                        {t.name}
                        <button
                          type="button"
                          aria-label={`Remove ${t.name}`}
                          className="grid size-5 place-items-center rounded-full hover:bg-black/10"
                          onClick={async () => {
                            const r = await run(sub.subjectId, () =>
                              sb<Teachers>(
                                `exams/sections/${s.sectionId}/subjects/${sub.subjectId}/teachers`,
                                {
                                  method: "PUT",
                                  body: {
                                    userIds: sub.teachers
                                      .filter((x) => x.id !== t.id)
                                      .map((x) => x.id),
                                  },
                                },
                              ),
                            );
                            if (r) setTeachers(r);
                          }}
                        >
                          ×
                        </button>
                      </span>
                    ))}
                    <select
                      aria-label={`Add a teacher for ${sub.subject} in ${s.label}`}
                      className={`${inputCls} w-auto! py-1 text-xs`}
                      value=""
                      onChange={async (e) => {
                        if (!e.target.value) return;
                        const r = await run(sub.subjectId, () =>
                          sb<Teachers>(
                            `exams/sections/${s.sectionId}/subjects/${sub.subjectId}/teachers`,
                            {
                              method: "PUT",
                              body: { userIds: [...sub.teachers.map((x) => x.id), e.target.value] },
                            },
                          ),
                        );
                        if (r) setTeachers(r);
                      }}
                    >
                      <option value="">+ Add</option>
                      {teachers.staff
                        .filter((t) => !sub.teachers.some((x) => x.id === t.id))
                        .map((t) => (
                          <option key={t.id} value={t.id}>
                            {t.name}
                          </option>
                        ))}
                    </select>
                  </li>
                ))}
              </ul>
            </div>
          ))}
      </Card>
    </div>
  );
}

// ------------------------------------------------------------------ grading settings

export function SettingsSetup({
  settings,
  onChanged,
}: {
  settings: Settings;
  onChanged: () => void;
}) {
  const [s, setS] = useState(settings);
  const [bands, setBands] = useState<Band[]>(settings.bands);
  const [co, setCo] = useState(settings.coGrades.join(", "));
  const { busy, run } = useAction();
  return (
    <Card className="max-w-2xl space-y-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <Field label="Board">
          <select
            className={inputCls}
            value={s.board}
            onChange={(e) => setS({ ...s, board: e.target.value as Settings["board"] })}
          >
            <option value="CBSE">CBSE</option>
            <option value="ICSE">ICSE / ISC</option>
            <option value="STATE">State board</option>
            <option value="OTHER">Other</option>
          </select>
        </Field>
        <Field label="Pass mark per subject (%)">
          <input
            type="number"
            min={0}
            max={100}
            className={inputCls}
            value={s.passPercent}
            onChange={(e) => setS({ ...s, passPercent: Number(e.target.value) })}
          />
        </Field>
        <Field label="Principal (signs report cards)">
          <input
            className={inputCls}
            value={s.principalName ?? ""}
            maxLength={120}
            onChange={(e) => setS({ ...s, principalName: e.target.value })}
          />
        </Field>
      </div>
      <fieldset>
        <legend className="mb-1.5 text-sm font-medium">Grade scale (from % upwards)</legend>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {bands.map((b, i) => (
            <div key={i} className="flex items-center gap-1">
              <input
                aria-label="Grade"
                className={`${inputCls} w-16! py-1`}
                value={b.grade}
                maxLength={4}
                onChange={(e) =>
                  setBands(bands.map((x, j) => (j === i ? { ...x, grade: e.target.value } : x)))
                }
              />
              <span className="text-ink-muted text-xs">≥</span>
              <input
                aria-label={`Minimum % for ${b.grade}`}
                type="number"
                min={0}
                max={100}
                className={`${inputCls} w-16! py-1`}
                value={b.min}
                onChange={(e) =>
                  setBands(
                    bands.map((x, j) => (j === i ? { ...x, min: Number(e.target.value) } : x)),
                  )
                }
              />
            </div>
          ))}
        </div>
      </fieldset>
      <Field label="Co-scholastic grades" hint="Separated by commas, best first.">
        <input className={inputCls} value={co} onChange={(e) => setCo(e.target.value)} />
      </Field>
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={s.showRank}
          onChange={(e) => setS({ ...s, showRank: e.target.checked })}
        />
        Show rank in class on report cards
      </label>
      <Btn
        variant="primary"
        busy={busy === "save"}
        onClick={async () => {
          const r = await run(
            "save",
            () =>
              sb("exams/settings", {
                method: "PUT",
                body: {
                  board: s.board,
                  passPercent: s.passPercent,
                  bands,
                  coGrades: co.split(","),
                  principalName: s.principalName ?? "",
                  showRank: s.showRank,
                },
              }),
            "Grading saved.",
          );
          if (r) onChanged();
        }}
      >
        Save
      </Btn>
    </Card>
  );
}
