"use client";

import { useCallback, useEffect, useState } from "react";
import { Plus, Search, Trash2, X } from "lucide-react";
import { sb, fmtDate } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Field, Loading, inputCls, useAction } from "@/components/kit";

export type Ticket = {
  id: string;
  ticketNo: string;
  category: string;
  subject: string;
  requesterName: string;
  phone: string;
  email?: string | null;
  studentName?: string | null;
  studentClass?: string | null;
  priority: string;
  status: string;
  assignedTo?: string | null;
  assignedName?: string | null;
  source: string;
  dueAt: string;
  overdue: boolean;
  firstResponseAt?: string | null;
  resolvedAt?: string | null;
  createdAt: string;
};
type Message = {
  id: string;
  authorKind: string;
  authorName?: string | null;
  body: string;
  isPublic: boolean;
  createdAt: string;
};
type Detail = { ticket: Ticket; messages: Message[] };
type Summary = {
  open: number;
  overdue: number;
  dueToday: number;
  unassigned: number;
  resolvedThisWeek: number;
};
export type Staff = { id: string; name: string };

export const CATEGORY: Record<string, string> = {
  FEES: "Fees",
  TRANSPORT: "Transport",
  ACADEMICS: "Academics",
  ADMISSIONS: "Admissions",
  FACILITIES: "Facilities",
  CERTIFICATES: "Certificates",
  COMPLAINT: "Complaint",
  OTHER: "Other",
};
const STATUS: Record<string, string> = {
  OPEN: "Open",
  IN_PROGRESS: "In progress",
  WAITING: "Waiting on parent",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
};
const STATUS_TONE: Record<string, "blue" | "purple" | "amber" | "green" | "gray"> = {
  OPEN: "blue",
  IN_PROGRESS: "purple",
  WAITING: "amber",
  RESOLVED: "green",
  CLOSED: "gray",
};
const SOURCE: Record<string, string> = {
  WEBSITE: "Website",
  PHONE: "Phone",
  WALK_IN: "Walk-in",
  EMAIL: "Email",
  OTHER: "Other",
};

const dueText = (t: Ticket) => {
  if (t.status === "RESOLVED" || t.status === "CLOSED" || t.status === "WAITING") return null;
  const h = Math.round((new Date(t.dueAt).getTime() - Date.now()) / 3600000);
  if (h < 0) return `${-h < 48 ? `${-h} h` : `${Math.round(-h / 24)} days`} overdue`;
  return `due in ${h < 48 ? `${h} h` : `${Math.round(h / 24)} days`}`;
};

export function HelpdeskView({ canAssign, siteUrl }: { canAssign: boolean; siteUrl?: string }) {
  const [view, setView] = useState("open");
  const [q, setQ] = useState("");
  const [rows, setRows] = useState<Ticket[] | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [staff, setStaff] = useState<Staff[]>([]);
  const [sel, setSel] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const p = new URLSearchParams({ view });
      if (q) p.set("q", q);
      const [r, s] = await Promise.all([
        sb<Ticket[]>(`engagement/helpdesk?${p}`),
        sb<Summary>("engagement/helpdesk/summary"),
      ]);
      setRows(r);
      setSummary(s);
    } catch (e) {
      setErr((e as Error).message);
    }
  }, [view, q]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 250);
    return () => clearTimeout(t);
  }, [load]);
  useEffect(() => {
    sb<Staff[]>("engagement/staff")
      .then(setStaff)
      .catch(() => {});
  }, []);

  if (err) return <Empty>{err}</Empty>;
  return (
    <div>
      {summary && (
        <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {(
            [
              ["Open", summary.open, ""],
              ["Overdue", summary.overdue, "text-red-600"],
              ["Due today", summary.dueToday, "text-amber-700"],
              ["Resolved this week", summary.resolvedThisWeek, "text-emerald-700"],
            ] as [string, number, string][]
          ).map(([k, v, c]) => (
            <div key={k} className="rounded-2xl bg-white p-4 ring-1 ring-black/5">
              <p className={`text-2xl font-bold tabular-nums ${v ? c : ""}`}>{v}</p>
              <p className="text-ink-muted text-xs">{k}</p>
            </div>
          ))}
        </div>
      )}
      {siteUrl && (
        <p className="text-ink-muted mb-4 text-sm">
          Parents raise and track requests at{" "}
          <a
            className="text-primary font-medium underline"
            href={`${siteUrl}/help`}
            target="_blank"
            rel="noopener noreferrer"
          >
            {siteUrl.replace(/^https?:\/\//, "")}/help
          </a>
          . Add it to your website menu or share it on WhatsApp.
        </p>
      )}
      <div className="mb-4 flex flex-wrap items-center gap-2">
        {(
          [
            ["open", "Open"],
            ["overdue", "Overdue"],
            ["mine", "Mine"],
            ["unassigned", "Unassigned"],
            ["resolved", "Resolved"],
            ["all", "All"],
          ] as [string, string][]
        ).map(([k, label]) => (
          <Btn
            key={k}
            size="sm"
            variant={view === k ? "primary" : "secondary"}
            onClick={() => setView(k)}
          >
            {label}
          </Btn>
        ))}
        <div className="relative ml-auto w-full sm:w-60">
          <Search className="text-ink-muted absolute top-2.5 left-3 size-4" aria-hidden />
          <input
            className={`${inputCls} pl-9`}
            placeholder="Number, name or phone"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Search requests"
          />
        </div>
        <Btn variant="primary" onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden /> Log request
        </Btn>
      </div>
      {adding && (
        <NewTicket
          onClose={() => setAdding(false)}
          onCreated={(d) => {
            setAdding(false);
            void load();
            setSel(d.ticket.id);
          }}
        />
      )}
      <div className={`grid gap-5 ${sel ? "xl:grid-cols-[1fr_460px]" : ""}`}>
        <div className="min-w-0">
          {!rows ? (
            <Loading />
          ) : rows.length === 0 ? (
            <Empty>No requests here.</Empty>
          ) : (
            <ul className="overflow-hidden rounded-2xl bg-white ring-1 ring-black/5">
              {rows.map((t) => (
                <li key={t.id} className="border-b border-black/5 last:border-0">
                  <button
                    type="button"
                    onClick={() => setSel(t.id)}
                    className={`flex w-full flex-wrap items-start gap-x-3 gap-y-1 px-4 py-3 text-left hover:bg-black/[0.02] ${sel === t.id ? "bg-primary/5" : ""}`}
                  >
                    <span className="min-w-[12rem] flex-1">
                      <span className="block text-sm font-semibold">
                        {t.ticketNo} · {t.subject}
                      </span>
                      <span className="text-ink-muted block text-xs">
                        {[
                          t.requesterName,
                          t.studentName &&
                            `${t.studentName}${t.studentClass ? ` (${t.studentClass})` : ""}`,
                          CATEGORY[t.category],
                          t.assignedName && `→ ${t.assignedName}`,
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      </span>
                    </span>
                    <span className="flex flex-wrap items-center gap-1.5">
                      {t.priority === "HIGH" && <Badge tone="red">High</Badge>}
                      {dueText(t) && (
                        <span
                          className={`text-xs ${t.overdue ? "font-semibold text-red-600" : "text-ink-muted"}`}
                        >
                          {dueText(t)}
                        </span>
                      )}
                      <Badge tone={STATUS_TONE[t.status]}>{STATUS[t.status]}</Badge>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        {sel && (
          <TicketPanel
            key={sel}
            id={sel}
            staff={staff}
            canAssign={canAssign}
            onClose={() => setSel(null)}
            onChanged={() => void load()}
            onDeleted={() => {
              setSel(null);
              void load();
            }}
          />
        )}
      </div>
    </div>
  );
}

function NewTicket({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (d: Detail) => void;
}) {
  const [f, setF] = useState({
    requesterName: "",
    phone: "",
    email: "",
    studentName: "",
    studentClass: "",
    category: "OTHER",
    subject: "",
    description: "",
    priority: "NORMAL",
    source: "PHONE",
  });
  const { busy, run } = useAction();
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF({ ...f, [k]: e.target.value });
  async function save(e: React.FormEvent) {
    e.preventDefault();
    const r = await run(
      "add",
      () => sb<Detail>("engagement/helpdesk", { body: { ...f, email: f.email || null } }),
      "Request logged",
    );
    if (r) onCreated(r);
  }
  return (
    <Card className="mb-5">
      <form onSubmit={(e) => void save(e)}>
        <h2 className="text-lg font-semibold">Log a request from a phone call, visit or email</h2>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Parent name *">
            <input
              className={inputCls}
              required
              maxLength={120}
              value={f.requesterName}
              onChange={set("requesterName")}
            />
          </Field>
          <Field label="Phone *">
            <input
              className={inputCls}
              required
              type="tel"
              maxLength={20}
              value={f.phone}
              onChange={set("phone")}
            />
          </Field>
          <Field label="Email">
            <input
              className={inputCls}
              type="email"
              maxLength={200}
              value={f.email}
              onChange={set("email")}
            />
          </Field>
          <Field label="Came by">
            <select className={inputCls} value={f.source} onChange={set("source")}>
              {["PHONE", "WALK_IN", "EMAIL", "OTHER"].map((s) => (
                <option key={s} value={s}>
                  {SOURCE[s]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Student">
            <input
              className={inputCls}
              maxLength={120}
              value={f.studentName}
              onChange={set("studentName")}
            />
          </Field>
          <Field label="Class">
            <input
              className={inputCls}
              maxLength={60}
              value={f.studentClass}
              onChange={set("studentClass")}
            />
          </Field>
          <Field label="Category">
            <select className={inputCls} value={f.category} onChange={set("category")}>
              {Object.entries(CATEGORY).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Priority">
            <select className={inputCls} value={f.priority} onChange={set("priority")}>
              <option value="LOW">Low (7 days)</option>
              <option value="NORMAL">Normal (3 days)</option>
              <option value="HIGH">High (24 hours)</option>
            </select>
          </Field>
          <div className="sm:col-span-2 lg:col-span-4">
            <Field label="Subject *">
              <input
                className={inputCls}
                required
                maxLength={160}
                value={f.subject}
                onChange={set("subject")}
              />
            </Field>
          </div>
          <div className="sm:col-span-2 lg:col-span-4">
            <Field label="Details *">
              <textarea
                className={inputCls}
                required
                rows={3}
                maxLength={4000}
                value={f.description}
                onChange={set("description")}
              />
            </Field>
          </div>
        </div>
        <div className="mt-4 flex gap-2">
          <Btn type="submit" variant="primary" busy={busy === "add"}>
            Log request
          </Btn>
          <Btn variant="ghost" onClick={onClose}>
            Cancel
          </Btn>
        </div>
      </form>
    </Card>
  );
}

function TicketPanel({
  id,
  staff,
  canAssign,
  onClose,
  onChanged,
  onDeleted,
}: {
  id: string;
  staff: Staff[];
  canAssign: boolean;
  onClose: () => void;
  onChanged: () => void;
  onDeleted: () => void;
}) {
  const [d, setD] = useState<Detail | null>(null);
  const [reply, setReply] = useState("");
  const [internal, setInternal] = useState(false);
  const { busy, run } = useAction();

  useEffect(() => {
    let live = true;
    void sb<Detail>(`engagement/helpdesk/${id}`).then((r) => live && setD(r));
    return () => {
      live = false;
    };
  }, [id]);

  if (!d) {
    return (
      <Card className="h-fit">
        <p className="text-ink-muted text-sm">Loading…</p>
      </Card>
    );
  }
  const t = d.ticket;
  const done = (r: Detail | undefined | null) => {
    if (r) {
      setD(r);
      onChanged();
    }
  };
  async function patch(body: Record<string, unknown>, ok: string) {
    done(
      await run(
        "patch",
        () => sb<Detail>(`engagement/helpdesk/${t.id}`, { method: "PATCH", body }),
        ok,
      ),
    );
  }
  async function send(status?: string) {
    const r = await run(
      "reply",
      () =>
        sb<Detail>(`engagement/helpdesk/${t.id}/reply`, {
          body: { body: reply, isPublic: !internal, status },
        }),
      internal ? "Note added" : "Reply sent",
    );
    if (r) {
      setReply("");
      done(r);
    }
  }
  async function remove() {
    if (!confirm(`Delete request ${t.ticketNo}? This cannot be undone.`)) return;
    if (
      await run(
        "del",
        () => sb(`engagement/helpdesk/${t.id}`, { method: "DELETE" }).then(() => true),
        "Deleted",
      )
    )
      onDeleted();
  }

  return (
    <Card className="h-fit xl:sticky xl:top-24">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-ink-muted text-xs font-medium">
            {t.ticketNo} · {SOURCE[t.source]} · {fmtDate(t.createdAt)}
          </p>
          <h2 className="text-lg font-semibold">{t.subject}</h2>
          <p className="text-ink-muted text-xs">
            {t.requesterName} · {t.phone}
            {t.email ? ` · ${t.email}` : ""}
            {t.studentName
              ? ` · ${t.studentName}${t.studentClass ? ` (${t.studentClass})` : ""}`
              : ""}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="text-ink-muted hover:text-ink rounded-full p-1.5 hover:bg-black/5"
          aria-label="Close request"
        >
          <X className="size-4" />
        </button>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Badge tone={STATUS_TONE[t.status]}>{STATUS[t.status]}</Badge>
        {dueText(t) && (
          <span
            className={`text-xs ${t.overdue ? "font-semibold text-red-600" : "text-ink-muted"}`}
          >
            {dueText(t)}
          </span>
        )}
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-3">
        <Field label="Status">
          <select
            className={inputCls}
            value={t.status}
            onChange={(e) => void patch({ status: e.target.value }, "Status updated")}
          >
            {Object.entries(STATUS).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Priority">
          <select
            className={inputCls}
            value={t.priority}
            onChange={(e) => void patch({ priority: e.target.value }, "Priority updated")}
          >
            <option value="LOW">Low</option>
            <option value="NORMAL">Normal</option>
            <option value="HIGH">High</option>
          </select>
        </Field>
        <Field label="Assigned to">
          <select
            className={inputCls}
            value={t.assignedTo ?? ""}
            disabled={!canAssign}
            title={canAssign ? undefined : "Assigning requests is part of ShikshaBlox Standard"}
            onChange={(e) =>
              void patch(
                e.target.value ? { assignedTo: e.target.value } : { unassign: true },
                "Assigned",
              )
            }
          >
            <option value="">Nobody</option>
            {staff.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </Field>
      </div>

      <ol className="mt-5 space-y-2.5">
        {d.messages.map((m) => (
          <li
            key={m.id}
            className={`rounded-xl p-3 text-sm ${m.authorKind === "SYSTEM" ? "text-ink-muted bg-transparent px-0 py-0 text-xs" : !m.isPublic ? "bg-amber-50 ring-1 ring-amber-200" : m.authorKind === "STAFF" ? "bg-primary/5" : "bg-black/[0.03]"}`}
          >
            {m.authorKind === "SYSTEM" ? (
              <p>
                {m.body} · {m.authorName ? `${m.authorName} · ` : ""}
                {fmtDate(m.createdAt)}
              </p>
            ) : (
              <>
                <p className="text-xs font-semibold">
                  {m.authorKind === "REQUESTER" ? `${m.authorName} (parent)` : m.authorName}
                  {!m.isPublic && " · internal note"}{" "}
                  <span className="text-ink-muted font-normal">· {fmtDate(m.createdAt)}</span>
                </p>
                <p className="mt-1 whitespace-pre-line">{m.body}</p>
              </>
            )}
          </li>
        ))}
      </ol>

      {t.status !== "CLOSED" && (
        <div className="mt-4 rounded-xl bg-black/[0.03] p-3">
          <div className="mb-2 flex gap-1.5">
            <Btn
              size="sm"
              variant={!internal ? "primary" : "secondary"}
              onClick={() => setInternal(false)}
            >
              Reply to parent
            </Btn>
            <Btn
              size="sm"
              variant={internal ? "primary" : "secondary"}
              onClick={() => setInternal(true)}
            >
              Internal note
            </Btn>
          </div>
          <textarea
            className={inputCls}
            rows={3}
            maxLength={4000}
            placeholder={
              internal
                ? "Only staff see this"
                : t.email
                  ? "The parent sees this on the website and by email"
                  : "The parent sees this on the website"
            }
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            aria-label={internal ? "Internal note" : "Reply to parent"}
          />
          <div className="mt-2 flex flex-wrap gap-2">
            <Btn
              variant="primary"
              busy={busy === "reply"}
              disabled={!reply.trim()}
              onClick={() => void send()}
            >
              {internal ? "Add note" : "Send reply"}
            </Btn>
            {!internal && (
              <Btn
                busy={busy === "reply"}
                disabled={!reply.trim()}
                onClick={() => void send("RESOLVED")}
              >
                Send and resolve
              </Btn>
            )}
          </div>
        </div>
      )}
      <div className="mt-5 border-t border-black/5 pt-3">
        <Btn size="sm" variant="ghost" busy={busy === "del"} onClick={() => void remove()}>
          <Trash2 className="size-3.5" aria-hidden /> Delete request
        </Btn>
      </div>
    </Card>
  );
}
