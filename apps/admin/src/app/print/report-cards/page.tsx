"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { sb } from "@/lib/studio";
import { ReportCard, type ReportCardData } from "../report-card";

function Cards() {
  const sectionId = useSearchParams().get("sectionId") ?? "";
  const [cards, setCards] = useState<ReportCardData[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => {
    const t = setTimeout(
      () =>
        void sb<ReportCardData[]>(`exams/report-cards?sectionId=${sectionId}`)
          .then(setCards)
          .catch((e: Error) => setErr(e.message)),
      0,
    );
    return () => clearTimeout(t);
  }, [sectionId]);
  if (err)
    return (
      <p role="alert" className="p-8 text-red-700">
        {err}
      </p>
    );
  if (!cards) return <p className="p-8 text-gray-600">Preparing report cards…</p>;
  if (!cards.length) return <p className="p-8">No students in this class.</p>;
  return (
    <>
      <div className="mx-auto flex max-w-[780px] items-center justify-between py-4 print:hidden">
        <p className="text-sm text-gray-600">
          {cards.length} report card(s) · {cards[0]?.student.classLabel}
        </p>
        <button
          type="button"
          onClick={() => window.print()}
          className="rounded-full bg-indigo-700 px-4 py-2 text-sm font-medium text-white"
        >
          Print / save as PDF
        </button>
      </div>
      {cards.map((c) => (
        <div key={c.student.id} className="break-after-page">
          <ReportCard r={c} />
        </div>
      ))}
    </>
  );
}

export default function Page() {
  return (
    <main className="min-h-dvh bg-gray-100 px-4 py-4 print:bg-white print:p-0">
      <Suspense>
        <Cards />
      </Suspense>
    </main>
  );
}
