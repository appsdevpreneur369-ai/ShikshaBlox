"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowLeft, BellRing, CloudOff } from "lucide-react";
import { cn } from "@shikshablox/ui";
import { sb, StudioError } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Loading, inputCls, useToast } from "@/components/kit";
import {
  STATUS,
  dayLabel,
  todayIst,
  type MarkInput,
  type SaveResult,
  type Settings,
  type Sheet,
  type SheetInput,
  type Status,
} from "./types";

// ------------------------------------------------------------------ offline support

const QUEUE = "sb-attendance-queue";
const sheetKey = (s: string, d: string, p: number) => `sb-attendance-sheet:${s}:${d}:${p}`;

function readQueue(): SheetInput[] {
  try {
    return JSON.parse(localStorage.getItem(QUEUE) ?? "[]") as SheetInput[];
  } catch {
    return [];
  }
}
function writeQueue(q: SheetInput[]) {
  try {
    localStorage.setItem(QUEUE, JSON.stringify(q));
  } catch {
    /* storage unavailable */
  }
}
function remember(sheet: Sheet) {
  try {
    localStorage.setItem(
      sheetKey(sheet.sectionId, sheet.date, sheet.period),
      JSON.stringify(sheet),
    );
  } catch {
    /* storage unavailable */
  }
}
function recall(s: string, d: string, p: number): Sheet | null {
  try {
    const v = localStorage.getItem(sheetKey(s, d, p));
    return v ? (JSON.parse(v) as Sheet) : null;
  } catch {
    return null;
  }
}
/** A network failure (as opposed to the server refusing the request). */
const offline = (e: unknown) => !(e instanceof StudioError) || !navigator.onLine;

/** Sends roll calls saved while offline as soon as the connection is back; returns how many wait. */
export function useOfflineQueue(onSynced: () => void) {
  const toast = useToast();
  const [count, setCount] = useState(0);
  const flush = useCallback(async () => {
    let q = readQueue();
    setCount(q.length);
    if (!q.length || !navigator.onLine) return;
    let sent = 0;
    while (q.length) {
      try {
        await sb("attendance/sheet", { method: "PUT", body: q[0] });
        sent++;
      } catch (e) {
        if (offline(e)) break;
        toast(`A saved roll call could not be sent: ${(e as Error).message}`, "error");
      }
      q = q.slice(1);
      writeQueue(q);
    }
    setCount(q.length);
    if (sent) {
      toast(`${sent} roll call(s) saved offline have been sent.`);
      onSynced();
    }
  }, [toast, onSynced]);
  useEffect(() => {
    const t = setTimeout(() => void flush(), 0);
    window.addEventListener("online", flush);
    return () => {
      clearTimeout(t);
      window.removeEventListener("online", flush);
    };
  }, [flush]);
  return count;
}

// ------------------------------------------------------------------ roll call

type Draft = Record<string, { status: Status; reason?: string | null; note?: string | null }>;

function draftOf(sheet: Sheet): Draft {
  const d: Draft = {};
  for (const r of sheet.rows) {
    d[r.studentId] = {
      status: r.status ?? (r.leave === "APPROVED" ? "LV" : "P"),
      reason: r.reason ?? null,
      note: r.note ?? null,
    };
  }
  return d;
}

export function RollCall({
  sectionId,
  date,
  period: initialPeriod,
  settings,
  onBack,
}: {
  sectionId: string;
  date: string;
  period: number;
  settings: Settings;
  onBack: () => void;
}) {
  const toast = useToast();
  const [period, setPeriod] = useState(initialPeriod);
  const [sheet, setSheet] = useState<Sheet | null>(null);
  const [draft, setDraft] = useState<Draft>({});
  const [subject, setSubject] = useState("");
  const [why, setWhy] = useState("");
  const [busy, setBusy] = useState(false);
  const [cached, setCached] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const s = await sb<Sheet>(
        `attendance/sheet?sectionId=${sectionId}&date=${date}&period=${period}`,
      );
      remember(s);
      setSheet(s);
      setDraft(draftOf(s));
      setSubject(s.subject ?? "");
      setCached(false);
    } catch (e) {
      const c = offline(e) ? recall(sectionId, date, period) : null;
      if (c) {
        setSheet(c);
        setDraft(draftOf(c));
        setCached(true);
      } else setErr((e as Error).message);
    }
  }, [sectionId, date, period]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  const counts = useMemo(() => {
    const c: Record<Status, number> = { P: 0, A: 0, L: 0, H: 0, LV: 0 };
    Object.values(draft).forEach((m) => c[m.status]++);
    return c;
  }, [draft]);

  if (err) return <Empty>{err}</Empty>;
  if (!sheet) return <Loading />;

  const readOnly = sheet.editMode === "READ_ONLY";
  const correcting = sheet.editMode === "CORRECTION";
  const changed = sheet.rows.filter((r) => {
    const d = draft[r.studentId];
    return d && (d.status !== r.status || (d.reason ?? null) !== (r.reason ?? null));
  }).length;

  function set(id: string, status: Status) {
    setDraft((d) => ({
      ...d,
      [id]: { ...d[id], status, reason: status === "P" ? null : d[id]?.reason },
    }));
  }

  async function save() {
    if (!sheet) return;
    const marks: MarkInput[] = sheet.rows.map((r) => {
      const m = draft[r.studentId] ?? { status: "P" as Status };
      return {
        studentId: r.studentId,
        status: m.status,
        reason: m.reason ?? null,
        note: m.note ?? null,
      };
    });
    const body: SheetInput = {
      sectionId,
      date,
      period,
      subject: subject || null,
      marks,
      correctionReason: why || null,
    };
    setBusy(true);
    try {
      const r = await sb<SaveResult>("attendance/sheet", { method: "PUT", body });
      remember(r.sheet);
      setSheet(r.sheet);
      setDraft(draftOf(r.sheet));
      setWhy("");
      toast(r.message);
      if (!correcting) onBack();
    } catch (e) {
      if (offline(e)) {
        writeQueue([
          ...readQueue().filter(
            (q) => !(q.sectionId === sectionId && q.date === date && q.period === period),
          ),
          body,
        ]);
        toast("No connection. Saved on this device; it will be sent when you are back online.");
        onBack();
      } else toast((e as Error).message, "error");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl pb-28">
      <button
        type="button"
        onClick={onBack}
        className="text-ink-muted hover:text-ink mb-3 inline-flex items-center gap-1 text-sm"
      >
        <ArrowLeft className="size-4" aria-hidden /> All classes
      </button>
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{sheet.label}</h1>
          <p className="text-ink-muted text-sm">
            {dayLabel(date)}
            {sheet.marked && sheet.markedBy
              ? ` · marked by ${sheet.markedBy}`
              : sheet.marked
                ? " · marked"
                : " · not marked yet"}
          </p>
        </div>
        {settings.mode === "PERIOD" && (
          <label className="block">
            <span className="mb-1 block text-sm font-medium">Period</span>
            <select
              className={`${inputCls} w-auto!`}
              value={period}
              onChange={(e) => setPeriod(Number(e.target.value))}
            >
              {Array.from({ length: settings.periodsPerDay }, (_, i) => i + 1).map((p) => (
                <option key={p} value={p}>
                  Period {p}
                  {p === 1 ? " (day's attendance)" : ""}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {cached && (
        <p className="mb-3 flex items-center gap-2 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <CloudOff className="size-4" aria-hidden /> Offline: showing the class list saved on this
          device.
        </p>
      )}
      {readOnly && (
        <p className="mb-3 rounded-xl bg-gray-100 px-4 py-3 text-sm">
          {date > todayIst()
            ? "Attendance can't be marked for a future date."
            : "This day can no longer be changed here. Please ask the school office."}
        </p>
      )}
      {correcting && (
        <p className="mb-3 rounded-xl bg-indigo-50 px-4 py-3 text-sm text-indigo-900">
          This day is already marked. Changes go to the school office for approval.
        </p>
      )}

      {settings.mode === "PERIOD" && !readOnly && (
        <label className="mb-3 block">
          <span className="mb-1 block text-sm font-medium">Subject (optional)</span>
          <input
            className={inputCls}
            value={subject}
            maxLength={60}
            onChange={(e) => setSubject(e.target.value)}
          />
        </label>
      )}

      {!readOnly && !correcting && (
        <div className="mb-3 flex flex-wrap gap-2">
          <Btn
            size="sm"
            onClick={() =>
              setDraft((d) => {
                const n: Draft = {};
                sheet.rows.forEach(
                  (r) =>
                    (n[r.studentId] = {
                      ...d[r.studentId],
                      status: r.leave === "APPROVED" ? "LV" : "P",
                      reason: null,
                    }),
                );
                return n;
              })
            }
          >
            Everyone present
          </Btn>
        </div>
      )}

      {sheet.rows.length === 0 ? (
        <Empty>No students in this class yet.</Empty>
      ) : (
        <ul className="divide-y divide-black/5 overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
          {sheet.rows.map((r) => {
            const d = draft[r.studentId];
            return (
              <li key={r.studentId} className="px-3 py-2.5 sm:px-4">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
                  <span className="text-ink-muted w-7 shrink-0 text-right text-sm tabular-nums">
                    {r.rollNo ?? "–"}
                  </span>
                  <span className="min-w-[8rem] flex-1">
                    <span className="block font-medium">{r.name}</span>
                    <span className="flex flex-wrap gap-1.5">
                      {r.leave === "APPROVED" && <Badge tone="purple">Leave approved</Badge>}
                      {r.leave === "PENDING" && <Badge tone="amber">Leave requested</Badge>}
                      {r.correctionPending && <Badge tone="blue">Correction pending</Badge>}
                      {r.alert && (
                        <span className="text-ink-muted inline-flex items-center gap-1 text-xs">
                          <BellRing className="size-3" aria-hidden /> {r.alert}
                        </span>
                      )}
                    </span>
                  </span>
                  <div
                    role="radiogroup"
                    aria-label={`Attendance for ${r.name}`}
                    className="flex gap-1"
                  >
                    {(["P", "A", "L", "H", "LV"] as Status[]).map((s) => (
                      <button
                        key={s}
                        type="button"
                        role="radio"
                        aria-checked={d?.status === s}
                        aria-label={STATUS[s].label}
                        title={STATUS[s].label}
                        disabled={readOnly}
                        onClick={() => set(r.studentId, s)}
                        className={cn(
                          "grid size-10 place-items-center rounded-xl text-sm font-semibold transition disabled:opacity-60",
                          d?.status === s
                            ? STATUS[s].on
                            : "text-ink-muted bg-black/[0.04] hover:bg-black/[0.08]",
                        )}
                      >
                        {STATUS[s].short}
                      </button>
                    ))}
                  </div>
                </div>
                {settings.reasonsAllowed &&
                  d &&
                  (d.status === "A" || d.status === "L" || d.status === "H") &&
                  !readOnly && (
                    <div className="mt-2 pl-10">
                      <select
                        aria-label={`Reason for ${r.name}`}
                        className={`${inputCls} w-auto! py-1.5 text-xs`}
                        value={d.reason ?? ""}
                        onChange={(e) =>
                          setDraft((x) => ({
                            ...x,
                            [r.studentId]: {
                              ...d,
                              ...x[r.studentId],
                              reason: e.target.value || null,
                            },
                          }))
                        }
                      >
                        <option value="">Reason (optional)</option>
                        {settings.reasonCodes.map((c) => (
                          <option key={c}>{c}</option>
                        ))}
                      </select>
                    </div>
                  )}
              </li>
            );
          })}
        </ul>
      )}

      {correcting && changed > 0 && (
        <Card className="mt-4">
          <label className="block">
            <span className="mb-1 block text-sm font-medium">Why does this need to change?</span>
            <input
              className={inputCls}
              value={why}
              maxLength={300}
              onChange={(e) => setWhy(e.target.value)}
            />
          </label>
        </Card>
      )}

      {!readOnly && sheet.rows.length > 0 && (
        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-black/5 bg-white/95 backdrop-blur">
          <div className="mx-auto flex max-w-2xl flex-wrap items-center justify-between gap-3 px-4 py-3">
            <p className="text-sm">
              <span className="font-semibold text-emerald-700">
                {counts.P + counts.L + counts.H} present
              </span>
              {" · "}
              <span className="font-semibold text-red-700">{counts.A} absent</span>
              {counts.LV ? ` · ${counts.LV} on leave` : ""}
            </p>
            <Btn
              variant="primary"
              busy={busy}
              disabled={correcting && (changed === 0 || !why.trim())}
              onClick={() => void save()}
            >
              {correcting
                ? `Send ${changed} change(s) for approval`
                : sheet.marked
                  ? "Save changes"
                  : "Save attendance"}
            </Btn>
          </div>
        </div>
      )}
    </div>
  );
}
