"use client";

import { useCallback, useEffect, useState } from "react";
import { sb } from "@/lib/studio";
import { Badge, Btn, Empty, Loading, inputCls, useAction } from "@/components/kit";
import { STATUS, dayLabel, type Correction, type Leave } from "./types";

const LEAVE_TONE = {
  PENDING: "amber",
  APPROVED: "green",
  REJECTED: "red",
  CANCELLED: "gray",
} as const;
const LEAVE_LABEL = {
  PENDING: "Waiting",
  APPROVED: "Approved",
  REJECTED: "Declined",
  CANCELLED: "Withdrawn",
};

function range(l: { fromDate: string; toDate: string; days: number }) {
  return l.fromDate === l.toDate
    ? dayLabel(l.fromDate)
    : `${dayLabel(l.fromDate)} – ${dayLabel(l.toDate)} (${l.days} days)`;
}

export function LeaveView({ onChanged }: { onChanged: () => void }) {
  const [rows, setRows] = useState<Leave[] | null>(null);
  const [filter, setFilter] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [declining, setDeclining] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const { busy, run } = useAction();

  const load = useCallback(async () => {
    try {
      setRows(await sb<Leave[]>(`attendance/leave${filter ? `?status=${filter}` : ""}`));
    } catch (e) {
      setErr((e as Error).message);
    }
  }, [filter]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  async function decide(id: string, approve: boolean) {
    const r = await run(
      id,
      () =>
        sb(`attendance/leave/${id}/${approve ? "approve" : "reject"}`, {
          method: "POST",
          body: { note: note || null },
        }),
      approve ? "Leave approved." : "Leave declined.",
    );
    if (r !== undefined) {
      setDeclining(null);
      setNote("");
      void load();
      onChanged();
    }
  }

  if (err) return <Empty>{err}</Empty>;
  if (!rows) return <Loading />;
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <select
          aria-label="Show"
          className={`${inputCls} w-auto!`}
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        >
          <option value="">Waiting and recent</option>
          <option value="PENDING">Waiting for a decision</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Declined</option>
          <option value="CANCELLED">Withdrawn</option>
        </select>
        <p className="text-ink-muted text-sm">
          Parents apply in the parent portal on the school website.
        </p>
      </div>
      {rows.length === 0 ? (
        <Empty>No leave requests.</Empty>
      ) : (
        <ul className="divide-y divide-black/5 rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
          {rows.map((l) => (
            <li key={l.id} className="space-y-2 p-4">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">
                    {l.studentName}
                    {l.classLabel && (
                      <span className="text-ink-muted font-normal"> · {l.classLabel}</span>
                    )}
                  </p>
                  <p className="text-sm">{range(l)}</p>
                </div>
                <Badge tone={LEAVE_TONE[l.status]}>{LEAVE_LABEL[l.status]}</Badge>
              </div>
              <p className="text-sm">“{l.reason}”</p>
              <p className="text-ink-muted text-xs">
                {l.source === "PORTAL"
                  ? `Applied by ${l.requestedBy ?? "parent"} in the portal`
                  : `Recorded by ${l.requestedBy ?? "staff"}`}
                {l.decidedBy && l.status !== "PENDING"
                  ? ` · ${LEAVE_LABEL[l.status].toLowerCase()} by ${l.decidedBy}`
                  : ""}
                {l.decisionNote ? ` · “${l.decisionNote}”` : ""}
              </p>
              {l.status === "PENDING" &&
                (declining === l.id ? (
                  <div className="flex flex-wrap items-center gap-2">
                    <input
                      className={`${inputCls} max-w-sm`}
                      placeholder="Reason, shown to the parent"
                      value={note}
                      maxLength={300}
                      onChange={(e) => setNote(e.target.value)}
                    />
                    <Btn
                      size="sm"
                      variant="danger"
                      busy={busy === l.id}
                      disabled={!note.trim()}
                      onClick={() => void decide(l.id, false)}
                    >
                      Decline
                    </Btn>
                    <Btn size="sm" variant="ghost" onClick={() => setDeclining(null)}>
                      Back
                    </Btn>
                  </div>
                ) : (
                  <div className="flex gap-2">
                    <Btn
                      size="sm"
                      variant="success"
                      busy={busy === l.id}
                      onClick={() => void decide(l.id, true)}
                    >
                      Approve
                    </Btn>
                    <Btn
                      size="sm"
                      onClick={() => {
                        setDeclining(l.id);
                        setNote("");
                      }}
                    >
                      Decline…
                    </Btn>
                  </div>
                ))}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function CorrectionsView({
  canManage,
  onChanged,
}: {
  canManage: boolean;
  onChanged: () => void;
}) {
  const [rows, setRows] = useState<Correction[] | null>(null);
  const [status, setStatus] = useState("PENDING");
  const [err, setErr] = useState<string | null>(null);
  const { busy, run } = useAction();

  const load = useCallback(async () => {
    try {
      setRows(await sb<Correction[]>(`attendance/corrections?status=${status}`));
    } catch (e) {
      setErr((e as Error).message);
    }
  }, [status]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  async function decide(id: string, approve: boolean) {
    const r = await run(
      id,
      () =>
        sb(`attendance/corrections/${id}/${approve ? "approve" : "reject"}`, {
          method: "POST",
        }).then(() => true),
      approve ? "Correction approved." : "Correction rejected.",
    );
    if (r) {
      void load();
      onChanged();
    }
  }

  if (err) return <Empty>{err}</Empty>;
  if (!rows) return <Loading />;
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <select
          aria-label="Show"
          className={`${inputCls} w-auto!`}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
        >
          <option value="PENDING">Waiting for approval</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
        </select>
        <p className="text-ink-muted text-sm">
          {canManage
            ? "Teachers' changes to days already marked wait here. Every change is in the audit log."
            : "Changes you asked for on days already marked."}
        </p>
      </div>
      {rows.length === 0 ? (
        <Empty>Nothing here.</Empty>
      ) : (
        <ul className="divide-y divide-black/5 rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
          {rows.map((c) => (
            <li key={c.id} className="flex flex-wrap items-center gap-3 p-4">
              <div className="min-w-[14rem] flex-1">
                <p className="font-semibold">
                  {c.studentName}{" "}
                  <span className="text-ink-muted font-normal">· {c.classLabel}</span>
                </p>
                <p className="text-sm">
                  {dayLabel(c.date)}
                  {c.period ? ` · period ${c.period}` : ""}:{" "}
                  {c.fromStatus ? STATUS[c.fromStatus].label : "not marked"} →{" "}
                  <strong>{STATUS[c.toStatus].label}</strong>
                  {c.toReason ? ` (${c.toReason})` : ""}
                </p>
                <p className="text-ink-muted text-xs">
                  “{c.reason}” · asked by {c.requestedBy ?? "a teacher"}
                  {c.decidedBy ? ` · decided by ${c.decidedBy}` : ""}
                </p>
              </div>
              {canManage && c.status === "PENDING" && (
                <div className="flex gap-2">
                  <Btn
                    size="sm"
                    variant="success"
                    busy={busy === c.id}
                    onClick={() => void decide(c.id, true)}
                  >
                    Approve
                  </Btn>
                  <Btn size="sm" variant="danger" onClick={() => void decide(c.id, false)}>
                    Reject
                  </Btn>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
