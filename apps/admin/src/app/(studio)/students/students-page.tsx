"use client";

import { useCallback, useEffect, useState } from "react";
import { sb, StudioError } from "@/lib/studio";
import { Card, Loading, PageHeader } from "@/components/kit";
import { StudentList } from "./student-list";
import { ClassesView, PromotionView, SetupView } from "./setup";
import type { Section, Settings } from "./types";

type Tab = "students" | "classes" | "promotion" | "setup";

export function StudentsPage() {
  const [tab, setTab] = useState<Tab>("students");
  const [settings, setSettings] = useState<Settings | null>(null);
  const [sections, setSections] = useState<Section[]>([]);
  const [blocked, setBlocked] = useState(false);

  const load = useCallback(async () => {
    try {
      const s = await sb<Settings>("sis/settings");
      setSettings(s);
      setSections(await sb<Section[]>(`sis/sections?year=${s.currentYear}`));
    } catch (e) {
      if (e instanceof StudioError && e.status === 403) setBlocked(true);
    }
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  if (blocked) {
    return (
      <div>
        <PageHeader title="Students" />
        <Card className="max-w-2xl">
          <h2 className="text-lg font-semibold">Student records are not part of your plan yet</h2>
          <p className="text-ink-muted mt-2 text-sm">
            Upgrade to ShikshaBlox Lite or Standard for student and guardian records, classes,
            promotion and UDISE+ readiness.
          </p>
        </Card>
      </div>
    );
  }
  if (!settings) return <Loading />;

  return (
    <div>
      <PageHeader
        title="Students"
        subtitle={`Academic year ${settings.currentYear} · ${sections.reduce((n, s) => n + s.students, 0)} students in ${sections.length} sections`}
      />
      <div className="mb-5 flex flex-wrap gap-2">
        {(
          [
            ["students", "Students"],
            ["classes", "Classes"],
            ["promotion", "Year-end promotion"],
            ["setup", "Import & settings"],
          ] as [Tab, string][]
        ).map(([k, label]) => (
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
      {tab === "students" ? (
        <StudentList
          year={settings.currentYear}
          sections={sections}
          onChanged={() => void load()}
        />
      ) : tab === "classes" ? (
        <ClassesView
          year={settings.currentYear}
          sections={sections}
          onChanged={() => void load()}
        />
      ) : tab === "promotion" ? (
        <PromotionView key={settings.currentYear} onDone={() => void load()} />
      ) : (
        <SetupView settings={settings} onChanged={() => void load()} />
      )}
    </div>
  );
}
