"use client";

import { useCallback, useEffect, useState } from "react";
import { ArrowLeft, Paperclip, Plus, Trash2 } from "lucide-react";
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

type Criterion = { criterion: string; points: number };
type Overview = {
  sections: { id: string; label: string; grade: string }[];
  subjects: { id: string; grade: string; name: string }[];
  canManage: boolean;
  submissions: boolean;
  rubrics: boolean;
  alerts: boolean;
};
type FileRef = { id: string; name: string; sizeBytes: number };
type Homework = {
  id: string;
  sectionId: string;
  section: string;
  subjectId?: string | null;
  subject?: string | null;
  title: string;
  details?: string | null;
  assignedOn: string;
  dueOn: string;
  allowSubmissions: boolean;
  maxPoints?: number | null;
  rubric: Criterion[];
  latePolicy: "ACCEPT" | "CLOSE";
  createdBy?: string | null;
  files: FileRef[];
  students: number;
  submitted: number;
  graded: number;
};
type Submission = {
  id?: string | null;
  studentId: string;
  studentName: string;
  rollNo?: number | null;
  status: "NOT_SUBMITTED" | "SUBMITTED" | "RETURNED" | "GRADED";
  text?: string | null;
  fileName?: string | null;
  submittedAt?: string | null;
  late: boolean;
  points?: number | null;
  rubricScores: Record<string, number>;
  feedback?: string | null;
};

const todayIst = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
const day = (d: string) =>
  new Intl.DateTimeFormat("en-IN", { weekday: "short", day: "numeric", month: "short" }).format(
    new Date(`${d}T00:00:00`),
  );

async function openUrl(path: string) {
  const r = await sb<{ url: string }>(path);
  window.open(r.url, "_blank", "noopener");
}

export function HomeworkPage() {
  const [ov, setOv] = useState<Overview | null>(null);
  const [rows, setRows] = useState<Homework[] | null>(null);
  const [section, setSection] = useState("");
  const [mode, setMode] = useState<"list" | "new" | { hw: Homework }>("list");
  const [blocked, setBlocked] = useState(false);

  const load = useCallback(async () => {
    try {
      setOv(await sb<Overview>("homework/overview"));
      setRows(await sb<Homework[]>(`homework${section ? `?sectionId=${section}` : ""}`));
    } catch (e) {
      if (e instanceof StudioError && e.status === 403) setBlocked(true);
    }
  }, [section]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  if (blocked)
    return (
      <div>
        <PageHeader title="Homework" />
        <Card className="max-w-2xl">
          <h2 className="text-lg font-semibold">Homework is not part of your plan yet</h2>
          <p className="text-ink-muted mt-2 text-sm">
            Upgrade to ShikshaBlox Lite or Standard to post homework with parent alerts.
          </p>
        </Card>
      </div>
    );
  if (!ov || !rows) return <Loading />;
  if (mode === "new")
    return (
      <NewHomework
        ov={ov}
        onDone={() => {
          setMode("list");
          void load();
        }}
      />
    );
  if (typeof mode === "object")
    return (
      <Submissions
        hw={mode.hw}
        ov={ov}
        onBack={() => {
          setMode("list");
          void load();
        }}
      />
    );

  return (
    <div>
      <PageHeader
        title="Homework"
        subtitle="Post homework to your classes. Parents see it in the parent portal."
        actions={
          ov.sections.length > 0 && (
            <Btn variant="primary" onClick={() => setMode("new")}>
              <Plus className="size-4" aria-hidden /> New homework
            </Btn>
          )
        }
      />
      {ov.sections.length === 0 ? (
        <Empty>
          You are not a class or subject teacher of any class yet. The office assigns classes under
          Attendance and Exams.
        </Empty>
      ) : (
        <div className="space-y-4">
          <select
            aria-label="Class"
            className={`${inputCls} w-auto!`}
            value={section}
            onChange={(e) => setSection(e.target.value)}
          >
            <option value="">All my classes</option>
            {ov.sections.map((s) => (
              <option key={s.id} value={s.id}>
                {s.label}
              </option>
            ))}
          </select>
          {rows.length === 0 ? (
            <Empty>No homework yet.</Empty>
          ) : (
            <ul className="divide-y divide-black/5 rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
              {rows.map((h) => {
                const overdue = h.dueOn < todayIst();
                return (
                  <li key={h.id}>
                    <button
                      type="button"
                      onClick={() => setMode({ hw: h })}
                      className="flex w-full flex-wrap items-center gap-3 p-4 text-left hover:bg-black/[0.02]"
                    >
                      <span className="min-w-[14rem] flex-1">
                        <span className="block font-semibold">{h.title}</span>
                        <span className="text-ink-muted block text-xs">
                          {h.section}
                          {h.subject ? ` · ${h.subject}` : ""} · set {day(h.assignedOn)}
                          {h.files.length ? ` · ${h.files.length} file(s)` : ""}
                        </span>
                      </span>
                      {h.allowSubmissions && (
                        <span className="text-ink-muted text-sm tabular-nums">
                          {h.submitted}/{h.students} handed in
                          {h.graded ? ` · ${h.graded} marked` : ""}
                        </span>
                      )}
                      <Badge tone={overdue ? "gray" : "blue"}>Due {day(h.dueOn)}</Badge>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

function NewHomework({ ov, onDone }: { ov: Overview; onDone: () => void }) {
  const [sections, setSections] = useState<string[]>(
    ov.sections.length === 1 ? [ov.sections[0]!.id] : [],
  );
  const [subject, setSubject] = useState("");
  const [title, setTitle] = useState("");
  const [details, setDetails] = useState("");
  const [due, setDue] = useState("");
  const [allow, setAllow] = useState(false);
  const [max, setMax] = useState("");
  const [rubric, setRubric] = useState<Criterion[]>([]);
  const [late, setLate] = useState<"ACCEPT" | "CLOSE">("ACCEPT");
  const [notify, setNotify] = useState(true);
  const [files, setFiles] = useState<File[]>([]);
  const { busy, run } = useAction();
  const grades = new Set(ov.sections.filter((s) => sections.includes(s.id)).map((s) => s.grade));
  const subjectNames = [
    ...new Set(ov.subjects.filter((s) => grades.has(s.grade)).map((s) => s.name)),
  ];

  async function save() {
    // Any section's subject with that name; the server maps it to each section's class.
    const subj = ov.subjects.find((s) => s.name === subject && grades.has(s.grade));
    const created = await run("save", () =>
      sb<Homework[]>("homework", {
        body: {
          sectionIds: sections,
          subjectId: subj?.id ?? null,
          subjectName: subj ? null : subject || null,
          title,
          details: details || null,
          dueOn: due,
          allowSubmissions: allow,
          maxPoints: max ? Number(max) : null,
          rubric: rubric.filter((r) => r.criterion.trim()),
          latePolicy: late,
          notifyParents: notify,
        },
      }),
    );
    if (!created) return;
    for (const h of created)
      for (const f of files) {
        const form = new FormData();
        form.append("file", f);
        await run(`file-${h.id}`, () => sb(`homework/${h.id}/files`, { form }));
      }
    onDone();
  }

  return (
    <Card className="max-w-3xl space-y-4">
      <button
        type="button"
        onClick={onDone}
        className="text-ink-muted hover:text-ink inline-flex items-center gap-1 text-sm"
      >
        <ArrowLeft className="size-4" aria-hidden /> Homework
      </button>
      <h2 className="text-lg font-semibold">New homework</h2>
      <fieldset>
        <legend className="mb-1.5 text-sm font-medium">Classes</legend>
        <div className="flex flex-wrap gap-2">
          {ov.sections.map((s) => (
            <label
              key={s.id}
              className="inline-flex items-center gap-1.5 rounded-full bg-black/[0.04] px-3 py-1 text-sm"
            >
              <input
                type="checkbox"
                checked={sections.includes(s.id)}
                onChange={(e) =>
                  setSections(
                    e.target.checked ? [...sections, s.id] : sections.filter((x) => x !== s.id),
                  )
                }
              />
              {s.label}
            </label>
          ))}
        </div>
      </fieldset>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Subject">
          <input
            className={inputCls}
            list="hw-subjects"
            value={subject}
            maxLength={80}
            onChange={(e) => setSubject(e.target.value)}
          />
          <datalist id="hw-subjects">
            {subjectNames.map((n) => (
              <option key={n} value={n} />
            ))}
          </datalist>
        </Field>
        <Field label="Due on">
          <input
            type="date"
            className={inputCls}
            min={todayIst()}
            value={due}
            onChange={(e) => setDue(e.target.value)}
          />
        </Field>
      </div>
      <Field label="Title">
        <input
          className={inputCls}
          value={title}
          maxLength={160}
          onChange={(e) => setTitle(e.target.value)}
        />
      </Field>
      <Field label="Details (optional)">
        <textarea
          rows={4}
          className={inputCls}
          value={details}
          maxLength={4000}
          onChange={(e) => setDetails(e.target.value)}
        />
      </Field>
      <Field label="Attachments (PDF, JPG or PNG; up to 5 files, 10 MB each)">
        <input
          type="file"
          multiple
          accept="application/pdf,image/jpeg,image/png"
          onChange={(e) => setFiles(Array.from(e.target.files ?? []).slice(0, 5))}
          className="text-sm"
        />
      </Field>
      {ov.submissions && (
        <fieldset className="space-y-2 rounded-xl bg-black/[0.02] p-3">
          <label className="flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" checked={allow} onChange={(e) => setAllow(e.target.checked)} />{" "}
            Parents hand in the work online
          </label>
          {allow && (
            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-3">
                <label className="text-sm">
                  Out of{" "}
                  <input
                    type="number"
                    min={1}
                    max={1000}
                    className={`${inputCls} inline-block w-24!`}
                    value={max}
                    onChange={(e) => setMax(e.target.value)}
                  />
                </label>
                <select
                  aria-label="Late work"
                  className={`${inputCls} w-auto!`}
                  value={late}
                  onChange={(e) => setLate(e.target.value as "ACCEPT" | "CLOSE")}
                >
                  <option value="ACCEPT">Accept late work (marked late)</option>
                  <option value="CLOSE">Close after the due date</option>
                </select>
              </div>
              {ov.rubrics && (
                <div className="space-y-1.5">
                  <p className="text-sm">Rubric (optional)</p>
                  {rubric.map((r, i) => (
                    <div key={i} className="flex gap-2">
                      <input
                        aria-label="Criterion"
                        className={`${inputCls} max-w-xs`}
                        value={r.criterion}
                        maxLength={60}
                        onChange={(e) =>
                          setRubric(
                            rubric.map((x, j) =>
                              j === i ? { ...x, criterion: e.target.value } : x,
                            ),
                          )
                        }
                      />
                      <input
                        aria-label="Points"
                        type="number"
                        min={1}
                        max={100}
                        className={`${inputCls} w-20!`}
                        value={r.points}
                        onChange={(e) =>
                          setRubric(
                            rubric.map((x, j) =>
                              j === i ? { ...x, points: Number(e.target.value) } : x,
                            ),
                          )
                        }
                      />
                      <Btn
                        size="sm"
                        variant="ghost"
                        aria-label="Remove row"
                        onClick={() => setRubric(rubric.filter((_, j) => j !== i))}
                      >
                        <Trash2 className="size-4" aria-hidden />
                      </Btn>
                    </div>
                  ))}
                  {rubric.length < 8 && (
                    <Btn
                      size="sm"
                      onClick={() => setRubric([...rubric, { criterion: "", points: 5 }])}
                    >
                      Add a rubric row
                    </Btn>
                  )}
                </div>
              )}
            </div>
          )}
        </fieldset>
      )}
      {ov.alerts && (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={notify} onChange={(e) => setNotify(e.target.checked)} />{" "}
          Send parents a message (through Messages, using their opt-ins)
        </label>
      )}
      <Btn
        variant="primary"
        busy={busy === "save" || !!busy?.startsWith("file")}
        disabled={!sections.length || !title.trim() || !due}
        onClick={() => void save()}
      >
        Post homework
      </Btn>
    </Card>
  );
}

function Submissions({ hw, ov, onBack }: { hw: Homework; ov: Overview; onBack: () => void }) {
  const [rows, setRows] = useState<Submission[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [form, setForm] = useState<{
    points: string;
    feedback: string;
    scores: Record<string, string>;
  }>({ points: "", feedback: "", scores: {} });
  const { busy, run } = useAction();
  const load = useCallback(
    async () => setRows(await sb<Submission[]>(`homework/${hw.id}/submissions`)),
    [hw.id],
  );
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  async function grade(s: Submission, redo: boolean) {
    const scores: Record<string, number> = {};
    Object.entries(form.scores).forEach(([k, v]) => v !== "" && (scores[k] = Number(v)));
    const r = await run(
      s.id!,
      () =>
        sb(`homework/submissions/${s.id}/grade`, {
          body: {
            points: form.points ? Number(form.points) : null,
            rubricScores: scores,
            feedback: form.feedback || null,
            returnForRedo: redo,
          },
        }),
      redo ? "Returned for redo." : "Marked.",
    );
    if (r) {
      setOpen(null);
      void load();
    }
  }

  return (
    <div className="space-y-4">
      <button
        type="button"
        onClick={onBack}
        className="text-ink-muted hover:text-ink inline-flex items-center gap-1 text-sm"
      >
        <ArrowLeft className="size-4" aria-hidden /> Homework
      </button>
      <Card className="space-y-2">
        <h2 className="text-xl font-bold">{hw.title}</h2>
        <p className="text-ink-muted text-sm">
          {hw.section}
          {hw.subject ? ` · ${hw.subject}` : ""} · due {day(hw.dueOn)}
          {hw.createdBy ? ` · by ${hw.createdBy}` : ""}
        </p>
        {hw.details && <p className="text-sm whitespace-pre-line">{hw.details}</p>}
        {hw.files.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {hw.files.map((f) => (
              <Btn key={f.id} size="sm" onClick={() => void openUrl(`homework/files/${f.id}/url`)}>
                <Paperclip className="size-3.5" aria-hidden /> {f.name}
              </Btn>
            ))}
          </div>
        )}
        <Btn
          size="sm"
          variant="ghost"
          onClick={async () => {
            const r = await run(
              "del",
              () => sb(`homework/${hw.id}`, { method: "DELETE" }).then(() => true),
              "Homework deleted.",
            );
            if (r) onBack();
          }}
        >
          <Trash2 className="size-4" aria-hidden /> Delete homework
        </Btn>
      </Card>
      {!hw.allowSubmissions ? (
        <p className="text-ink-muted text-sm">This homework is not handed in online.</p>
      ) : !rows ? (
        <Loading />
      ) : (
        <ul className="divide-y divide-black/5 rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
          {rows.map((s) => (
            <li key={s.studentId} className="space-y-2 p-4">
              <div className="flex flex-wrap items-center gap-3">
                <span className="min-w-[10rem] flex-1 font-medium">{s.studentName}</span>
                {s.late && <Badge tone="amber">Late</Badge>}
                <Badge
                  tone={
                    s.status === "GRADED"
                      ? "green"
                      : s.status === "SUBMITTED"
                        ? "blue"
                        : s.status === "RETURNED"
                          ? "amber"
                          : "gray"
                  }
                >
                  {s.status === "GRADED"
                    ? `Marked${s.points != null ? ` · ${s.points}` : ""}`
                    : s.status === "SUBMITTED"
                      ? "Handed in"
                      : s.status === "RETURNED"
                        ? "Returned for redo"
                        : "Not handed in"}
                </Badge>
                {s.id && ov.submissions && (
                  <Btn
                    size="sm"
                    onClick={() => {
                      setOpen(open === s.id ? null : s.id!);
                      setForm({
                        points: s.points?.toString() ?? "",
                        feedback: s.feedback ?? "",
                        scores: Object.fromEntries(
                          Object.entries(s.rubricScores).map(([k, v]) => [k, String(v)]),
                        ),
                      });
                    }}
                  >
                    {open === s.id ? "Close" : "Open"}
                  </Btn>
                )}
              </div>
              {open === s.id && (
                <div className="space-y-3 rounded-xl bg-black/[0.02] p-3">
                  {s.text && <p className="text-sm whitespace-pre-line">{s.text}</p>}
                  {s.fileName && (
                    <Btn
                      size="sm"
                      onClick={() => void openUrl(`homework/submissions/${s.id}/file`)}
                    >
                      <Paperclip className="size-3.5" aria-hidden /> {s.fileName}
                    </Btn>
                  )}
                  {hw.rubric.length > 0 ? (
                    <div className="grid gap-2 sm:grid-cols-2">
                      {hw.rubric.map((c) => (
                        <label key={c.criterion} className="text-sm">
                          {c.criterion} (/{c.points}){" "}
                          <input
                            type="number"
                            min={0}
                            max={c.points}
                            step={0.5}
                            className={`${inputCls} inline-block w-20!`}
                            value={form.scores[c.criterion] ?? ""}
                            onChange={(e) =>
                              setForm({
                                ...form,
                                scores: { ...form.scores, [c.criterion]: e.target.value },
                              })
                            }
                          />
                        </label>
                      ))}
                    </div>
                  ) : (
                    <label className="text-sm">
                      Points{hw.maxPoints ? ` (/${hw.maxPoints})` : ""}{" "}
                      <input
                        type="number"
                        min={0}
                        step={0.5}
                        className={`${inputCls} inline-block w-24!`}
                        value={form.points}
                        onChange={(e) => setForm({ ...form, points: e.target.value })}
                      />
                    </label>
                  )}
                  <textarea
                    rows={2}
                    aria-label="Feedback"
                    placeholder="Feedback for the child and parent"
                    className={inputCls}
                    value={form.feedback}
                    maxLength={2000}
                    onChange={(e) => setForm({ ...form, feedback: e.target.value })}
                  />
                  <div className="flex gap-2">
                    <Btn
                      size="sm"
                      variant="success"
                      busy={busy === s.id}
                      onClick={() => void grade(s, false)}
                    >
                      Save mark
                    </Btn>
                    <Btn
                      size="sm"
                      disabled={!form.feedback.trim()}
                      onClick={() => void grade(s, true)}
                    >
                      Return for redo
                    </Btn>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
