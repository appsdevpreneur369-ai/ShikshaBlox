"use client";

import { useCallback, useEffect, useState } from "react";
import { ArrowLeft, Plus, Send, Trash2 } from "lucide-react";
import { sb, fmtDate } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Field, Loading, inputCls, useAction } from "@/components/kit";
import { CHANNEL_LABEL, type Template, type TagCount } from "./types";

type Stats = {
  total: number;
  sent: number;
  failed: number;
  skipped: number;
  read: number;
  acknowledged: number;
  consentYes: number;
  consentNo: number;
  testMode: number;
};
type Broadcast = {
  id: string;
  title: string;
  details?: string | null;
  templateName: string;
  channels: string[];
  audienceTags: string[];
  ackRequired: boolean;
  consentQuestion?: string | null;
  emergency: boolean;
  status: string;
  sendAt?: string | null;
  sentAt?: string | null;
  createdAt: string;
  stats: Stats;
};
type Preview = {
  audience: number;
  byChannel: Record<string, number>;
  skipped: number;
  skipReasons: Record<string, number>;
  costPaise: number;
  testMode: boolean;
  willSendAt: string;
  sample?: string | null;
};
type Delivery = {
  id: string;
  contactName: string;
  channel?: string | null;
  address?: string | null;
  status: string;
  skipReason?: string | null;
  testMode: boolean;
  sentAt?: string | null;
  readAt?: string | null;
  ackAt?: string | null;
  consentAnswer?: boolean | null;
  link: string;
};

const STATUS_TONE: Record<string, "gray" | "amber" | "purple" | "green" | "red"> = {
  DRAFT: "gray",
  SCHEDULED: "amber",
  SENDING: "purple",
  SENT: "green",
  CANCELLED: "gray",
};
const STATUS_LABEL: Record<string, string> = {
  DRAFT: "Draft",
  SCHEDULED: "Scheduled",
  SENDING: "Sending",
  SENT: "Sent",
  CANCELLED: "Cancelled",
};
const pct = (n: number, d: number) => (d ? `${Math.round((n / d) * 100)}%` : "—");
const rupees = (p: number) => `₹${(p / 100).toLocaleString("en-IN", { minimumFractionDigits: 2 })}`;

export function BroadcastsView({ templates, tags }: { templates: Template[]; tags: TagCount[] }) {
  const [list, setList] = useState<Broadcast[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [open, setOpen] = useState<string | "new" | null>(null);

  const load = useCallback(async () => {
    try {
      setList(await sb<Broadcast[]>("comms/broadcasts"));
    } catch (e) {
      setErr((e as Error).message);
    }
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  // Refresh while something is sending or scheduled.
  useEffect(() => {
    if (!list?.some((b) => b.status === "SENDING" || b.status === "SCHEDULED")) return;
    const t = setInterval(() => void load(), 15000);
    return () => clearInterval(t);
  }, [list, load]);

  if (err) return <Empty>{err}</Empty>;
  if (open === "new")
    return (
      <Composer
        templates={templates}
        tags={tags}
        onClose={() => setOpen(null)}
        onSaved={(b) => {
          void load();
          setOpen(b.id);
        }}
      />
    );
  if (open)
    return (
      <BroadcastDetail
        id={open}
        templates={templates}
        tags={tags}
        onBack={() => {
          setOpen(null);
          void load();
        }}
      />
    );

  return (
    <div>
      <Btn
        variant="primary"
        className="mb-4"
        onClick={() => setOpen("new")}
        disabled={templates.length === 0}
      >
        <Plus className="size-4" aria-hidden /> New circular
      </Btn>
      {templates.length === 0 && (
        <p className="text-ink-muted mb-4 text-sm">
          Create a message template first (Templates tab).
        </p>
      )}
      {!list ? (
        <Loading />
      ) : list.length === 0 ? (
        <Empty>No circulars yet.</Empty>
      ) : (
        <ul className="overflow-hidden rounded-2xl bg-white ring-1 ring-black/5">
          {list.map((b) => (
            <li key={b.id} className="border-b border-black/5 last:border-0">
              <button
                type="button"
                onClick={() => setOpen(b.id)}
                className="flex w-full flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3 text-left hover:bg-black/[0.02]"
              >
                <span className="min-w-[12rem] flex-1">
                  <span className="block text-sm font-semibold">{b.title}</span>
                  <span className="text-ink-muted block text-xs">
                    {[
                      b.audienceTags.length ? b.audienceTags.join(", ") : "All contacts",
                      b.channels.map((c) => CHANNEL_LABEL[c]).join(" → "),
                      b.sentAt ? fmtDate(b.sentAt) : b.sendAt ? `for ${fmtDate(b.sendAt)}` : null,
                    ]
                      .filter(Boolean)
                      .join(" · ")}
                  </span>
                </span>
                {b.status === "SENT" && (
                  <span className="text-ink-muted text-xs tabular-nums">
                    {b.stats.sent} sent · {pct(b.stats.read, b.stats.sent)} read
                    {b.ackRequired
                      ? ` · ${pct(b.stats.acknowledged, b.stats.sent)} acknowledged`
                      : ""}
                  </span>
                )}
                {b.emergency && <Badge tone="red">Urgent</Badge>}
                <Badge tone={STATUS_TONE[b.status]}>{STATUS_LABEL[b.status]}</Badge>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Composer({
  templates,
  tags,
  draft,
  onClose,
  onSaved,
}: {
  templates: Template[];
  tags: TagCount[];
  draft?: Broadcast;
  onClose: () => void;
  onSaved: (b: Broadcast) => void;
}) {
  const names = [...new Set(templates.map((t) => t.name))];
  const [f, setF] = useState({
    title: draft?.title ?? "",
    details: draft?.details ?? "",
    templateName: draft?.templateName ?? names[0] ?? "",
    channels: draft?.channels ?? ["WHATSAPP", "SMS", "EMAIL"],
    audienceTags: draft?.audienceTags ?? ([] as string[]),
    ackRequired: draft?.ackRequired ?? false,
    consentQuestion: draft?.consentQuestion ?? "",
    emergency: draft?.emergency ?? false,
    sendAt: draft?.sendAt
      ? new Intl.DateTimeFormat("sv-SE", {
          timeZone: "Asia/Kolkata",
          dateStyle: "short",
          timeStyle: "short",
        })
          .format(new Date(draft.sendAt))
          .replace(" ", "T")
      : "",
  });
  const { busy, run } = useAction();
  const toggle = (list: string[], v: string) =>
    list.includes(v) ? list.filter((x) => x !== v) : [...list, v];

  async function save() {
    const body = {
      ...f,
      details: f.details || null,
      consentQuestion: f.consentQuestion || null,
      sendAt: f.sendAt ? new Date(`${f.sendAt}:00+05:30`).toISOString() : null,
    };
    const r = await run(
      "save",
      () =>
        draft
          ? sb<Broadcast>(`comms/broadcasts/${draft.id}`, { method: "PATCH", body })
          : sb<Broadcast>("comms/broadcasts", { body }),
      "Draft saved",
    );
    if (r) onSaved(r);
  }

  return (
    <Card>
      <h2 className="text-lg font-semibold">{draft ? "Edit circular" : "New circular"}</h2>
      <div className="mt-4 grid gap-3">
        <Field label="Title *">
          <input
            className={inputCls}
            maxLength={160}
            value={f.title}
            onChange={(e) => setF({ ...f, title: e.target.value })}
            placeholder="e.g. School closed on Monday"
          />
        </Field>
        <Field
          label="Full circular (shown on the parent's personal link)"
          hint="Keep marks, health details and other sensitive information out of messages; they only carry the link."
        >
          <textarea
            className={inputCls}
            rows={6}
            maxLength={8000}
            value={f.details}
            onChange={(e) => setF({ ...f, details: e.target.value })}
          />
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Message template">
            <select
              className={inputCls}
              value={f.templateName}
              onChange={(e) => setF({ ...f, templateName: e.target.value })}
            >
              {names.map((n) => (
                <option key={n} value={n}>
                  {n} (
                  {templates
                    .filter((t) => t.name === n)
                    .map((t) => t.language)
                    .join(", ")}
                  )
                </option>
              ))}
            </select>
          </Field>
          <Field
            label="Send at (optional)"
            hint="Leave empty to send now. Non-urgent messages wait for the end of quiet hours."
          >
            <input
              className={inputCls}
              type="datetime-local"
              value={f.sendAt}
              onChange={(e) => setF({ ...f, sendAt: e.target.value })}
            />
          </Field>
        </div>
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium">Channels, in order of preference</legend>
          <div className="flex flex-wrap gap-1.5">
            {["WHATSAPP", "SMS", "EMAIL"].map((c) => (
              <Btn
                key={c}
                size="sm"
                variant={f.channels.includes(c) ? "primary" : "secondary"}
                onClick={() =>
                  setF({
                    ...f,
                    channels: ["WHATSAPP", "SMS", "EMAIL"].filter((x) =>
                      x === c ? !f.channels.includes(c) : f.channels.includes(x),
                    ),
                  })
                }
                aria-pressed={f.channels.includes(c)}
              >
                {CHANNEL_LABEL[c]}
              </Btn>
            ))}
          </div>
          <p className="text-ink-muted mt-1 text-xs">
            Each parent gets one message, on the first channel they have opted in to.
          </p>
        </fieldset>
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium">Audience</legend>
          <div className="flex flex-wrap gap-1.5">
            <Btn
              size="sm"
              variant={f.audienceTags.length === 0 ? "primary" : "secondary"}
              onClick={() => setF({ ...f, audienceTags: [] })}
            >
              All contacts
            </Btn>
            {tags.map((t) => (
              <Btn
                key={t.tag}
                size="sm"
                variant={f.audienceTags.includes(t.tag) ? "primary" : "secondary"}
                onClick={() => setF({ ...f, audienceTags: toggle(f.audienceTags, t.tag) })}
                aria-pressed={f.audienceTags.includes(t.tag)}
              >
                {t.tag} ({t.contacts})
              </Btn>
            ))}
          </div>
        </fieldset>
        <div className="flex flex-wrap gap-4 text-sm">
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              className="size-4"
              checked={f.ackRequired}
              onChange={(e) => setF({ ...f, ackRequired: e.target.checked })}
            />{" "}
            Ask parents to acknowledge
          </label>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              className="size-4"
              checked={f.emergency}
              onChange={(e) => setF({ ...f, emergency: e.target.checked })}
            />{" "}
            Urgent (send during quiet hours)
          </label>
        </div>
        {f.ackRequired && (
          <Field
            label="Yes / no question (optional)"
            hint="e.g. a permission slip: “Do you allow your child to join the trip?”"
          >
            <input
              className={inputCls}
              maxLength={300}
              value={f.consentQuestion}
              onChange={(e) => setF({ ...f, consentQuestion: e.target.value })}
            />
          </Field>
        )}
      </div>
      <div className="mt-5 flex gap-2">
        <Btn variant="primary" busy={busy === "save"} onClick={() => void save()}>
          Save and review
        </Btn>
        <Btn variant="ghost" onClick={onClose}>
          Cancel
        </Btn>
      </div>
    </Card>
  );
}

function BroadcastDetail({
  id,
  templates,
  tags,
  onBack,
}: {
  id: string;
  templates: Template[];
  tags: TagCount[];
  onBack: () => void;
}) {
  const [b, setB] = useState<Broadcast | null>(null);
  const [p, setP] = useState<Preview | null>(null);
  const [deferred, setDeferred] = useState(false);
  const [rows, setRows] = useState<Delivery[] | null>(null);
  const [filter, setFilter] = useState("");
  const [editing, setEditing] = useState(false);
  const { busy, run } = useAction();

  const load = useCallback(async () => {
    const x = await sb<Broadcast>(`comms/broadcasts/${id}`);
    setB(x);
    if (x.status === "DRAFT") {
      const pv = await sb<Preview>(`comms/broadcasts/${id}/preview`);
      setP(pv);
      setDeferred(new Date(pv.willSendAt).getTime() - Date.now() > 120000);
    } else
      setRows(
        await sb<Delivery[]>(
          `comms/broadcasts/${id}/deliveries${filter ? `?filter=${filter}` : ""}`,
        ),
      );
  }, [id, filter]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);
  useEffect(() => {
    if (b?.status !== "SENDING" && b?.status !== "SCHEDULED") return;
    const t = setInterval(() => void load(), 10000);
    return () => clearInterval(t);
  }, [b?.status, load]);

  if (!b) return <Loading />;
  if (editing)
    return (
      <Composer
        draft={b}
        templates={templates}
        tags={tags}
        onClose={() => setEditing(false)}
        onSaved={() => {
          setEditing(false);
          void load();
        }}
      />
    );

  async function send() {
    if (
      await run(
        "send",
        () => sb<Broadcast>(`comms/broadcasts/${id}/send`, { method: "POST" }),
        "Queued for sending",
      )
    )
      void load();
  }
  async function cancel() {
    if (
      await run(
        "cancel",
        () => sb<Broadcast>(`comms/broadcasts/${id}/cancel`, { method: "POST" }),
        "Cancelled",
      )
    )
      void load();
  }
  async function remove() {
    if (!confirm(`Delete “${b!.title}”?`)) return;
    if (
      await run(
        "del",
        () => sb(`comms/broadcasts/${id}`, { method: "DELETE" }).then(() => true),
        "Deleted",
      )
    )
      onBack();
  }

  const s = b.stats;
  return (
    <div>
      <button
        type="button"
        className="text-primary mb-3 inline-flex items-center gap-1 text-sm font-medium"
        onClick={onBack}
      >
        <ArrowLeft className="size-4" aria-hidden /> All circulars
      </button>
      <Card className="mb-5">
        <div className="flex flex-wrap items-start gap-3">
          <div className="min-w-0 flex-1">
            <h2 className="text-lg font-semibold">{b.title}</h2>
            <p className="text-ink-muted text-sm">
              {[
                b.audienceTags.length ? b.audienceTags.join(", ") : "All contacts",
                b.channels.map((c) => CHANNEL_LABEL[c]).join(" → "),
                `template ${b.templateName}`,
              ].join(" · ")}
            </p>
          </div>
          {b.emergency && <Badge tone="red">Urgent</Badge>}
          <Badge tone={STATUS_TONE[b.status]}>{STATUS_LABEL[b.status]}</Badge>
        </div>
        {b.details && (
          <p className="text-ink-muted mt-3 line-clamp-4 text-sm whitespace-pre-line">
            {b.details}
          </p>
        )}
        {b.status === "SCHEDULED" && b.sendAt && (
          <p className="mt-3 text-sm">
            Will send at <strong>{fmtDate(b.sendAt)}</strong>.{" "}
            <Btn size="sm" variant="ghost" busy={busy === "cancel"} onClick={() => void cancel()}>
              Cancel
            </Btn>
          </p>
        )}
      </Card>

      {b.status === "DRAFT" && p && (
        <Card className="mb-5">
          <h3 className="text-sm font-semibold">Before you send</h3>
          <div className="mt-3 grid gap-3 sm:grid-cols-3">
            <div className="rounded-xl bg-black/[0.03] p-3">
              <p className="text-2xl font-bold">{p.audience - p.skipped}</p>
              <p className="text-ink-muted text-xs">
                will receive it (
                {Object.entries(p.byChannel)
                  .map(([c, n]) => `${n} ${CHANNEL_LABEL[c]}`)
                  .join(", ") || "none"}
                )
              </p>
            </div>
            <div className="rounded-xl bg-black/[0.03] p-3">
              <p className="text-2xl font-bold">{p.skipped}</p>
              <p className="text-ink-muted text-xs">
                skipped
                {Object.keys(p.skipReasons).length
                  ? `: ${Object.entries(p.skipReasons)
                      .map(([r, n]) => `${n} ${r.toLowerCase()}`)
                      .join("; ")}`
                  : ""}
              </p>
            </div>
            <div className="rounded-xl bg-black/[0.03] p-3">
              <p className="text-2xl font-bold">{p.testMode ? "₹0" : rupees(p.costPaise)}</p>
              <p className="text-ink-muted text-xs">
                {p.testMode
                  ? "test mode: nothing is sent on WhatsApp/SMS"
                  : "estimated from your wallet"}
              </p>
            </div>
          </div>
          <p className="mt-3 text-sm">
            Sends at <strong>{fmtDate(p.willSendAt)}</strong>
            {deferred ? " (quiet hours or schedule)" : " (now)"}.
          </p>
          {p.sample && (
            <div className="mt-3">
              <p className="text-ink-muted mb-1 text-xs">Example message</p>
              <p className="rounded-xl bg-emerald-50 p-3 text-sm whitespace-pre-line">{p.sample}</p>
            </div>
          )}
          <div className="mt-4 flex flex-wrap gap-2">
            <Btn
              variant="primary"
              busy={busy === "send"}
              disabled={p.audience - p.skipped === 0}
              onClick={() => void send()}
            >
              <Send className="size-4" aria-hidden /> Send to {p.audience - p.skipped}
            </Btn>
            <Btn onClick={() => setEditing(true)}>Edit</Btn>
            <Btn variant="ghost" busy={busy === "del"} onClick={() => void remove()}>
              <Trash2 className="size-4" aria-hidden /> Delete
            </Btn>
          </div>
        </Card>
      )}

      {b.status !== "DRAFT" && (
        <>
          {(b.status === "SENT" || b.status === "CANCELLED") && (
            <div className="mb-3 text-right">
              <Btn size="sm" variant="ghost" busy={busy === "del"} onClick={() => void remove()}>
                <Trash2 className="size-3.5" aria-hidden /> Delete circular and its records
              </Btn>
            </div>
          )}
          <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {(
              [
                ["Sent", s.sent, s.testMode ? `${s.testMode} in test mode` : ""],
                ["Read", s.read, pct(s.read, s.sent)],
                b.ackRequired
                  ? ["Acknowledged", s.acknowledged, pct(s.acknowledged, s.sent)]
                  : ["Not delivered", s.failed + s.skipped, ""],
                b.consentQuestion
                  ? ["Yes / No", `${s.consentYes} / ${s.consentNo}`, b.consentQuestion]
                  : ["Failed or skipped", s.failed + s.skipped, ""],
              ] as [string, number | string, string][]
            ).map(([k, v, sub]) => (
              <div key={k} className="rounded-2xl bg-white p-4 ring-1 ring-black/5">
                <p className="text-2xl font-bold tabular-nums">{v}</p>
                <p className="text-ink-muted text-xs">{k}</p>
                {sub && <p className="text-ink-muted truncate text-xs">{sub}</p>}
              </div>
            ))}
          </div>
          <div className="mb-3 flex flex-wrap gap-1.5">
            {[
              ["", "Everyone"],
              ["unread", "Not read"],
              ...(b.ackRequired ? [["unacknowledged", "Not acknowledged"]] : []),
              ["failed", "Failed / skipped"],
            ].map(([k, label]) => (
              <Btn
                key={k}
                size="sm"
                variant={filter === k ? "primary" : "secondary"}
                onClick={() => setFilter(k!)}
              >
                {label}
              </Btn>
            ))}
          </div>
          {!rows ? (
            <Loading />
          ) : rows.length === 0 ? (
            <Empty>Nobody here.</Empty>
          ) : (
            <div className="overflow-x-auto rounded-2xl bg-white ring-1 ring-black/5">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-ink-muted text-left text-xs">
                    <th className="px-3 py-2 font-medium">Parent</th>
                    <th className="px-3 py-2 font-medium">Channel</th>
                    <th className="px-3 py-2 font-medium">Status</th>
                    <th className="px-3 py-2 font-medium">Read</th>
                    {b.ackRequired && <th className="px-3 py-2 font-medium">Acknowledged</th>}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((d) => (
                    <tr key={d.id} className="border-t border-black/5">
                      <td className="px-3 py-2">
                        {d.contactName}
                        <span className="text-ink-muted block text-xs">{d.address}</span>
                        {d.status !== "SKIPPED" && (
                          <button
                            type="button"
                            className="text-primary text-xs font-medium hover:underline"
                            onClick={() => void navigator.clipboard.writeText(d.link)}
                            aria-label={`Copy ${d.contactName}'s personal link`}
                          >
                            Copy personal link
                          </button>
                        )}
                      </td>
                      <td className="px-3 py-2">{d.channel ? CHANNEL_LABEL[d.channel] : "—"}</td>
                      <td className="px-3 py-2">
                        {d.status === "SKIPPED" || d.status === "FAILED" ? (
                          <span className="text-red-700">
                            {d.status === "SKIPPED" ? "Skipped" : "Failed"}
                            <span className="text-ink-muted block text-xs">{d.skipReason}</span>
                          </span>
                        ) : (
                          <>
                            {d.status === "QUEUED" ? "Queued" : "Sent"}
                            {d.testMode && (
                              <span className="text-ink-muted block text-xs">test mode</span>
                            )}
                          </>
                        )}
                      </td>
                      <td className="px-3 py-2">{d.readAt ? fmtDate(d.readAt) : "—"}</td>
                      {b.ackRequired && (
                        <td className="px-3 py-2">
                          {d.ackAt ? `✓ ${fmtDate(d.ackAt)}` : "—"}
                          {d.consentAnswer != null && (
                            <span className="text-ink-muted block text-xs">
                              Answer: {d.consentAnswer ? "Yes" : "No"}
                            </span>
                          )}
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  );
}
