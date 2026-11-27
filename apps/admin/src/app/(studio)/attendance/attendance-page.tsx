"use client";

import { useCallback, useEffect, useState } from "react";
import { CheckCircle2, ChevronRight, CircleDashed } from "lucide-react";
import { sb, StudioError } from "@/lib/studio";
import { Badge, Card, Empty, Loading, PageHeader, inputCls } from "@/components/kit";
import { RollCall, useOfflineQueue } from "./roll-call";
import { LeaveView, CorrectionsView } from "./leave";
import { RegisterView, ConcernsView } from "./register";
import { SetupView } from "./setup";
import { dayLabel, todayIst, type Overview } from "./types";

type Tab = "today" | "leave" | "corrections" | "registers" | "concerns" | "setup";

export function AttendancePage() {
  const [tab, setTab] = useState<Tab>("today");
  const [date, setDate] = useState(todayIst());
  const [ov, setOv] = useState<Overview | null>(null);
  const [blocked, setBlocked] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [open, setOpen] = useState<{ sectionId: string; period: number } | null>(null);

  const load = useCallback(async () => {
    try {
      setOv(await sb<Overview>(`attendance/overview?date=${date}`));
    } catch (e) {
      if (e instanceof StudioError && e.status === 403) setBlocked(true);
      else setErr((e as Error).message);
    }
  }, [date]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);
  const pending = useOfflineQueue(load);

  if (blocked)
    return (
      <div>
        <PageHeader title="Attendance" />
        <Card className="max-w-2xl">
          <h2 className="text-lg font-semibold">Attendance is not part of your plan yet</h2>
          <p className="text-ink-muted mt-2 text-sm">
            Upgrade to ShikshaBlox Lite or Standard for daily attendance on a phone, absence alerts
            to parents, leave applications and monthly registers.
          </p>
        </Card>
      </div>
    );
  if (err && !ov) return <Empty>{err}</Empty>;
  if (!ov) return <Loading />;

  if (open)
    return (
      <RollCall
        sectionId={open.sectionId}
        date={date}
        period={open.period}
        settings={ov.settings}
        onBack={() => {
          setOpen(null);
          void load();
        }}
      />
    );

  const tabs: [Tab, string, number?][] = [
    ["today", "Roll call"],
    ["leave", "Leave", ov.pendingLeave],
    ["corrections", "Corrections", ov.pendingCorrections],
    ["registers", "Registers"],
  ];
  if (ov.settings.concernsAllowed) tabs.push(["concerns", "Needs attention"]);
  if (ov.canManage) tabs.push(["setup", "Teachers & settings"]);
  const marked = ov.sections.filter((s) => s.marked).length;

  return (
    <div>
      <PageHeader
        title="Attendance"
        subtitle={`Academic year ${ov.year} · ${marked} of ${ov.sections.length} classes marked on ${dayLabel(date)}`}
      />
      {pending > 0 && (
        <p role="status" className="mb-4 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900">
          {pending} roll call(s) saved on this device will be sent when you are back online.
        </p>
      )}
      <div className="mb-5 flex flex-wrap gap-2">
        {tabs.map(([k, label, n]) => (
          <button
            key={k}
            type="button"
            onClick={() => setTab(k)}
            className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition ${tab === k ? "bg-primary text-on-primary" : "text-ink-muted hover:text-ink bg-white ring-1 ring-black/10"}`}
          >
            {label}
            {n ? (
              <span className="ml-1.5 rounded-full bg-red-600 px-1.5 py-0.5 text-[11px] text-white">
                {n}
              </span>
            ) : null}
          </button>
        ))}
      </div>

      {tab === "today" ? (
        <div className="space-y-4">
          <div className="flex flex-wrap items-end gap-3">
            <label className="block">
              <span className="mb-1 block text-sm font-medium">Date</span>
              <input
                type="date"
                className={`${inputCls} w-auto!`}
                value={date}
                max={todayIst()}
                onChange={(e) => e.target.value && setDate(e.target.value)}
              />
            </label>
          </div>
          {ov.sections.length === 0 ? (
            <Empty>
              {ov.canManage
                ? "No classes yet. Add classes under Students, then assign teachers under Teachers & settings."
                : "You are not assigned to a class yet. Please ask the school office."}
            </Empty>
          ) : (
            <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {ov.sections.map((s) => (
                <li key={s.id}>
                  <button
                    type="button"
                    onClick={() =>
                      setOpen({ sectionId: s.id, period: ov.settings.mode === "PERIOD" ? 1 : 0 })
                    }
                    className="flex w-full items-center gap-3 rounded-2xl bg-white p-4 text-left shadow-sm ring-1 ring-black/5 transition hover:ring-black/15"
                  >
                    {s.marked ? (
                      <CheckCircle2 className="size-6 shrink-0 text-emerald-600" aria-hidden />
                    ) : (
                      <CircleDashed className="text-ink-muted size-6 shrink-0" aria-hidden />
                    )}
                    <span className="min-w-0 flex-1">
                      <span className="block font-semibold">{s.label}</span>
                      <span className="text-ink-muted block truncate text-xs">
                        {s.marked
                          ? `${s.present} present · ${s.absent} absent${s.late ? ` · ${s.late} late` : ""}${s.onLeave ? ` · ${s.onLeave} on leave` : ""}`
                          : `${s.students} students · not marked`}
                      </span>
                      {s.classTeacher && (
                        <span className="text-ink-muted block truncate text-xs">
                          {s.classTeacher}
                        </span>
                      )}
                    </span>
                    {!s.marked && date === todayIst() && <Badge tone="amber">To do</Badge>}
                    <ChevronRight className="text-ink-muted size-4" aria-hidden />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : tab === "leave" ? (
        <LeaveView onChanged={() => void load()} />
      ) : tab === "corrections" ? (
        <CorrectionsView canManage={ov.canManage} onChanged={() => void load()} />
      ) : tab === "registers" ? (
        <RegisterView sections={ov.sections} />
      ) : tab === "concerns" ? (
        <ConcernsView />
      ) : (
        <SetupView settings={ov.settings} onChanged={() => void load()} />
      )}
    </div>
  );
}
