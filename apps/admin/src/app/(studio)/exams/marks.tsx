"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowLeft, MessageSquareText } from "lucide-react";
import { sb } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Loading, inputCls, useAction } from "@/components/kit";
import { ENTRY_LABEL, type Overview, type Remark, type Row, type Sheet, type Task } from "./types";

const TONE = { OPEN: "gray", SUBMITTED: "amber", APPROVED: "green" } as const;

export function ExamPicker({
  overview,
  value,
  onChange,
  includeDraft = false,
}: {
  overview: Overview;
  value: string;
  onChange: (id: string) => void;
  includeDraft?: boolean;
}) {
  const list = overview.exams.filter((e) => includeDraft || e.status !== "DRAFT");
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium">Exam</span>
      <select
        className={`${inputCls} w-auto!`}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        {list.map((e) => (
          <option key={e.id} value={e.id}>
            {e.name}
            {e.status === "PUBLISHED" ? " (published)" : e.status === "DRAFT" ? " (draft)" : ""}
          </option>
        ))}
      </select>
    </label>
  );
}

export function MarksView({ overview }: { overview: Overview }) {
  const open = overview.exams.filter((e) => e.status !== "DRAFT");
  const [examId, setExamId] = useState(
    (open.find((e) => e.status === "MARKS_OPEN") ?? open[open.length - 1])?.id ?? "",
  );
  const [tasks, setTasks] = useState<Task[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [sheet, setSheet] = useState<{ sectionId: string; subjectId: string } | null>(null);
  const [remarks, setRemarks] = useState<{ sectionId: string; section: string } | null>(null);

  const load = useCallback(async () => {
    if (!examId) return;
    try {
      setTasks(await sb<Task[]>(`exams/${examId}/progress`));
      setErr(null);
    } catch (e) {
      setErr((e as Error).message);
    }
  }, [examId]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  if (open.length === 0)
    return (
      <Empty>
        {overview.settings.canManage
          ? "No exam is open for marks yet. Create one under Exams & timetable and open it."
          : "No exam is open for marks yet."}
      </Empty>
    );
  if (sheet)
    return (
      <MarksSheet
        examId={examId}
        {...sheet}
        onBack={() => {
          setSheet(null);
          void load();
        }}
      />
    );
  if (remarks) return <RemarksSheet examId={examId} {...remarks} onBack={() => setRemarks(null)} />;

  const sections = [...new Map((tasks ?? []).map((t) => [t.sectionId, t.section])).entries()];
  return (
    <div className="space-y-4">
      <ExamPicker overview={overview} value={examId} onChange={setExamId} />
      {err ? (
        <Empty>{err}</Empty>
      ) : !tasks ? (
        <Loading />
      ) : tasks.length === 0 ? (
        <Empty>
          You have no subjects to enter for this exam. The exam cell assigns subject teachers.
        </Empty>
      ) : (
        sections.map(([sid, label]) => (
          <Card key={sid}>
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <h2 className="font-semibold">{label}</h2>
              <Btn
                size="sm"
                variant="ghost"
                onClick={() => setRemarks({ sectionId: sid, section: label })}
              >
                <MessageSquareText className="size-4" aria-hidden /> Remarks
              </Btn>
            </div>
            <ul className="divide-y divide-black/5">
              {tasks
                .filter((t) => t.sectionId === sid)
                .map((t) => (
                  <li key={t.subjectId}>
                    <button
                      type="button"
                      onClick={() => setSheet({ sectionId: t.sectionId, subjectId: t.subjectId })}
                      className="flex w-full flex-wrap items-center gap-3 py-2.5 text-left hover:bg-black/[0.02]"
                    >
                      <span className="min-w-[10rem] flex-1 font-medium">
                        {t.subject}
                        {t.kind === "CO_SCHOLASTIC" && (
                          <span className="text-ink-muted text-xs font-normal">
                            {" "}
                            · co-scholastic
                          </span>
                        )}
                      </span>
                      <span className="text-ink-muted text-sm tabular-nums">
                        {t.entered}/{t.students}
                      </span>
                      <Badge
                        tone={t.entered === 0 && t.status === "OPEN" ? "gray" : TONE[t.status]}
                      >
                        {t.entered === 0 && t.status === "OPEN"
                          ? "Not started"
                          : ENTRY_LABEL[t.status]}
                      </Badge>
                    </button>
                  </li>
                ))}
            </ul>
          </Card>
        ))
      )}
    </div>
  );
}

type Draft = Record<string, { marks: Record<string, string>; grade: string; code: string }>;

function draftOf(s: Sheet): Draft {
  const d: Draft = {};
  for (const r of s.rows) {
    const marks: Record<string, string> = {};
    s.components.forEach(
      (c) => (marks[c.key] = r.marks?.[c.key] != null ? String(r.marks[c.key]) : ""),
    );
    d[r.studentId] = { marks, grade: r.grade ?? "", code: r.code ?? "" };
  }
  return d;
}

function MarksSheet({
  examId,
  sectionId,
  subjectId,
  onBack,
}: {
  examId: string;
  sectionId: string;
  subjectId: string;
  onBack: () => void;
}) {
  const [s, setS] = useState<Sheet | null>(null);
  const [d, setD] = useState<Draft>({});
  const [note, setNote] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const { busy, run } = useAction();
  const q = `sectionId=${sectionId}&subjectId=${subjectId}`;

  const take = (x: Sheet) => {
    setS(x);
    setD(draftOf(x));
  };
  useEffect(() => {
    const t = setTimeout(
      () =>
        void sb<Sheet>(`exams/${examId}/marks?${q}`)
          .then(take)
          .catch((e: Error) => setErr(e.message)),
      0,
    );
    return () => clearTimeout(t);
  }, [examId, q]);

  const max = useMemo(() => (s ? s.components.reduce((n, c) => n + c.max, 0) : 0), [s]);
  if (err) return <Empty>{err}</Empty>;
  if (!s) return <Loading />;
  const co = s.kind === "CO_SCHOLASTIC";

  function rowTotal(r: Row) {
    const x = d[r.studentId];
    if (!x || x.code) return x?.code || "";
    const vals = s!.components.map((c) => x.marks[c.key]);
    if (vals.some((v) => v === "" || v == null)) return "";
    const t = vals.reduce((n, v) => n + Number(v), 0);
    return `${t} (${Math.round((t * 1000) / max) / 10}%)`;
  }

  async function save() {
    const rows = s!.rows.map((r) => {
      const x = d[r.studentId]!;
      const marks: Record<string, number> = {};
      Object.entries(x.marks).forEach(([k, v]) => {
        if (v !== "") marks[k] = Number(v);
      });
      return { studentId: r.studentId, marks, grade: x.grade || null, code: x.code || null };
    });
    const r = await run(
      "save",
      () => sb<Sheet>(`exams/${examId}/marks?${q}`, { method: "PUT", body: rows }),
      "Marks saved.",
    );
    if (r) take(r);
  }
  async function act(path: string, body?: unknown, ok?: string) {
    const r = await run(
      path,
      () => sb<Sheet>(`exams/${examId}/marks/${path}?${q}`, { method: "POST", body }),
      ok,
    );
    if (r) {
      take(r);
      setNote("");
    }
  }

  return (
    <div className="space-y-4 pb-24">
      <button
        type="button"
        onClick={onBack}
        className="text-ink-muted hover:text-ink inline-flex items-center gap-1 text-sm"
      >
        <ArrowLeft className="size-4" aria-hidden /> All subjects
      </button>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold">
            {s.subject} · {s.section}
          </h2>
          <p className="text-ink-muted text-sm">
            {s.exam}
            {!co && ` · out of ${max}`}
            {co && ` · grades ${s.coGrades.join(", ")}`}
          </p>
        </div>
        <Badge tone={TONE[s.entryStatus]}>{ENTRY_LABEL[s.entryStatus]}</Badge>
      </div>
      {s.note && s.entryStatus === "OPEN" && (
        <p className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900">
          Sent back by the exam cell: “{s.note}”
        </p>
      )}
      {!s.editable && (
        <p className="rounded-xl bg-gray-100 px-4 py-3 text-sm">
          {s.examStatus === "PUBLISHED"
            ? "Results are published, so these marks are locked."
            : s.entryStatus === "APPROVED"
              ? "These marks are approved and locked."
              : "These marks are submitted and waiting for the exam cell."}
        </p>
      )}
      <div className="overflow-x-auto rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-ink-muted border-b border-black/5 text-left text-xs">
              <th scope="col" className="px-3 py-2 font-medium">
                Student
              </th>
              {co ? (
                <th scope="col" className="px-2 py-2 font-medium">
                  Grade
                </th>
              ) : (
                s.components.map((c) => (
                  <th key={c.key} scope="col" className="px-2 py-2 font-medium">
                    {c.label} <span className="font-normal">/{c.max}</span>
                  </th>
                ))
              )}
              <th scope="col" className="px-2 py-2 font-medium">
                AB / ML
              </th>
              {!co && (
                <th scope="col" className="px-3 py-2 text-right font-medium">
                  Total
                </th>
              )}
            </tr>
          </thead>
          <tbody>
            {s.rows.map((r) => {
              const x = d[r.studentId]!;
              return (
                <tr key={r.studentId} className="border-b border-black/5 last:border-0">
                  <th scope="row" className="px-3 py-1.5 text-left font-medium whitespace-nowrap">
                    <span className="text-ink-muted mr-2 tabular-nums">{r.rollNo ?? ""}</span>
                    {r.name}
                  </th>
                  {co ? (
                    <td className="px-2 py-1.5">
                      <select
                        aria-label={`Grade for ${r.name}`}
                        disabled={!s.editable || !!x.code}
                        className={`${inputCls} w-20! py-1`}
                        value={x.grade}
                        onChange={(e) =>
                          setD({ ...d, [r.studentId]: { ...x, grade: e.target.value } })
                        }
                      >
                        <option value="">–</option>
                        {s.coGrades.map((g) => (
                          <option key={g}>{g}</option>
                        ))}
                      </select>
                    </td>
                  ) : (
                    s.components.map((c) => (
                      <td key={c.key} className="px-2 py-1.5">
                        <input
                          aria-label={`${c.label} for ${r.name}`}
                          inputMode="decimal"
                          type="number"
                          min={0}
                          max={c.max}
                          step={0.5}
                          disabled={!s.editable || !!x.code}
                          className={`${inputCls} w-20! py-1 tabular-nums ${x.marks[c.key] !== "" && Number(x.marks[c.key]) > c.max ? "border-red-500" : ""}`}
                          value={x.marks[c.key] ?? ""}
                          onChange={(e) =>
                            setD({
                              ...d,
                              [r.studentId]: {
                                ...x,
                                marks: { ...x.marks, [c.key]: e.target.value },
                              },
                            })
                          }
                        />
                      </td>
                    ))
                  )}
                  <td className="px-2 py-1.5">
                    <select
                      aria-label={`Absent or medical for ${r.name}`}
                      disabled={!s.editable}
                      className={`${inputCls} w-20! py-1`}
                      value={x.code}
                      onChange={(e) =>
                        setD({ ...d, [r.studentId]: { ...x, code: e.target.value } })
                      }
                    >
                      <option value="">–</option>
                      <option value="AB">AB</option>
                      <option value="ML">ML</option>
                    </select>
                  </td>
                  {!co && <td className="px-3 py-1.5 text-right tabular-nums">{rowTotal(r)}</td>}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <p className="text-ink-muted text-xs">
        AB = absent (counts as zero). ML = medical leave (left out of the total).
      </p>

      {s.canManage && s.moderation && s.entryStatus === "SUBMITTED" && (
        <Card className="space-y-3">
          <p className="text-sm font-medium">
            Check these marks and approve them, or send them back to the teacher.
          </p>
          <input
            className={inputCls}
            placeholder="Note for the teacher (needed to send back)"
            value={note}
            maxLength={300}
            onChange={(e) => setNote(e.target.value)}
          />
          <div className="flex gap-2">
            <Btn
              variant="success"
              busy={busy === "moderate"}
              onClick={() => void act("moderate", { approve: true }, "Approved.")}
            >
              Approve
            </Btn>
            <Btn
              variant="danger"
              disabled={!note.trim()}
              onClick={() => void act("moderate", { approve: false, note }, "Sent back.")}
            >
              Send back
            </Btn>
          </div>
        </Card>
      )}
      {s.canManage &&
        s.moderation &&
        s.entryStatus === "APPROVED" &&
        s.examStatus !== "PUBLISHED" && (
          <Btn
            size="sm"
            onClick={() =>
              void act(
                "moderate",
                { approve: false, note: "Reopened by the exam cell" },
                "Reopened for changes.",
              )
            }
          >
            Reopen for changes
          </Btn>
        )}

      {s.editable && (
        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-black/5 bg-white/95 backdrop-blur">
          <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-end gap-2 px-4 py-3">
            <Btn variant="primary" busy={busy === "save"} onClick={() => void save()}>
              Save marks
            </Btn>
            {s.moderation && s.entryStatus === "OPEN" && (
              <Btn
                busy={busy === "submit"}
                onClick={() => void act("submit", undefined, "Submitted for approval.")}
              >
                Submit for approval
              </Btn>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function RemarksSheet({
  examId,
  sectionId,
  section,
  onBack,
}: {
  examId: string;
  sectionId: string;
  section: string;
  onBack: () => void;
}) {
  const [rows, setRows] = useState<Remark[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const { busy, run } = useAction();
  useEffect(() => {
    const t = setTimeout(
      () =>
        void sb<Remark[]>(`exams/${examId}/remarks?sectionId=${sectionId}`)
          .then(setRows)
          .catch((e: Error) => setErr(e.message)),
      0,
    );
    return () => clearTimeout(t);
  }, [examId, sectionId]);
  if (err) return <Empty>{err}</Empty>;
  if (!rows) return <Loading />;
  return (
    <div className="space-y-4">
      <button
        type="button"
        onClick={onBack}
        className="text-ink-muted hover:text-ink inline-flex items-center gap-1 text-sm"
      >
        <ArrowLeft className="size-4" aria-hidden /> Back
      </button>
      <h2 className="text-xl font-bold">Remarks · {section}</h2>
      <ul className="space-y-3">
        {rows.map((r, i) => (
          <li key={r.studentId}>
            <label className="block">
              <span className="mb-1 block text-sm font-medium">{r.name}</span>
              <textarea
                rows={2}
                maxLength={500}
                className={inputCls}
                value={r.remark ?? ""}
                onChange={(e) =>
                  setRows(rows.map((x, j) => (j === i ? { ...x, remark: e.target.value } : x)))
                }
              />
            </label>
          </li>
        ))}
      </ul>
      <Btn
        variant="primary"
        busy={busy === "save"}
        onClick={async () => {
          const r = await run(
            "save",
            () =>
              sb<Remark[]>(`exams/${examId}/remarks?sectionId=${sectionId}`, {
                method: "PUT",
                body: rows.map((x) => ({ studentId: x.studentId, remark: x.remark ?? "" })),
              }),
            "Remarks saved.",
          );
          if (r) setRows(r);
        }}
      >
        Save remarks
      </Btn>
    </div>
  );
}
