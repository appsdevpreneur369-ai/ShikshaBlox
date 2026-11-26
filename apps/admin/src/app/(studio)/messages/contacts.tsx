"use client";

import { useCallback, useEffect, useState } from "react";
import { Plus, Search, Trash2, Upload, X } from "lucide-react";
import { sb, fmtDate } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Field, Loading, inputCls, useAction } from "@/components/kit";
import { LANGUAGES, type Contact, type TagCount } from "./types";

type ConsentEntry = {
  channel: string;
  optedIn: boolean;
  source: string;
  note?: string | null;
  actorName?: string | null;
  createdAt: string;
};
type Detail = { contact: Contact; consent: ConsentEntry[] };
type ImportResult = { added: number; updated: number; skipped: number; errors: string[] };

const SOURCE: Record<string, string> = {
  FORM: "Form",
  IMPORT: "Import",
  STAFF: "Staff",
  LINK: "Message link",
  REPLY: "Reply",
};

export function ContactsView({ tags, onChanged }: { tags: TagCount[]; onChanged: () => void }) {
  const [rows, setRows] = useState<Contact[] | null>(null);
  const [tag, setTag] = useState("");
  const [q, setQ] = useState("");
  const [sel, setSel] = useState<string | "new" | null>(null);
  const [importing, setImporting] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const p = new URLSearchParams();
      if (tag) p.set("tag", tag);
      if (q) p.set("q", q);
      setRows(await sb<Contact[]>(`comms/contacts?${p}`));
    } catch (e) {
      setErr((e as Error).message);
    }
  }, [tag, q]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 250);
    return () => clearTimeout(t);
  }, [load]);

  if (err) return <Empty>{err}</Empty>;
  const refresh = () => {
    void load();
    onChanged();
  };
  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <select
          className={`${inputCls} w-auto`}
          value={tag}
          onChange={(e) => setTag(e.target.value)}
          aria-label="Filter by tag"
        >
          <option value="">All tags</option>
          {tags.map((t) => (
            <option key={t.tag} value={t.tag}>
              {t.tag} ({t.contacts})
            </option>
          ))}
        </select>
        <div className="relative w-full sm:w-60">
          <Search className="text-ink-muted absolute top-2.5 left-3 size-4" aria-hidden />
          <input
            className={`${inputCls} pl-9`}
            placeholder="Name, phone, student"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Search contacts"
          />
        </div>
        <span className="ml-auto flex gap-2">
          <Btn onClick={() => setImporting(true)}>
            <Upload className="size-4" aria-hidden /> Import CSV
          </Btn>
          <Btn variant="primary" onClick={() => setSel("new")}>
            <Plus className="size-4" aria-hidden /> Add contact
          </Btn>
        </span>
      </div>
      {importing && <ImportCard onClose={() => setImporting(false)} onDone={refresh} />}
      <div className={`grid gap-5 ${sel ? "xl:grid-cols-[1fr_420px]" : ""}`}>
        <div className="min-w-0">
          {!rows ? (
            <Loading />
          ) : rows.length === 0 ? (
            <Empty>
              No contacts yet. Add parents one by one or import a CSV from your existing records.
            </Empty>
          ) : (
            <ul className="overflow-hidden rounded-2xl bg-white ring-1 ring-black/5">
              {rows.map((c) => (
                <li key={c.id} className="border-b border-black/5 last:border-0">
                  <button
                    type="button"
                    onClick={() => setSel(c.id)}
                    className={`flex w-full flex-wrap items-center gap-x-3 gap-y-1 px-4 py-2.5 text-left hover:bg-black/[0.02] ${sel === c.id ? "bg-primary/5" : ""}`}
                  >
                    <span className="min-w-[11rem] flex-1">
                      <span className="block text-sm font-semibold">{c.name}</span>
                      <span className="text-ink-muted block text-xs">
                        {[c.phone, c.email, c.studentNames].filter(Boolean).join(" · ")}
                      </span>
                    </span>
                    <span className="flex flex-wrap gap-1">
                      {c.tags.map((t) => (
                        <Badge key={t}>{t}</Badge>
                      ))}
                    </span>
                    <span className="flex gap-1">
                      <Badge tone={c.whatsappOptIn ? "green" : "gray"}>WA</Badge>
                      <Badge tone={c.smsOptIn ? "green" : "gray"}>SMS</Badge>
                      <Badge tone={c.emailOptIn ? "green" : "gray"}>Email</Badge>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        {sel && (
          <ContactPanel
            key={sel}
            id={sel === "new" ? null : sel}
            onClose={() => setSel(null)}
            onSaved={(c) => {
              refresh();
              setSel(c.id);
            }}
            onDeleted={() => {
              setSel(null);
              refresh();
            }}
          />
        )}
      </div>
    </div>
  );
}

function ContactPanel({
  id,
  onClose,
  onSaved,
  onDeleted,
}: {
  id: string | null;
  onClose: () => void;
  onSaved: (c: Contact) => void;
  onDeleted: () => void;
}) {
  const [d, setD] = useState<Detail | null>(null);
  const [f, setF] = useState({
    name: "",
    phone: "",
    email: "",
    language: "en",
    tags: "",
    studentNames: "",
    whatsappOptIn: false,
    smsOptIn: false,
    emailOptIn: false,
    consentNote: "",
  });
  const { busy, run } = useAction();

  useEffect(() => {
    if (!id) return;
    void sb<Detail>(`comms/contacts/${id}`).then((r) => {
      setD(r);
      const c = r.contact;
      setF({
        name: c.name,
        phone: c.phone ?? "",
        email: c.email ?? "",
        language: c.language,
        tags: c.tags.join(", "),
        studentNames: c.studentNames ?? "",
        whatsappOptIn: c.whatsappOptIn,
        smsOptIn: c.smsOptIn,
        emailOptIn: c.emailOptIn,
        consentNote: "",
      });
    });
  }, [id]);

  async function save() {
    const body = {
      ...f,
      phone: f.phone,
      email: f.email,
      tags: f.tags
        .split(",")
        .map((t) => t.trim())
        .filter(Boolean),
      consentNote: f.consentNote || null,
    };
    const r = await run(
      "save",
      () =>
        id
          ? sb<Contact>(`comms/contacts/${id}`, { method: "PATCH", body })
          : sb<Contact>("comms/contacts", { body }),
      "Saved",
    );
    if (r) onSaved(r);
  }
  async function remove() {
    if (!id || !confirm(`Delete ${f.name}? Their consent history is deleted too.`)) return;
    if (
      await run(
        "del",
        () => sb(`comms/contacts/${id}`, { method: "DELETE" }).then(() => true),
        "Deleted",
      )
    )
      onDeleted();
  }

  if (id && !d) {
    return (
      <Card className="h-fit">
        <p className="text-ink-muted text-sm">Loading…</p>
      </Card>
    );
  }
  return (
    <Card className="h-fit xl:sticky xl:top-24">
      <div className="flex items-start justify-between">
        <h2 className="text-lg font-semibold">{id ? f.name : "New contact"}</h2>
        <button
          type="button"
          onClick={onClose}
          className="text-ink-muted hover:text-ink rounded-full p-1.5 hover:bg-black/5"
          aria-label="Close"
        >
          <X className="size-4" />
        </button>
      </div>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <Field label="Parent / guardian name *">
          <input
            className={inputCls}
            maxLength={120}
            value={f.name}
            onChange={(e) => setF({ ...f, name: e.target.value })}
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
        <Field label="Mobile number">
          <input
            className={inputCls}
            type="tel"
            maxLength={20}
            value={f.phone}
            onChange={(e) => setF({ ...f, phone: e.target.value })}
          />
        </Field>
        <Field label="Email">
          <input
            className={inputCls}
            type="email"
            maxLength={200}
            value={f.email}
            onChange={(e) => setF({ ...f, email: e.target.value })}
          />
        </Field>
        <div className="sm:col-span-2">
          <Field
            label="Tags (comma-separated)"
            hint="Use classes, sections, bus routes: Class 3A, Bus 4"
          >
            <input
              className={inputCls}
              value={f.tags}
              onChange={(e) => setF({ ...f, tags: e.target.value })}
            />
          </Field>
        </div>
        <div className="sm:col-span-2">
          <Field label="Children">
            <input
              className={inputCls}
              maxLength={300}
              value={f.studentNames}
              onChange={(e) => setF({ ...f, studentNames: e.target.value })}
              placeholder="e.g. Aarav (3A), Diya (1B)"
            />
          </Field>
        </div>
      </div>
      <fieldset className="mt-4 rounded-xl bg-black/[0.03] p-3">
        <legend className="px-1 text-sm font-medium">
          Opt-in (only with the parent&apos;s consent)
        </legend>
        <div className="flex flex-wrap gap-4 text-sm">
          {(
            [
              ["whatsappOptIn", "WhatsApp"],
              ["smsOptIn", "SMS"],
              ["emailOptIn", "Email"],
            ] as const
          ).map(([k, label]) => (
            <label key={k} className="flex items-center gap-2">
              <input
                type="checkbox"
                className="size-4"
                checked={f[k]}
                onChange={(e) => setF({ ...f, [k]: e.target.checked })}
              />{" "}
              {label}
            </label>
          ))}
        </div>
        <input
          className={`${inputCls} mt-2`}
          maxLength={300}
          value={f.consentNote}
          onChange={(e) => setF({ ...f, consentNote: e.target.value })}
          placeholder="How consent was given, e.g. admission form 2027, phone call"
          aria-label="How consent was given"
        />
      </fieldset>
      <div className="mt-4 flex flex-wrap gap-2">
        <Btn variant="primary" busy={busy === "save"} onClick={() => void save()}>
          Save
        </Btn>
        {id && (
          <Btn variant="ghost" busy={busy === "del"} onClick={() => void remove()}>
            <Trash2 className="size-4" aria-hidden /> Delete
          </Btn>
        )}
      </div>
      {d && d.consent.length > 0 && (
        <div className="mt-5">
          <p className="mb-2 text-sm font-medium">Consent history</p>
          <ol className="space-y-1.5 text-xs">
            {d.consent.map((e, i) => (
              <li key={i} className="flex flex-wrap gap-x-2">
                <span
                  className={
                    e.optedIn ? "font-semibold text-emerald-700" : "font-semibold text-red-700"
                  }
                >
                  {e.optedIn ? "Opted in" : "Opted out"} ·{" "}
                  {e.channel === "WHATSAPP" ? "WhatsApp" : e.channel === "SMS" ? "SMS" : "Email"}
                </span>
                <span className="text-ink-muted">
                  {fmtDate(e.createdAt)} · {SOURCE[e.source] ?? e.source}
                  {e.actorName ? ` · ${e.actorName}` : ""}
                  {e.note ? ` · ${e.note}` : ""}
                </span>
              </li>
            ))}
          </ol>
        </div>
      )}
    </Card>
  );
}

function ImportCard({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [csv, setCsv] = useState("");
  const [note, setNote] = useState("");
  const [res, setRes] = useState<ImportResult | null>(null);
  const { busy, run } = useAction();
  async function go() {
    const r = await run("imp", () =>
      sb<ImportResult>("comms/contacts/import", { body: { csv, consentNote: note || null } }),
    );
    if (r) {
      setRes(r);
      onDone();
    }
  }
  return (
    <Card className="mb-5">
      <div className="flex items-start justify-between">
        <h2 className="text-lg font-semibold">Import contacts</h2>
        <button
          type="button"
          onClick={onClose}
          className="text-ink-muted hover:text-ink rounded-full p-1.5 hover:bg-black/5"
          aria-label="Close"
        >
          <X className="size-4" />
        </button>
      </div>
      <p className="text-ink-muted mt-1 text-sm">
        CSV with a header row:{" "}
        <code>name,phone,email,language,tags,students,whatsapp,sms,email_opt_in</code>. Tags are
        separated by <code>;</code>, opt-ins are yes/no. Existing contacts (same phone) are updated.
        Mark opt-ins only where parents have agreed.
      </p>
      <label className="mt-3 inline-flex h-10 cursor-pointer items-center gap-1.5 rounded-full bg-white px-4 text-sm font-medium ring-1 ring-black/10">
        <Upload className="size-4" aria-hidden /> Choose CSV file
        <input
          type="file"
          accept=".csv,text/csv"
          className="sr-only"
          onChange={async (e) => setCsv((await e.target.files?.[0]?.text()) ?? "")}
        />
      </label>
      <textarea
        className={`${inputCls} mt-3 font-mono text-xs`}
        rows={5}
        value={csv}
        onChange={(e) => setCsv(e.target.value)}
        placeholder={
          "name,phone,email,language,tags,students,whatsapp,sms,email_opt_in\nPriya Sharma,+91 98480 00000,,en,Class 3A;Bus 4,Aarav (3A),yes,no,no"
        }
        aria-label="CSV contents"
      />
      <input
        className={`${inputCls} mt-2`}
        maxLength={300}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="How these parents gave consent, e.g. admission forms 2026-27"
        aria-label="Consent note"
      />
      <Btn
        variant="primary"
        className="mt-3"
        busy={busy === "imp"}
        disabled={!csv.trim()}
        onClick={() => void go()}
      >
        Import
      </Btn>
      {res && (
        <div className="mt-3 text-sm">
          <p>
            Added {res.added}, updated {res.updated}, skipped {res.skipped}.
          </p>
          {res.errors.length > 0 && (
            <ul className="mt-1 list-disc pl-5 text-xs text-red-700">
              {res.errors.map((e, i) => (
                <li key={i}>{e}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Card>
  );
}
