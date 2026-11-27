"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { sb } from "@/lib/studio";

type Data = {
  school: string;
  exam: string;
  section: string;
  instructions: string[];
  tickets: {
    student: {
      id: string;
      name: string;
      admissionNo: string;
      classLabel: string;
      rollNo?: number | null;
      dateOfBirth?: string | null;
    };
    papers: {
      subject: string;
      date?: string | null;
      startTime?: string | null;
      durationMin?: number | null;
    }[];
  }[];
};

const day = (d?: string | null) =>
  d
    ? new Intl.DateTimeFormat("en-IN", { weekday: "short", day: "numeric", month: "short" }).format(
        new Date(`${d}T00:00:00`),
      )
    : "—";
const time = (t?: string | null) => {
  if (!t) return "—";
  const [h, m] = t.split(":").map(Number);
  return new Intl.DateTimeFormat("en-IN", { hour: "numeric", minute: "2-digit" }).format(
    new Date(2000, 0, 1, h, m),
  );
};

function Tickets() {
  const q = useSearchParams();
  const [data, setData] = useState<Data | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const examId = q.get("examId") ?? "";
  const sectionId = q.get("sectionId") ?? "";
  useEffect(() => {
    const t = setTimeout(
      () =>
        void sb<Data>(`exams/${examId}/hall-tickets?sectionId=${sectionId}`)
          .then(setData)
          .catch((e: Error) => setErr(e.message)),
      0,
    );
    return () => clearTimeout(t);
  }, [examId, sectionId]);
  if (err)
    return (
      <p role="alert" className="p-8 text-red-700">
        {err}
      </p>
    );
  if (!data) return <p className="p-8 text-gray-600">Preparing hall tickets…</p>;
  return (
    <>
      <div className="mx-auto flex max-w-[780px] items-center justify-between py-4 print:hidden">
        <p className="text-sm text-gray-600">
          {data.tickets.length} hall ticket(s) · {data.section} · {data.exam}
        </p>
        <button
          type="button"
          onClick={() => window.print()}
          className="rounded-full bg-indigo-700 px-4 py-2 text-sm font-medium text-white"
        >
          Print
        </button>
      </div>
      {data.tickets.map((t, i) => (
        <article
          key={t.student.id}
          className={`mx-auto mb-6 max-w-[780px] rounded-lg border-2 border-gray-800 bg-white p-6 text-[13px] print:mb-4 ${i % 2 === 1 ? "break-after-page" : ""}`}
        >
          <header className="border-b border-gray-400 pb-2 text-center">
            <h1 className="text-lg font-bold">{data.school}</h1>
            <p className="font-semibold">Hall ticket · {data.exam}</p>
          </header>
          <dl className="mt-3 grid grid-cols-2 gap-x-6 gap-y-1">
            <div className="flex gap-2">
              <dt className="text-gray-500">Name</dt>
              <dd className="font-semibold">{t.student.name}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="text-gray-500">Class</dt>
              <dd>{t.student.classLabel}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="text-gray-500">Roll no.</dt>
              <dd>{t.student.rollNo ?? "—"}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="text-gray-500">Admission no.</dt>
              <dd>{t.student.admissionNo}</dd>
            </div>
          </dl>
          <table className="mt-3 w-full border-collapse">
            <thead>
              <tr className="bg-gray-100">
                <th scope="col" className="border border-gray-300 px-2 py-1 text-left">
                  Date
                </th>
                <th scope="col" className="border border-gray-300 px-2 py-1 text-left">
                  Time
                </th>
                <th scope="col" className="border border-gray-300 px-2 py-1 text-left">
                  Subject
                </th>
                <th scope="col" className="border border-gray-300 px-2 py-1 text-left">
                  Invigilator&apos;s sign
                </th>
              </tr>
            </thead>
            <tbody>
              {t.papers.map((p) => (
                <tr key={p.subject}>
                  <td className="border border-gray-300 px-2 py-1">{day(p.date)}</td>
                  <td className="border border-gray-300 px-2 py-1">
                    {time(p.startTime)}
                    {p.durationMin ? ` · ${p.durationMin} min` : ""}
                  </td>
                  <td className="border border-gray-300 px-2 py-1">{p.subject}</td>
                  <td className="border border-gray-300 px-2 py-1" />
                </tr>
              ))}
            </tbody>
          </table>
          <ul className="mt-3 list-disc pl-5 text-[12px] text-gray-700">
            {data.instructions.map((x) => (
              <li key={x}>{x}</li>
            ))}
          </ul>
          <footer className="mt-8 grid grid-cols-2 gap-6 text-center text-[12px]">
            <p className="border-t border-gray-400 pt-1">Student&apos;s signature</p>
            <p className="border-t border-gray-400 pt-1">Principal / exam cell</p>
          </footer>
        </article>
      ))}
    </>
  );
}

export default function Page() {
  return (
    <main className="min-h-dvh bg-gray-100 px-4 py-4 print:bg-white print:p-0">
      <Suspense>
        <Tickets />
      </Suspense>
    </main>
  );
}
