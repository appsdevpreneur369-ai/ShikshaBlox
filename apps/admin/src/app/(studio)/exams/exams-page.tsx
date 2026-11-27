"use client";

import { useCallback, useEffect, useState } from "react";
import { sb, StudioError } from "@/lib/studio";
import { Card, Empty, Loading, PageHeader } from "@/components/kit";
import { MarksView } from "./marks";
import { ExamsSetup, SubjectsSetup, SettingsSetup } from "./setup";
import { AnalysisView, ReportCardsView } from "./outputs";
import type { Overview } from "./types";

type Tab = "marks" | "reports" | "exams" | "subjects" | "analysis" | "settings";

export function ExamsPage() {
  const [tab, setTab] = useState<Tab>("marks");
  const [ov, setOv] = useState<Overview | null>(null);
  const [blocked, setBlocked] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setOv(await sb<Overview>("exams/overview"));
    } catch (e) {
      if (e instanceof StudioError && e.status === 403) setBlocked(true);
      else setErr((e as Error).message);
    }
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  if (blocked)
    return (
      <div>
        <PageHeader title="Exams" />
        <Card className="max-w-2xl">
          <h2 className="text-lg font-semibold">Exams are not part of your plan yet</h2>
          <p className="text-ink-muted mt-2 text-sm">
            Upgrade to ShikshaBlox Lite or Standard for marks entry, grading and report cards.
          </p>
        </Card>
      </div>
    );
  if (err && !ov) return <Empty>{err}</Empty>;
  if (!ov) return <Loading />;
  const manage = ov.settings.canManage;
  const tabs: [Tab, string][] = [
    ["marks", "Marks"],
    ["reports", "Report cards"],
  ];
  if (manage) tabs.push(["exams", "Exams & timetable"], ["subjects", "Subjects & teachers"]);
  if (manage && ov.settings.analysis) tabs.push(["analysis", "Analysis"]);
  if (manage) tabs.push(["settings", "Grading"]);

  return (
    <div>
      <PageHeader title="Exams" subtitle={`Academic year ${ov.year} · ${ov.exams.length} exams`} />
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
      {tab === "marks" ? (
        <MarksView overview={ov} />
      ) : tab === "reports" ? (
        <ReportCardsView overview={ov} />
      ) : tab === "exams" ? (
        <ExamsSetup overview={ov} onChanged={() => void load()} />
      ) : tab === "subjects" ? (
        <SubjectsSetup overview={ov} />
      ) : tab === "analysis" ? (
        <AnalysisView overview={ov} />
      ) : (
        <SettingsSetup settings={ov.settings} onChanged={() => void load()} />
      )}
    </div>
  );
}
