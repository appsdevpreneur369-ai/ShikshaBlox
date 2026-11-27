"use client";

import { useEffect, useState } from "react";
import { Printer } from "lucide-react";
import { sb } from "@/lib/studio";
import { Card, Empty, Loading } from "@/components/kit";
import { ExamPicker } from "./marks";
import type { Analysis, Overview, Teachers } from "./types";

type Sec = { id: string; label: string };

/** Classes whose report cards this user may print: all for the exam cell, own classes for class teachers. */
function useSections(manage: boolean) {
  const [list, setList] = useState<Sec[] | null>(null);
  useEffect(() => {
    const t = setTimeout(async () => {
      if (manage) {
        const r = await sb<Teachers>("exams/teachers").catch(() => null);
        setList((r?.sections ?? []).map((s) => ({ id: s.sectionId, label: s.label })));
      } else {
        const r = await sb<{ sections: { id: string; label: string }[] }>(
          "attendance/overview",
        ).catch(() => null);
        setList((r?.sections ?? []).map((s) => ({ id: s.id, label: s.label })));
      }
    }, 0);
    return () => clearTimeout(t);
  }, [manage]);
  return list;
}

export function ReportCardsView({ overview }: { overview: Overview }) {
  const sections = useSections(overview.settings.canManage);
  if (!sections) return <Loading />;
  if (sections.length === 0)
    return <Empty>Report cards are printed by class teachers and the exam cell.</Empty>;
  const published = overview.exams.filter((e) => e.status === "PUBLISHED").length;
  return (
    <div className="space-y-4">
      <p className="text-ink-muted text-sm">
        A report card shows every exam of the year side by side, with grades, the weighted year
        result, co-scholastic grades, remarks and attendance. {published} exam(s) published;
        unpublished exams are marked “draft”. Print or save as PDF from the browser.
      </p>
      <ul className="grid gap-3 sm:grid-cols-3 lg:grid-cols-4">
        {sections.map((s) => (
          <li key={s.id}>
            <a
              href={`/print/report-cards?sectionId=${s.id}`}
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-2 rounded-2xl bg-white p-4 font-medium shadow-sm ring-1 ring-black/5 hover:ring-black/15"
            >
              <Printer className="text-ink-muted size-4" aria-hidden /> {s.label}
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function AnalysisView({ overview }: { overview: Overview }) {
  const done = overview.exams.filter((e) => e.status !== "DRAFT");
  const [examId, setExamId] = useState(done[done.length - 1]?.id ?? "");
  const [rows, setRows] = useState<Analysis[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => {
    if (!examId) return;
    const t = setTimeout(() => {
      setRows(null);
      void sb<Analysis[]>(`exams/${examId}/analysis`)
        .then(setRows)
        .catch((e: Error) => setErr(e.message));
    }, 0);
    return () => clearTimeout(t);
  }, [examId]);
  if (!done.length) return <Empty>No exam has marks yet.</Empty>;
  return (
    <div className="space-y-4">
      <ExamPicker overview={overview} value={examId} onChange={setExamId} />
      {err ? (
        <Empty>{err}</Empty>
      ) : !rows ? (
        <Loading />
      ) : (
        rows
          .filter((a) => a.students > 0)
          .map((a) => (
            <Card key={a.sectionId}>
              <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
                <h2 className="font-semibold">{a.section}</h2>
                <p className="text-ink-muted text-sm">
                  {a.students} students · class average {a.classAverage ?? "–"}%
                </p>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-ink-muted border-b border-black/5 text-left text-xs">
                      <th scope="col" className="py-1.5 font-medium">
                        Subject
                      </th>
                      <th scope="col" className="py-1.5 text-right font-medium">
                        Average
                      </th>
                      <th scope="col" className="py-1.5 text-right font-medium">
                        Highest
                      </th>
                      <th scope="col" className="py-1.5 text-right font-medium">
                        Lowest
                      </th>
                      <th scope="col" className="py-1.5 text-right font-medium">
                        Passed
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {a.subjects.map((s) => (
                      <tr key={s.subject} className="border-b border-black/5 last:border-0">
                        <td className="py-1.5">{s.subject}</td>
                        <td className="py-1.5 text-right tabular-nums">{s.average ?? "–"}%</td>
                        <td className="py-1.5 text-right tabular-nums">{s.highest ?? "–"}%</td>
                        <td className="py-1.5 text-right tabular-nums">{s.lowest ?? "–"}%</td>
                        <td className="py-1.5 text-right tabular-nums">
                          {s.passed}/{s.withResult}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {a.toppers.length > 0 && (
                <p className="mt-2 text-sm">
                  Top: {a.toppers.map((t) => `${t.name} (${t.percent}%)`).join(", ")}
                </p>
              )}
            </Card>
          ))
      )}
    </div>
  );
}
