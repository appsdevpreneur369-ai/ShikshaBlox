"use client";

import type { Component } from "../(studio)/exams/types";

export type Cell = {
  examId: string;
  marks: Record<string, number>;
  total?: number | null;
  max: number;
  percent?: number | null;
  grade?: string | null;
  code?: string | null;
} | null;
export type ReportCardData = {
  school: string;
  board: string;
  academicYear: string;
  student: {
    id: string;
    name: string;
    admissionNo: string;
    classLabel: string;
    rollNo?: number | null;
    dateOfBirth?: string | null;
    guardians?: string | null;
  };
  exams: {
    examId: string;
    name: string;
    term?: string | null;
    weight: number;
    published: boolean;
    components: Component[];
  }[];
  subjects: {
    subject: string;
    cells: Cell[];
    weightedPercent?: number | null;
    weightedGrade?: string | null;
  }[];
  coScholastic: { subject: string; grades: Record<string, string> }[];
  totals: {
    examId: string;
    percent?: number | null;
    grade?: string | null;
    passed?: boolean | null;
    rank?: number | null;
    of?: number | null;
    remark?: string | null;
  }[];
  weightedPercent?: number | null;
  weightedGrade?: string | null;
  attendancePercent?: number | null;
  daysMarked?: number | null;
  passPercent: number;
  principal?: string | null;
  draft: boolean;
};

const d = (iso?: string | null) =>
  iso
    ? new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(
        new Date(`${iso}T00:00:00`),
      )
    : "—";

/** One A4 report card; used for printing in Studio. */
export function ReportCard({ r }: { r: ReportCardData }) {
  const weighted = r.exams.some((e) => e.weight > 0);
  return (
    <article className="report mx-auto mb-8 max-w-[780px] rounded-lg bg-white p-8 text-[13px] leading-snug text-gray-900 shadow print:mb-0 print:max-w-none print:rounded-none print:p-0 print:shadow-none">
      <header className="border-b-2 border-gray-800 pb-3 text-center">
        <h1 className="text-xl font-bold tracking-tight">{r.school}</h1>
        <p className="text-gray-600">
          Report card · Academic year {r.academicYear} ·{" "}
          {r.board === "STATE" ? "State board" : r.board}
        </p>
        {r.draft && (
          <p className="mt-1 font-semibold text-red-700">
            DRAFT – includes results not yet published
          </p>
        )}
      </header>
      <dl className="mt-3 grid grid-cols-2 gap-x-6 gap-y-1">
        <div className="flex gap-2">
          <dt className="text-gray-500">Student</dt>
          <dd className="font-semibold">{r.student.name}</dd>
        </div>
        <div className="flex gap-2">
          <dt className="text-gray-500">Class</dt>
          <dd>
            {r.student.classLabel}
            {r.student.rollNo != null ? ` · Roll ${r.student.rollNo}` : ""}
          </dd>
        </div>
        <div className="flex gap-2">
          <dt className="text-gray-500">Admission no.</dt>
          <dd>{r.student.admissionNo}</dd>
        </div>
        <div className="flex gap-2">
          <dt className="text-gray-500">Date of birth</dt>
          <dd>{d(r.student.dateOfBirth)}</dd>
        </div>
        {r.student.guardians && (
          <div className="col-span-2 flex gap-2">
            <dt className="text-gray-500">Parent / guardian</dt>
            <dd>{r.student.guardians}</dd>
          </div>
        )}
      </dl>

      <table className="mt-4 w-full border-collapse">
        <thead>
          <tr className="bg-gray-100">
            <th scope="col" className="border border-gray-300 px-2 py-1 text-left">
              Subject
            </th>
            {r.exams.map((e) => (
              <th
                key={e.examId}
                scope="col"
                className="border border-gray-300 px-2 py-1 text-center"
              >
                {e.name}
                <span className="block text-[11px] font-normal text-gray-600">
                  /{e.components.reduce((n, c) => n + c.max, 0)}
                  {e.weight ? ` · ${e.weight}%` : ""}
                </span>
              </th>
            ))}
            {weighted && (
              <th scope="col" className="border border-gray-300 px-2 py-1 text-center">
                Year
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {r.subjects.map((s) => (
            <tr key={s.subject}>
              <th scope="row" className="border border-gray-300 px-2 py-1 text-left font-medium">
                {s.subject}
              </th>
              {s.cells.map((c, i) => (
                <td key={i} className="border border-gray-300 px-2 py-1 text-center tabular-nums">
                  {!c ? (
                    "–"
                  ) : c.code ? (
                    c.code
                  ) : c.total == null ? (
                    ""
                  ) : (
                    <>
                      {c.total} <span className="text-gray-600">({c.grade})</span>
                    </>
                  )}
                </td>
              ))}
              {weighted && (
                <td className="border border-gray-300 px-2 py-1 text-center font-semibold tabular-nums">
                  {s.weightedPercent != null ? `${s.weightedPercent}% (${s.weightedGrade})` : "–"}
                </td>
              )}
            </tr>
          ))}
          <tr className="bg-gray-50 font-semibold">
            <th scope="row" className="border border-gray-300 px-2 py-1 text-left">
              Overall
            </th>
            {r.totals.map((t) => (
              <td
                key={t.examId}
                className="border border-gray-300 px-2 py-1 text-center tabular-nums"
              >
                {t.percent != null ? `${t.percent}% (${t.grade})` : "–"}
                {t.rank != null && (
                  <span className="block text-[11px] font-normal">
                    Rank {t.rank} of {t.of}
                  </span>
                )}
              </td>
            ))}
            {weighted && (
              <td className="border border-gray-300 px-2 py-1 text-center tabular-nums">
                {r.weightedPercent != null ? `${r.weightedPercent}% (${r.weightedGrade})` : "–"}
              </td>
            )}
          </tr>
          <tr>
            <th scope="row" className="border border-gray-300 px-2 py-1 text-left font-medium">
              Result
            </th>
            {r.totals.map((t) => (
              <td key={t.examId} className="border border-gray-300 px-2 py-1 text-center">
                {t.passed == null ? "–" : t.passed ? "Pass" : "Needs improvement"}
              </td>
            ))}
            {weighted && <td className="border border-gray-300" />}
          </tr>
        </tbody>
      </table>

      {r.coScholastic.length > 0 && (
        <table className="mt-4 w-full border-collapse">
          <thead>
            <tr className="bg-gray-100">
              <th scope="col" className="border border-gray-300 px-2 py-1 text-left">
                Co-scholastic areas
              </th>
              {r.exams.map((e) => (
                <th
                  key={e.examId}
                  scope="col"
                  className="border border-gray-300 px-2 py-1 text-center"
                >
                  {e.name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {r.coScholastic.map((c) => (
              <tr key={c.subject}>
                <th scope="row" className="border border-gray-300 px-2 py-1 text-left font-medium">
                  {c.subject}
                </th>
                {r.exams.map((e) => (
                  <td key={e.examId} className="border border-gray-300 px-2 py-1 text-center">
                    {c.grades[e.examId] ?? "–"}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="mt-4 grid gap-2">
        {r.totals
          .filter((t) => t.remark)
          .map((t) => (
            <p key={t.examId}>
              <span className="font-semibold">
                Class teacher&apos;s remark ({r.exams.find((e) => e.examId === t.examId)?.name}):
              </span>{" "}
              {t.remark}
            </p>
          ))}
        <p>
          <span className="font-semibold">Attendance this year:</span>{" "}
          {r.attendancePercent != null
            ? `${r.attendancePercent}% of ${r.daysMarked} days`
            : "not recorded"}
        </p>
        <p className="text-[11px] text-gray-600">
          Pass mark {r.passPercent}% per subject. AB = absent, ML = medical leave (not counted).
          {weighted ? " Year result is weighted by each exam's share." : ""}
        </p>
      </div>
      <footer className="mt-10 grid grid-cols-3 gap-6 text-center text-[12px]">
        <p className="border-t border-gray-400 pt-1">Class teacher</p>
        <p className="border-t border-gray-400 pt-1">Parent / guardian</p>
        <p className="border-t border-gray-400 pt-1">
          Principal{r.principal ? ` · ${r.principal}` : ""}
        </p>
      </footer>
    </article>
  );
}
