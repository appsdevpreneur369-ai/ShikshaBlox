"use client";

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Download, Plus, Search, ShieldAlert } from "lucide-react";
import { sb } from "@/lib/studio";
import { Badge, Btn, Empty, Loading, inputCls } from "@/components/kit";
import { StudentPanel, NewStudent } from "./student-panel";
import { GENDER, STATUS_LABEL, type Section, type StudentRow } from "./types";

export function StudentList({
  year,
  sections,
  onChanged,
}: {
  year: string;
  sections: Section[];
  onChanged: () => void;
}) {
  const [rows, setRows] = useState<StudentRow[] | null>(null);
  const [sectionId, setSectionId] = useState("");
  const [status, setStatus] = useState("");
  const [q, setQ] = useState("");
  const [sel, setSel] = useState<string | "new" | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const p = new URLSearchParams({ year });
      if (sectionId) p.set("sectionId", sectionId);
      if (status) p.set("status", status);
      if (q) p.set("q", q);
      setRows(await sb<StudentRow[]>(`sis/students?${p}`));
    } catch (e) {
      setErr((e as Error).message);
    }
  }, [year, sectionId, status, q]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 250);
    return () => clearTimeout(t);
  }, [load]);

  async function csv() {
    const p = new URLSearchParams({ year });
    if (sectionId) p.set("sectionId", sectionId);
    if (status) p.set("status", status);
    const res = await fetch(`/api/backend/sis/students.csv?${p}`, { cache: "no-store" });
    if (!res.ok) return;
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = `students-${year}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  if (err) return <Empty>{err}</Empty>;
  const refresh = () => {
    void load();
    onChanged();
  };
  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <select
          className={`${inputCls} sm:w-56`}
          value={sectionId}
          onChange={(e) => setSectionId(e.target.value)}
          aria-label="Class and section"
        >
          <option value="">All classes</option>
          {sections.map((s) => (
            <option key={s.id} value={s.id}>
              {s.label} ({s.students})
            </option>
          ))}
        </select>
        <select
          className={`${inputCls} sm:w-44`}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          aria-label="Status"
        >
          <option value="">Enrolled</option>
          <option value="WITHDRAWN">Withdrawn</option>
          <option value="ALUMNI">Alumni</option>
          <option value="ALL">All</option>
        </select>
        <div className="relative w-full sm:w-64">
          <Search className="text-ink-muted absolute top-2.5 left-3 size-4" aria-hidden />
          <input
            className={`${inputCls} pl-9`}
            placeholder="Name, admission no., parent, phone"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Search students"
          />
        </div>
        <span className="ml-auto flex gap-2">
          <Btn onClick={() => void csv()}>
            <Download className="size-4" aria-hidden /> CSV
          </Btn>
          <Btn variant="primary" onClick={() => setSel("new")} disabled={sections.length === 0}>
            <Plus className="size-4" aria-hidden /> Add student
          </Btn>
        </span>
      </div>
      {sections.length === 0 && (
        <p className="text-ink-muted mb-4 text-sm">
          Set up your classes and sections first (Classes tab).
        </p>
      )}
      {sel === "new" && (
        <NewStudent
          sections={sections}
          onClose={() => setSel(null)}
          onCreated={(id) => {
            refresh();
            setSel(id);
          }}
        />
      )}
      <div className={`grid gap-5 ${sel && sel !== "new" ? "xl:grid-cols-[1fr_480px]" : ""}`}>
        <div className="min-w-0">
          {!rows ? (
            <Loading />
          ) : rows.length === 0 ? (
            <Empty>
              No students here yet. Add them one by one, import a CSV, or enrol admitted applicants
              from Admissions.
            </Empty>
          ) : (
            <div className="overflow-x-auto rounded-2xl bg-white ring-1 ring-black/5">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-ink-muted text-left text-xs">
                    <th className="px-3 py-2 font-medium">Student</th>
                    <th className="px-3 py-2 font-medium">Class</th>
                    <th className="px-3 py-2 font-medium">Parent</th>
                    <th className="px-3 py-2 font-medium">
                      <span className="sr-only">Alerts</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => (
                    <tr
                      key={r.id}
                      onClick={() => setSel(r.id)}
                      className={`cursor-pointer border-t border-black/5 hover:bg-black/[0.02] ${sel === r.id ? "bg-primary/5" : ""}`}
                    >
                      <td className="px-3 py-2.5">
                        <button
                          type="button"
                          className="text-left font-semibold hover:underline"
                          onClick={() => setSel(r.id)}
                        >
                          {r.name}
                        </button>
                        <span className="text-ink-muted block text-xs">
                          {[r.admissionNo, r.gender ? GENDER[r.gender] : null]
                            .filter(Boolean)
                            .join(" · ")}
                        </span>
                      </td>
                      <td className="px-3 py-2.5 whitespace-nowrap">
                        {r.sectionLabel ?? "—"}
                        {r.rollNo != null && (
                          <span className="text-ink-muted block text-xs">Roll {r.rollNo}</span>
                        )}
                      </td>
                      <td className="px-3 py-2.5">
                        {r.guardianName ?? "—"}
                        {r.guardianPhone && (
                          <span className="text-ink-muted block text-xs whitespace-nowrap">
                            {r.guardianPhone}
                          </span>
                        )}
                      </td>
                      <td className="px-3 py-2.5 text-right whitespace-nowrap">
                        {r.medicalAlert && (
                          <AlertTriangle
                            className="mr-1 inline size-4 text-amber-600"
                            aria-label="Medical alert"
                          />
                        )}
                        {r.custodyNote && (
                          <ShieldAlert
                            className="mr-1 inline size-4 text-red-600"
                            aria-label="Pickup restriction"
                          />
                        )}
                        {r.status !== "ENROLLED" && <Badge>{STATUS_LABEL[r.status]}</Badge>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        {sel && sel !== "new" && (
          <StudentPanel
            key={sel}
            id={sel}
            sections={sections}
            onClose={() => setSel(null)}
            onChanged={refresh}
          />
        )}
      </div>
    </div>
  );
}
