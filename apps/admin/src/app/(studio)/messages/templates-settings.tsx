"use client";

import { useEffect, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { sb, fmtDate } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Field, Loading, inputCls, useAction } from "@/components/kit";
import { LANGUAGES, type Settings, type Template } from "./types";

const WA_TONE: Record<string, "gray" | "amber" | "green" | "red"> = {
  DRAFT: "gray",
  SUBMITTED: "amber",
  APPROVED: "green",
  REJECTED: "red",
};
const WA_LABEL: Record<string, string> = {
  DRAFT: "Draft",
  SUBMITTED: "Submitted to Meta",
  APPROVED: "Approved",
  REJECTED: "Rejected",
};

export function TemplatesView({
  templates,
  onChanged,
}: {
  templates: Template[];
  onChanged: () => void;
}) {
  const [editing, setEditing] = useState<Template | "new" | null>(null);
  if (editing)
    return (
      <TemplateEditor
        t={editing === "new" ? null : editing}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null);
          onChanged();
        }}
      />
    );
  return (
    <div>
      <Btn variant="primary" className="mb-4" onClick={() => setEditing("new")}>
        <Plus className="size-4" aria-hidden /> New template
      </Btn>
      <p className="text-ink-muted mb-4 text-sm">
        Templates are the short messages parents receive; the full circular stays behind the
        personal link. WhatsApp needs each template approved by Meta, and SMS in India needs a
        DLT-registered template id. Add a language variant (same name) for parents who prefer
        another language.
      </p>
      {templates.length === 0 ? (
        <Empty>No templates yet.</Empty>
      ) : (
        <ul className="overflow-hidden rounded-2xl bg-white ring-1 ring-black/5">
          {templates.map((t) => (
            <li key={t.id} className="border-b border-black/5 last:border-0">
              <button
                type="button"
                onClick={() => setEditing(t)}
                className="flex w-full flex-wrap items-start gap-x-3 gap-y-1 px-4 py-3 text-left hover:bg-black/[0.02]"
              >
                <span className="min-w-[12rem] flex-1">
                  <span className="block text-sm font-semibold">
                    {t.name}{" "}
                    <span className="text-ink-muted font-normal">
                      · {LANGUAGES[t.language] ?? t.language}
                    </span>
                  </span>
                  <span className="text-ink-muted block text-xs">{t.body}</span>
                </span>
                <span className="flex flex-wrap gap-1">
                  <Badge tone={WA_TONE[t.whatsappStatus]}>WA: {WA_LABEL[t.whatsappStatus]}</Badge>
                  <Badge tone={t.smsDltId ? "green" : "gray"}>
                    {t.smsDltId ? "SMS ready" : "No DLT id"}
                  </Badge>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function TemplateEditor({
  t,
  onClose,
  onSaved,
}: {
  t: Template | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [f, setF] = useState({
    name: t?.name ?? "",
    language: t?.language ?? "en",
    category: t?.category ?? "UTILITY",
    subject: t?.subject ?? "{{school}}: {{title}}",
    body: t?.body ?? "Dear {{name}}, {{school}} has shared: {{title}}. Read it here: {{link}}",
    whatsappStatus: t?.whatsappStatus ?? "DRAFT",
    smsDltId: t?.smsDltId ?? "",
  });
  const { busy, run } = useAction();
  async function save() {
    const body = { ...f, subject: f.subject || null, smsDltId: f.smsDltId || null };
    if (
      await run(
        "save",
        () =>
          t
            ? sb(`comms/templates/${t.id}`, { method: "PUT", body })
            : sb("comms/templates", { body }),
        "Template saved",
      )
    )
      onSaved();
  }
  async function remove() {
    if (!t || !confirm(`Delete the ${LANGUAGES[t.language] ?? t.language} version of ${t.name}?`))
      return;
    if (
      await run(
        "del",
        () => sb(`comms/templates/${t.id}`, { method: "DELETE" }).then(() => true),
        "Deleted",
      )
    )
      onSaved();
  }
  return (
    <Card>
      <h2 className="text-lg font-semibold">{t ? "Edit template" : "New template"}</h2>
      <div className="mt-4 grid gap-3 sm:grid-cols-3">
        <Field label="Name *" hint="lowercase_with_underscores">
          <input
            className={inputCls}
            maxLength={60}
            value={f.name}
            onChange={(e) =>
              setF({ ...f, name: e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, "_") })
            }
          />
        </Field>
        <Field label="Language">
          <select
            className={inputCls}
            value={f.language}
            onChange={(e) => setF({ ...f, language: e.target.value })}
          >
            {Object.entries(LANGUAGES).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Category">
          <select
            className={inputCls}
            value={f.category}
            onChange={(e) => setF({ ...f, category: e.target.value })}
          >
            <option value="UTILITY">Utility (notices, alerts)</option>
            <option value="MARKETING">Marketing (admissions, events)</option>
          </select>
        </Field>
        <div className="sm:col-span-3">
          <Field
            label="Message *"
            hint="Placeholders: {{name}} {{school}} {{title}} {{link}}. The link is added if you leave it out."
          >
            <textarea
              className={inputCls}
              rows={3}
              maxLength={1000}
              value={f.body}
              onChange={(e) => setF({ ...f, body: e.target.value })}
            />
          </Field>
        </div>
        <div className="sm:col-span-3">
          <Field label="Email subject">
            <input
              className={inputCls}
              maxLength={160}
              value={f.subject}
              onChange={(e) => setF({ ...f, subject: e.target.value })}
            />
          </Field>
        </div>
        <Field
          label="WhatsApp approval"
          hint="Set to Approved once Meta approves it in your WhatsApp account"
        >
          <select
            className={inputCls}
            value={f.whatsappStatus}
            onChange={(e) => setF({ ...f, whatsappStatus: e.target.value })}
          >
            {Object.entries(WA_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </Field>
        <Field label="SMS DLT template id" hint="From your DLT portal; needed for SMS">
          <input
            className={inputCls}
            inputMode="numeric"
            maxLength={25}
            value={f.smsDltId}
            onChange={(e) => setF({ ...f, smsDltId: e.target.value.replace(/\D/g, "") })}
          />
        </Field>
      </div>
      <div className="mt-5 flex flex-wrap gap-2">
        <Btn variant="primary" busy={busy === "save"} onClick={() => void save()}>
          Save
        </Btn>
        <Btn variant="ghost" onClick={onClose}>
          Cancel
        </Btn>
        {t && (
          <Btn
            variant="ghost"
            className="ml-auto"
            busy={busy === "del"}
            onClick={() => void remove()}
          >
            <Trash2 className="size-4" aria-hidden /> Delete
          </Btn>
        )}
      </div>
    </Card>
  );
}

type WalletEntry = {
  amountPaise: number;
  reason: string;
  actorName?: string | null;
  createdAt: string;
};
const rupees = (p: number) => `₹${(p / 100).toLocaleString("en-IN", { minimumFractionDigits: 2 })}`;

export function SettingsView() {
  const [s, setS] = useState<Settings | null>(null);
  const [wallet, setWallet] = useState<WalletEntry[]>([]);
  const [f, setF] = useState({ quietStart: "21:00", quietEnd: "07:00", monthlyCap: "5000" });
  const { busy, run } = useAction();
  useEffect(() => {
    void sb<Settings>("comms/settings").then((r) => {
      setS(r);
      setF({
        quietStart: r.quietStart.slice(0, 5),
        quietEnd: r.quietEnd.slice(0, 5),
        monthlyCap: String(r.monthlyCap),
      });
    });
    void sb<WalletEntry[]>("comms/wallet").then(setWallet);
  }, []);
  if (!s) return <Loading />;
  async function save() {
    const r = await run(
      "save",
      () =>
        sb<Settings>("comms/settings", {
          method: "PUT",
          body: {
            quietStart: f.quietStart,
            quietEnd: f.quietEnd,
            monthlyCap: Number(f.monthlyCap),
          },
        }),
      "Settings saved",
    );
    if (r) setS(r);
  }
  const test = s.provider === "TEST";
  return (
    <div className="grid gap-5 lg:grid-cols-2">
      <Card>
        <h2 className="text-lg font-semibold">Sending rules</h2>
        <div className="mt-4 grid gap-3 sm:grid-cols-3">
          <Field label="Quiet hours from">
            <input
              className={inputCls}
              type="time"
              value={f.quietStart}
              onChange={(e) => setF({ ...f, quietStart: e.target.value })}
            />
          </Field>
          <Field label="until">
            <input
              className={inputCls}
              type="time"
              value={f.quietEnd}
              onChange={(e) => setF({ ...f, quietEnd: e.target.value })}
            />
          </Field>
          <Field label="Monthly limit">
            <input
              className={inputCls}
              type="number"
              min={0}
              max={1000000}
              value={f.monthlyCap}
              onChange={(e) => setF({ ...f, monthlyCap: e.target.value })}
            />
          </Field>
        </div>
        <p className="text-ink-muted mt-2 text-sm">
          Non-urgent messages wait until quiet hours end. {s.sentThisMonth} of {s.monthlyCap}{" "}
          messages used this month.
        </p>
        <Btn variant="primary" className="mt-3" busy={busy === "save"} onClick={() => void save()}>
          Save
        </Btn>
      </Card>
      <Card>
        <h2 className="text-lg font-semibold">WhatsApp &amp; SMS account</h2>
        {test ? (
          <p className="mt-2 rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900">
            <strong>Test mode.</strong> WhatsApp and SMS messages are recorded but not sent, so you
            can try circulars, receipts and acknowledgements. Email is sent normally. Contact
            ShikshaBlox to connect your school&apos;s WhatsApp Business number and DLT SMS sender.
          </p>
        ) : (
          <p className="mt-2 text-sm">
            Connected: {s.provider === "META" ? "WhatsApp Cloud API" : "Gupshup"}.
          </p>
        )}
        <div className="mt-4 grid grid-cols-3 gap-3 text-center">
          <div className="rounded-xl bg-black/[0.03] p-3">
            <p className="text-xl font-bold">{rupees(s.balancePaise)}</p>
            <p className="text-ink-muted text-xs">Wallet balance</p>
          </div>
          <div className="rounded-xl bg-black/[0.03] p-3">
            <p className="text-xl font-bold">{test ? "—" : rupees(s.whatsappRatePaise)}</p>
            <p className="text-ink-muted text-xs">per WhatsApp</p>
          </div>
          <div className="rounded-xl bg-black/[0.03] p-3">
            <p className="text-xl font-bold">{test ? "—" : rupees(s.smsRatePaise)}</p>
            <p className="text-ink-muted text-xs">per SMS</p>
          </div>
        </div>
        <p className="text-ink-muted mt-2 text-xs">
          Messaging is prepaid at the provider&apos;s cost plus 10%. Email is included.
        </p>
        {wallet.length > 0 && (
          <ul className="mt-3 divide-y divide-black/5 text-sm">
            {wallet.slice(0, 10).map((w, i) => (
              <li key={i} className="flex justify-between gap-3 py-1.5">
                <span className="min-w-0 truncate">
                  {w.reason}{" "}
                  <span className="text-ink-muted text-xs">· {fmtDate(w.createdAt)}</span>
                </span>
                <span className={`tabular-nums ${w.amountPaise < 0 ? "" : "text-emerald-700"}`}>
                  {rupees(w.amountPaise)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
