"use client";

import { useEffect, useState } from "react";
import { Mail, MessageCircle, Phone, X } from "lucide-react";
import { sb, fmtDate } from "@/lib/studio";
import { Badge, Btn, Card, Field, inputCls, useAction } from "@/components/kit";
import {
  KIND_LABEL,
  SOURCE_LABEL,
  STAGES,
  STAGE_LABEL,
  STAGE_TONE,
  dueState,
  fromDateInput,
  toDateInput,
  type Activity,
  type Counsellor,
  type Lead,
} from "./types";

type Detail = { lead: Lead; activities: Activity[] };

export function LeadPanel({
  id,
  counsellors,
  onClose,
  onChanged,
}: {
  id: string;
  counsellors: Counsellor[];
  onClose: () => void;
  onChanged: () => void;
}) {
  const [d, setD] = useState<Detail | null>(null);
  const [kind, setKind] = useState("CALL");
  const [body, setBody] = useState("");
  const [next, setNext] = useState("");
  const [lostReason, setLostReason] = useState("");
  const [askLost, setAskLost] = useState(false);
  const { busy, run } = useAction();

  useEffect(() => {
    let live = true;
    void sb<Detail>(`admissions/leads/${id}`).then((r) => {
      if (live) {
        setD(r);
        setNext(toDateInput(r.lead.nextFollowUpAt));
      }
    });
    return () => {
      live = false;
    };
  }, [id]);

  if (!d) {
    return (
      <Card className="h-fit">
        <p className="text-ink-muted text-sm">Loading lead…</p>
      </Card>
    );
  }
  const l = d.lead;

  async function move(stage: string, reason?: string) {
    const r = await run("stage", () => sb<Lead>(`admissions/leads/${l.id}/stage`, { body: { stage, reason } }), `Moved to ${STAGE_LABEL[stage]}`);
    if (r) {
      const fresh = await sb<Detail>(`admissions/leads/${l.id}`);
      setD(fresh);
      setAskLost(false);
      onChanged();
    }
  }

  async function logActivity() {
    const r = await run(
      "log",
      () => sb<Detail>(`admissions/leads/${l.id}/activities`, { body: { kind, body, nextFollowUpAt: fromDateInput(next) } }),
      "Logged",
    );
    if (r) {
      setD(r);
      setBody("");
      onChanged();
    }
  }

  async function patch(p: Record<string, unknown>, ok: string) {
    const r = await run("patch", () => sb<Lead>(`admissions/leads/${l.id}`, { method: "PATCH", body: p }), ok);
    if (r) {
      setD(await sb<Detail>(`admissions/leads/${l.id}`));
      onChanged();
    }
  }

  const due = dueState(l.nextFollowUpAt);
  const wa = l.phone.replace(/\D/g, "");
  const waNumber = wa.length === 10 ? `91${wa}` : wa;

  return (
    <Card className="h-fit lg:sticky lg:top-24">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="truncate text-lg font-semibold">{l.studentName || l.guardianName}</h2>
          <p className="text-ink-muted text-xs">
            {l.studentName ? `Parent: ${l.guardianName} · ` : ""}
            {l.gradeSought ? `${l.gradeSought} · ` : ""}
            {l.academicYear} · {SOURCE_LABEL[l.source] ?? l.source}
          </p>
        </div>
        <button type="button" onClick={onClose} className="text-ink-muted hover:text-ink rounded-full p-1.5 hover:bg-black/5" aria-label="Close lead">
          <X className="size-4" />
        </button>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Badge tone={STAGE_TONE[l.stage]}>{STAGE_LABEL[l.stage]}</Badge>
        {l.priority === "HIGH" && <Badge tone="red">High priority</Badge>}
        {due === "overdue" && <Badge tone="red">Follow-up overdue</Badge>}
        {due === "today" && <Badge tone="amber">Follow up today</Badge>}
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        <a href={`tel:${l.phone.replace(/\s/g, "")}`} className="bg-primary text-on-primary inline-flex h-9 items-center gap-1.5 rounded-full px-3.5 text-sm font-medium">
          <Phone className="size-4" aria-hidden /> Call
        </a>
        <a
          href={`https://wa.me/${waNumber}`}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex h-9 items-center gap-1.5 rounded-full bg-emerald-600 px-3.5 text-sm font-medium text-white"
        >
          <MessageCircle className="size-4" aria-hidden /> WhatsApp
        </a>
        {l.email && (
          <a href={`mailto:${l.email}`} className="inline-flex h-9 items-center gap-1.5 rounded-full px-3.5 text-sm font-medium ring-1 ring-black/10">
            <Mail className="size-4" aria-hidden /> Email
          </a>
        )}
      </div>
      <p className="text-ink-muted mt-2 text-sm">
        {l.phone}
        {l.email ? ` · ${l.email}` : ""}
      </p>

      <div className="mt-5">
        <p className="mb-2 text-sm font-medium">Move to stage</p>
        <div className="flex flex-wrap gap-1.5">
          {STAGES.filter((s) => s !== "LOST").map((s) => (
            <Btn key={s} size="sm" variant={l.stage === s ? "primary" : "secondary"} disabled={busy === "stage"} onClick={() => void move(s)}>
              {STAGE_LABEL[s]}
            </Btn>
          ))}
          <Btn size="sm" variant={l.stage === "LOST" ? "danger" : "ghost"} onClick={() => setAskLost(true)}>
            Lost…
          </Btn>
        </div>
        {askLost && (
          <div className="mt-3 flex gap-2">
            <input
              className={inputCls}
              placeholder="Reason, e.g. chose another school"
              value={lostReason}
              onChange={(e) => setLostReason(e.target.value)}
              aria-label="Reason the lead was lost"
            />
            <Btn variant="danger" busy={busy === "stage"} disabled={!lostReason.trim()} onClick={() => void move("LOST", lostReason)}>
              Mark lost
            </Btn>
          </div>
        )}
        {l.stage === "LOST" && l.lostReason && <p className="text-ink-muted mt-2 text-sm">Reason: {l.lostReason}</p>}
      </div>

      <div className="mt-5 grid gap-3 sm:grid-cols-2">
        <Field label="Counsellor">
          <select
            className={inputCls}
            value={l.assignedTo ?? ""}
            onChange={(e) => e.target.value && void patch({ assignedTo: e.target.value }, "Assigned")}
          >
            <option value="">Unassigned</option>
            {counsellors.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Priority">
          <select className={inputCls} value={l.priority} onChange={(e) => void patch({ priority: e.target.value }, "Priority updated")}>
            <option value="LOW">Low</option>
            <option value="NORMAL">Normal</option>
            <option value="HIGH">High</option>
          </select>
        </Field>
      </div>

      <div className="mt-5 rounded-xl bg-black/[0.03] p-3">
        <p className="mb-2 text-sm font-medium">Log an interaction</p>
        <div className="flex flex-wrap gap-1.5">
          {["CALL", "VISIT", "MESSAGE", "NOTE"].map((k) => (
            <Btn key={k} size="sm" variant={kind === k ? "primary" : "secondary"} onClick={() => setKind(k)}>
              {KIND_LABEL[k]}
            </Btn>
          ))}
        </div>
        <textarea
          className={`${inputCls} mt-2`}
          rows={2}
          placeholder={kind === "CALL" ? "What did the parent say?" : "Add a note"}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          aria-label="Interaction note"
        />
        <div className="mt-2 flex flex-wrap items-end gap-2">
          <label className="text-sm">
            <span className="text-ink-muted mb-1 block text-xs">Next follow-up</span>
            <input type="date" className={inputCls} value={next} onChange={(e) => setNext(e.target.value)} />
          </label>
          <Btn variant="primary" busy={busy === "log"} disabled={!body.trim()} onClick={() => void logActivity()}>
            Save
          </Btn>
        </div>
      </div>

      {l.notes && (
        <div className="mt-4">
          <p className="text-sm font-medium">{l.enquiryId ? "Enquiry message" : "Notes"}</p>
          <p className="text-ink-muted mt-1 text-sm whitespace-pre-line">{l.notes}</p>
        </div>
      )}

      <div className="mt-5">
        <p className="mb-2 text-sm font-medium">Timeline</p>
        <ol className="space-y-3 border-l border-black/10 pl-4">
          {d.activities.map((a) => (
            <li key={a.id} className="relative text-sm">
              <span className="bg-primary absolute top-1.5 -left-[1.3rem] size-2 rounded-full" aria-hidden />
              <p className="font-medium">
                {a.kind === "STAGE" ? `${STAGE_LABEL[a.fromStage ?? ""] ?? "—"} → ${STAGE_LABEL[a.toStage ?? ""] ?? a.toStage}` : KIND_LABEL[a.kind] ?? a.kind}
              </p>
              {a.body && <p className="text-ink-muted whitespace-pre-line">{a.body}</p>}
              <p className="text-ink-muted text-xs">
                {fmtDate(a.createdAt)}
                {a.actorName ? ` · ${a.actorName}` : ""}
              </p>
            </li>
          ))}
        </ol>
      </div>
    </Card>
  );
}
