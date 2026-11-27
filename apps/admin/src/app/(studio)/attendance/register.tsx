"use client";

import { useCallback, useEffect, useState } from "react";
import { Download } from "lucide-react";
import { sb } from "@/lib/studio";
import { Badge, Btn, Empty, Loading, inputCls } from "@/components/kit";
import { STATUS, dayLabel, todayIst, type Concern, type Register, type SectionDay } from "./types";

const CELL: Record<string, string> = {
  P: "text-emerald-700",
  A: "bg-red-50 text-red-700 font-semibold",
  L: "text-amber-700",
  H: "text-indigo-700",
  LV: "text-gray-500",
};

export function RegisterView({ sections }: { sections: SectionDay[] }) {
  const [sectionId, setSectionId] = useState(sections[0]?.id ?? "");
  const [month, setMonth] = useState(todayIst().slice(0, 7));
  const [reg, setReg] = useState<Register | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!sectionId) return;
    setReg(null);
    try {
      setReg(await sb<Register>(`attendance/register?sectionId=${sectionId}&month=${month}`));
      setErr(null);
    } catch (e) {
      setErr((e as Error).message);
    }
  }, [sectionId, month]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  async function csv() {
    const res = await fetch(
      `/api/backend/attendance/register.csv?sectionId=${sectionId}&month=${month}`,
      { cache: "no-store" },
    );
    if (!res.ok) return;
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = `attendance-${reg?.label.replace(/\s+/g, "-") ?? "class"}-${month}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  if (sections.length === 0) return <Empty>No classes to show.</Empty>;
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="mb-1 block text-sm font-medium">Class</span>
          <select
            className={`${inputCls} w-auto!`}
            value={sectionId}
            onChange={(e) => setSectionId(e.target.value)}
          >
            {sections.map((s) => (
              <option key={s.id} value={s.id}>
                {s.label}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium">Month</span>
          <input
            type="month"
            className={`${inputCls} w-auto!`}
            value={month}
            max={todayIst().slice(0, 7)}
            onChange={(e) => e.target.value && setMonth(e.target.value)}
          />
        </label>
        <Btn onClick={() => void csv()} disabled={!reg}>
          <Download className="size-4" aria-hidden /> Download CSV
        </Btn>
      </div>
      {err ? (
        <Empty>{err}</Empty>
      ) : !reg ? (
        <Loading />
      ) : reg.days.length === 0 ? (
        <Empty>No attendance was taken for {reg.label} in this month.</Empty>
      ) : (
        <>
          <p className="text-ink-muted text-sm">
            {reg.days.length} working days · P present, A absent, L late, ½ half day, LV on leave.
            Late counts as present; leave counts as absent for the {reg.minPercent}% minimum.
          </p>
          <div className="overflow-x-auto rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
            <table className="w-full border-collapse text-xs">
              <thead>
                <tr className="text-ink-muted border-b border-black/5">
                  <th
                    scope="col"
                    className="sticky left-0 bg-white px-3 py-2 text-left font-medium"
                  >
                    Student
                  </th>
                  {reg.days.map((d) => (
                    <th
                      key={d}
                      scope="col"
                      className="px-1 py-2 text-center font-medium"
                      title={dayLabel(d)}
                    >
                      {Number(d.slice(8))}
                    </th>
                  ))}
                  <th scope="col" className="px-2 py-2 text-right font-medium">
                    Month
                  </th>
                  <th scope="col" className="px-3 py-2 text-right font-medium">
                    Year
                  </th>
                </tr>
              </thead>
              <tbody>
                {reg.rows.map((r) => (
                  <tr key={r.studentId} className="border-b border-black/5 last:border-0">
                    <th
                      scope="row"
                      className="sticky left-0 bg-white px-3 py-1.5 text-left font-medium whitespace-nowrap"
                    >
                      <span className="text-ink-muted mr-2 tabular-nums">{r.rollNo ?? ""}</span>
                      {r.name}
                    </th>
                    {reg.days.map((d) => {
                      const s = r.marks[d];
                      return (
                        <td
                          key={d}
                          className={`px-1 py-1.5 text-center ${s ? CELL[s] : ""}`}
                          title={s ? STATUS[s].label : "Not marked"}
                        >
                          {s ? STATUS[s].short : "·"}
                        </td>
                      );
                    })}
                    <td className="px-2 py-1.5 text-right tabular-nums">
                      {r.percent != null ? `${r.percent}%` : "–"}
                    </td>
                    <td className="px-3 py-1.5 text-right tabular-nums">
                      {r.yearPercent != null ? (
                        r.belowMinimum ? (
                          <Badge tone="red">{r.yearPercent}%</Badge>
                        ) : (
                          `${r.yearPercent}%`
                        )
                      ) : (
                        "–"
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}

export function ConcernsView() {
  const [rows, setRows] = useState<Concern[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => {
    const t = setTimeout(
      () =>
        void sb<Concern[]>("attendance/concerns")
          .then(setRows)
          .catch((e: Error) => setErr(e.message)),
      0,
    );
    return () => clearTimeout(t);
  }, []);
  if (err) return <Empty>{err}</Empty>;
  if (!rows) return <Loading />;
  return (
    <div className="space-y-4">
      <p className="text-ink-muted text-sm">
        Students absent several days in a row, or below the school&apos;s attendance level this
        year. Follow up with the family or the counsellor; the levels are set under Teachers &amp;
        settings.
      </p>
      {rows.length === 0 ? (
        <Empty>No students need attention right now.</Empty>
      ) : (
        <ul className="divide-y divide-black/5 rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
          {rows.map((c) => (
            <li key={c.studentId} className="flex flex-wrap items-center gap-3 p-4">
              <div className="min-w-[12rem] flex-1">
                <p className="font-semibold">
                  {c.name} <span className="text-ink-muted font-normal">· {c.classLabel}</span>
                </p>
                <p className="text-sm">{c.why}</p>
                {c.lastAbsent && (
                  <p className="text-ink-muted text-xs">Last absent {dayLabel(c.lastAbsent)}</p>
                )}
              </div>
              {c.streak > 0 && <Badge tone="red">{c.streak} days absent</Badge>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
