"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  Archive,
  Check,
  Download,
  FileArchive,
  Plus,
  RotateCcw,
  Sparkles,
  Trash2,
  Upload,
  X,
} from "lucide-react";
import { sb, StudioError, fmtDate } from "@/lib/studio";
import {
  Badge,
  Btn,
  Card,
  Empty,
  Field,
  Loading,
  PageHeader,
  inputCls,
  useAction,
} from "@/components/kit";

type State = "OVERDUE" | "DUE_SOON" | "UPCOMING" | "NO_DATE" | "DONE";
type Item = {
  id: string;
  title: string;
  category: string;
  authority?: string | null;
  description?: string | null;
  recurrence: string;
  dueDate?: string | null;
  remindDays: number;
  ownerId?: string | null;
  ownerName?: string | null;
  active: boolean;
  state: State;
  daysLeft?: number | null;
  lastCompletedOn?: string | null;
  documents: number;
};
type Doc = {
  id: string;
  itemId?: string | null;
  itemTitle?: string | null;
  title: string;
  category: string;
  referenceNo?: string | null;
  issuedOn?: string | null;
  expiresOn?: string | null;
  filename: string;
  sizeBytes: number;
  expiry: "NO_EXPIRY" | "EXPIRED" | "EXPIRING" | "VALID";
  daysLeft?: number | null;
  uploadedByName?: string | null;
  createdAt: string;
};
type Completion = {
  id: string;
  dueDate?: string | null;
  completedOn: string;
  note?: string | null;
  documentId?: string | null;
  documentTitle?: string | null;
  completedByName?: string | null;
};
type Detail = { item: Item; completions: Completion[]; documents: Doc[] };
type Summary = {
  overdue: number;
  dueSoon: number;
  noDate: number;
  expiringDocuments: number;
  expiredDocuments: number;
};
type Template = {
  key: string;
  title: string;
  category: string;
  authority?: string | null;
  recurrence: string;
  description?: string | null;
};
type Person = { id: string; name: string };
type View = "calendar" | "vault" | "suggestions" | "archived";

const CATEGORY: Record<string, string> = {
  AFFILIATION: "Affiliation",
  SAFETY: "Safety",
  HEALTH: "Health",
  STATUTORY: "Statutory",
  FINANCE: "Finance",
  STAFF: "Staff",
  TRANSPORT: "Transport",
  DISCLOSURE: "Disclosure",
  OTHER: "Other",
};
const FREQ: Record<string, string> = {
  ONCE: "One time",
  MONTHLY: "Monthly",
  QUARTERLY: "Quarterly",
  HALF_YEARLY: "Every 6 months",
  YEARLY: "Yearly",
};
const STATE_LABEL: Record<State, string> = {
  OVERDUE: "Overdue",
  DUE_SOON: "Due soon",
  UPCOMING: "Upcoming",
  NO_DATE: "Set a date",
  DONE: "Done",
};
const STATE_TONE: Record<State, "red" | "amber" | "blue" | "gray" | "green"> = {
  OVERDUE: "red",
  DUE_SOON: "amber",
  UPCOMING: "blue",
  NO_DATE: "gray",
  DONE: "green",
};
const EXPIRY_LABEL: Record<Doc["expiry"], string> = {
  EXPIRED: "Expired",
  EXPIRING: "Expiring",
  VALID: "Valid",
  NO_EXPIRY: "No expiry",
};
const EXPIRY_TONE: Record<Doc["expiry"], "red" | "amber" | "green" | "gray"> = {
  EXPIRED: "red",
  EXPIRING: "amber",
  VALID: "green",
  NO_EXPIRY: "gray",
};

const today = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
const fmtDay = (d?: string | null) =>
  d
    ? new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(
        new Date(`${d}T00:00:00`),
      )
    : "—";
const kb = (n: number) =>
  n > 1024 * 1024
    ? `${(n / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(n / 1024))} KB`;
const when = (i: Item) =>
  i.daysLeft == null
    ? ""
    : i.daysLeft < 0
      ? `${-i.daysLeft} day${i.daysLeft === -1 ? "" : "s"} overdue`
      : i.daysLeft === 0
        ? "due today"
        : `in ${i.daysLeft} day${i.daysLeft === 1 ? "" : "s"}`;

async function downloadDoc(id: string) {
  const r = await sb<{ url: string }>(`compliance/documents/${id}/download`);
  const a = document.createElement("a");
  a.href = r.url;
  a.rel = "noopener noreferrer";
  document.body.appendChild(a);
  a.click();
  a.remove();
}

export function CompliancePage({ canOwn, canExport }: { canOwn: boolean; canExport: boolean }) {
  const [view, setView] = useState<View>("calendar");
  const [items, setItems] = useState<Item[] | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [blocked, setBlocked] = useState<string | null>(null);
  const [sel, setSel] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [people, setPeople] = useState<Person[]>([]);
  const [review, setReview] = useState<{ reviewedAt?: string | null; due: boolean } | null>(null);
  const { busy, run } = useAction();

  const load = useCallback(async () => {
    try {
      const [i, s] = await Promise.all([
        sb<Item[]>(`compliance/items${view === "archived" ? "?archived=true" : ""}`),
        sb<Summary>("compliance/summary"),
      ]);
      setItems(view === "archived" ? i.filter((x) => !x.active) : i);
      setSummary(s);
    } catch (e) {
      if (e instanceof StudioError && e.status === 403) setBlocked(e.message);
    }
  }, [view]);

  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  useEffect(() => {
    if (canOwn)
      sb<Person[]>("compliance/people")
        .then(setPeople)
        .catch(() => {});
    sb<{ reviewedAt?: string | null; due: boolean }>("disclosures/review")
      .then(setReview)
      .catch(() => {});
  }, [canOwn]);

  async function exportPack() {
    const r = await run("export", async () => {
      const res = await fetch("/api/backend/compliance/export.zip", { cache: "no-store" });
      if (!res.ok) {
        const p = (await res.json().catch(() => ({}))) as { detail?: string };
        throw new Error(p.detail ?? "The export could not be created.");
      }
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement("a");
      a.href = url;
      a.download = `compliance-pack-${today()}.zip`;
      a.click();
      URL.revokeObjectURL(url);
      return true;
    });
    return r;
  }

  if (blocked) {
    return (
      <div>
        <PageHeader title="Compliance" />
        <Card className="max-w-2xl">
          <h2 className="text-lg font-semibold">
            The compliance calendar is not part of your plan yet
          </h2>
          <p className="text-ink-muted mt-2 text-sm">
            Upgrade to ShikshaBlox Lite or Standard to track certificates, inspections and statutory
            returns with reminders before they fall due.
          </p>
        </Card>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Compliance"
        subtitle="Certificates, inspections and returns, with reminders before they fall due."
        actions={
          <>
            {canExport && (
              <Btn onClick={() => void exportPack()} busy={busy === "export"}>
                <FileArchive className="size-4" aria-hidden /> Inspection pack
              </Btn>
            )}
            <Btn variant="primary" onClick={() => setAdding(true)}>
              <Plus className="size-4" aria-hidden /> Add item
            </Btn>
          </>
        }
      />

      {summary && (
        <div className="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {(
            [
              ["Overdue", summary.overdue, "text-red-600"],
              ["Due soon", summary.dueSoon, "text-amber-700"],
              ["Certificates expiring (60 days)", summary.expiringDocuments, "text-amber-700"],
              ["Need a due date", summary.noDate, "text-ink"],
            ] as [string, number, string][]
          ).map(([k, v, c]) => (
            <div key={k} className="rounded-2xl bg-white p-4 ring-1 ring-black/5">
              <p className={`text-2xl font-bold tabular-nums ${v ? c : "text-ink"}`}>{v}</p>
              <p className="text-ink-muted text-xs">{k}</p>
            </div>
          ))}
        </div>
      )}

      {review && (
        <p
          className={`mb-4 rounded-xl px-4 py-2.5 text-sm ${review.due ? "bg-amber-50 text-amber-900" : "bg-emerald-50 text-emerald-900"}`}
        >
          Website disclosures{" "}
          {review.reviewedAt
            ? `last reviewed ${fmtDate(review.reviewedAt)}`
            : "have not been reviewed yet"}
          .{" "}
          {review.due && (
            <Link href="/content/disclosures" className="font-medium underline">
              Review them now
            </Link>
          )}
        </p>
      )}

      <div className="mb-4 flex flex-wrap gap-2">
        {(
          [
            ["calendar", "Calendar"],
            ["vault", "Evidence vault"],
            ["suggestions", "Suggestions"],
            ["archived", "Archived"],
          ] as [View, string][]
        ).map(([v, label]) => (
          <button
            key={v}
            type="button"
            onClick={() => {
              setView(v);
              setSel(null);
            }}
            className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition ${view === v ? "bg-primary text-on-primary" : "text-ink-muted hover:text-ink bg-white ring-1 ring-black/10"}`}
          >
            {label}
          </button>
        ))}
      </div>

      {adding && (
        <ItemForm
          people={canOwn ? people : []}
          onClose={() => setAdding(false)}
          onSaved={(i) => {
            setAdding(false);
            void load();
            setSel(i.id);
          }}
        />
      )}

      {view === "vault" ? (
        <Vault items={items ?? []} onChanged={() => void load()} />
      ) : view === "suggestions" ? (
        <Suggestions
          onAdded={() => {
            setView("calendar");
            void load();
          }}
        />
      ) : (
        <div className={`grid gap-5 ${sel ? "xl:grid-cols-[1fr_440px]" : ""}`}>
          <div className="min-w-0">
            {!items ? (
              <Loading />
            ) : items.length === 0 ? (
              view === "archived" ? (
                <Empty>No archived items.</Empty>
              ) : (
                <Empty>
                  Nothing on the calendar yet. Start from{" "}
                  <button
                    type="button"
                    className="text-primary font-medium underline"
                    onClick={() => setView("suggestions")}
                  >
                    suggestions
                  </button>{" "}
                  (fire NOC, water tests, UDISE+, returns…) or add your own item.
                </Empty>
              )
            ) : (
              <Calendar items={items} selected={sel} onOpen={setSel} />
            )}
          </div>
          {sel && (
            <ItemPanel
              key={sel}
              id={sel}
              people={canOwn ? people : []}
              onClose={() => setSel(null)}
              onChanged={() => void load()}
            />
          )}
        </div>
      )}
    </div>
  );
}

function Calendar({
  items,
  selected,
  onOpen,
}: {
  items: Item[];
  selected: string | null;
  onOpen: (id: string) => void;
}) {
  const groups: [string, Item[]][] = [
    ["Overdue", items.filter((i) => i.state === "OVERDUE")],
    ["Due soon", items.filter((i) => i.state === "DUE_SOON")],
    ["Needs a due date", items.filter((i) => i.state === "NO_DATE")],
    ["Upcoming", items.filter((i) => i.state === "UPCOMING")],
    ["Done", items.filter((i) => i.state === "DONE")],
  ];
  return (
    <div className="space-y-5">
      {groups
        .filter(([, list]) => list.length)
        .map(([label, list]) => (
          <section key={label}>
            <h2 className="text-ink-muted mb-2 text-xs font-semibold tracking-wide uppercase">
              {label} · {list.length}
            </h2>
            <ul className="overflow-hidden rounded-2xl bg-white ring-1 ring-black/5">
              {list.map((i) => (
                <li key={i.id} className="border-b border-black/5 last:border-0">
                  <button
                    type="button"
                    onClick={() => onOpen(i.id)}
                    className={`flex w-full flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3 text-left hover:bg-black/[0.02] ${selected === i.id ? "bg-primary/5" : ""}`}
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-semibold sm:truncate">{i.title}</span>
                      <span className="text-ink-muted block text-xs sm:truncate">
                        {[CATEGORY[i.category], FREQ[i.recurrence], i.authority, i.ownerName]
                          .filter(Boolean)
                          .join(" · ")}
                      </span>
                    </span>
                    <span className="text-right text-sm">
                      {i.dueDate ? fmtDay(i.dueDate) : ""}
                      <span className="text-ink-muted block text-xs">{when(i)}</span>
                    </span>
                    <Badge tone={STATE_TONE[i.state]}>{STATE_LABEL[i.state]}</Badge>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ))}
    </div>
  );
}

function ItemForm({
  item,
  people,
  onClose,
  onSaved,
}: {
  item?: Item;
  people: Person[];
  onClose: () => void;
  onSaved: (i: Item) => void;
}) {
  const [f, setF] = useState({
    title: item?.title ?? "",
    category: item?.category ?? "SAFETY",
    authority: item?.authority ?? "",
    recurrence: item?.recurrence ?? "YEARLY",
    dueDate: item?.dueDate ?? "",
    remindDays: String(item?.remindDays ?? 30),
    ownerId: item?.ownerId ?? "",
    description: item?.description ?? "",
  });
  const { busy, run } = useAction();
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF({ ...f, [k]: e.target.value });

  async function save(e: React.FormEvent) {
    e.preventDefault();
    const body = {
      title: f.title,
      category: f.category,
      authority: f.authority,
      recurrence: f.recurrence,
      dueDate: f.dueDate || null,
      remindDays: Number(f.remindDays),
      ownerId: f.ownerId || null,
      description: f.description,
    };
    const r = await run(
      "save",
      () =>
        item
          ? sb<Item>(`compliance/items/${item.id}`, { method: "PATCH", body })
          : sb<Item>("compliance/items", { body }),
      item ? "Saved" : "Item added",
    );
    if (r) onSaved(r);
  }

  return (
    <Card className="mb-5">
      <form onSubmit={(e) => void save(e)}>
        <h2 className="text-lg font-semibold">{item ? "Edit item" : "Add a compliance item"}</h2>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <div className="sm:col-span-2">
            <Field label="What needs to be done *">
              <input
                className={inputCls}
                required
                maxLength={160}
                value={f.title}
                onChange={set("title")}
                placeholder="e.g. Fire safety certificate renewal"
              />
            </Field>
          </div>
          <Field label="Category">
            <select className={inputCls} value={f.category} onChange={set("category")}>
              {Object.entries(CATEGORY).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Next due date">
            <input className={inputCls} type="date" value={f.dueDate} onChange={set("dueDate")} />
          </Field>
          <Field label="Repeats">
            <select className={inputCls} value={f.recurrence} onChange={set("recurrence")}>
              {Object.entries(FREQ).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Remind me (days before)">
            <input
              className={inputCls}
              type="number"
              min={0}
              max={180}
              value={f.remindDays}
              onChange={set("remindDays")}
            />
          </Field>
          <Field label="Authority / agency">
            <input
              className={inputCls}
              maxLength={120}
              value={f.authority}
              onChange={set("authority")}
              placeholder="e.g. Fire department"
            />
          </Field>
          {people.length > 0 && (
            <Field label="Owner">
              <select className={inputCls} value={f.ownerId} onChange={set("ownerId")}>
                <option value="">School admins</option>
                {people.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </Field>
          )}
          <div className="sm:col-span-2 lg:col-span-3">
            <Field label="Notes">
              <textarea
                className={inputCls}
                rows={2}
                maxLength={2000}
                value={f.description}
                onChange={set("description")}
              />
            </Field>
          </div>
        </div>
        <div className="mt-4 flex gap-2">
          <Btn type="submit" variant="primary" busy={busy === "save"}>
            {item ? "Save" : "Add item"}
          </Btn>
          <Btn variant="ghost" onClick={onClose}>
            Cancel
          </Btn>
        </div>
      </form>
    </Card>
  );
}

function ItemPanel({
  id,
  people,
  onClose,
  onChanged,
}: {
  id: string;
  people: Person[];
  onClose: () => void;
  onChanged: () => void;
}) {
  const [d, setD] = useState<Detail | null>(null);
  const [editing, setEditing] = useState(false);
  const [on, setOn] = useState(today());
  const [note, setNote] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [expires, setExpires] = useState("");
  const { busy, run } = useAction();

  const reload = useCallback(async () => setD(await sb<Detail>(`compliance/items/${id}`)), [id]);
  useEffect(() => {
    const t = setTimeout(() => void reload(), 0);
    return () => clearTimeout(t);
  }, [reload]);

  if (!d) {
    return (
      <Card className="h-fit">
        <p className="text-ink-muted text-sm">Loading…</p>
      </Card>
    );
  }
  const i = d.item;

  async function complete() {
    const r = await run(
      "done",
      async () => {
        let documentId: string | undefined;
        if (file) {
          const form = new FormData();
          form.set("file", file);
          form.set("title", `${i.title} – ${fmtDay(on)}`);
          form.set("category", i.category);
          form.set("itemId", i.id);
          form.set("issuedOn", on);
          if (expires) form.set("expiresOn", expires);
          documentId = (await sb<Doc>("compliance/documents", { form })).id;
        }
        return sb<Detail>(`compliance/items/${i.id}/complete`, {
          body: { completedOn: on, note, documentId },
        });
      },
      "Marked done",
    );
    if (r) {
      setD(r);
      setNote("");
      setFile(null);
      setExpires("");
      onChanged();
    }
  }

  async function remove() {
    if (!confirm(`Delete “${i.title}” and its history? Its documents stay in the evidence vault.`))
      return;
    const r = await run(
      "delete",
      () => sb(`compliance/items/${i.id}`, { method: "DELETE" }).then(() => true),
      "Deleted",
    );
    if (r) {
      onChanged();
      onClose();
    }
  }

  async function archive(active: boolean) {
    const r = await run(
      "archive",
      () => sb<Item>(`compliance/items/${i.id}/active`, { body: { active } }),
      active ? "Restored" : "Archived",
    );
    if (r) {
      onChanged();
      if (!active) onClose();
      else void reload();
    }
  }

  return (
    <Card className="h-fit xl:sticky xl:top-24">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-lg font-semibold">{i.title}</h2>
          <p className="text-ink-muted text-xs">
            {[CATEGORY[i.category], FREQ[i.recurrence], i.authority].filter(Boolean).join(" · ")}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="text-ink-muted hover:text-ink rounded-full p-1.5 hover:bg-black/5"
          aria-label="Close"
        >
          <X className="size-4" />
        </button>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
        <Badge tone={STATE_TONE[i.state]}>{STATE_LABEL[i.state]}</Badge>
        {i.dueDate && (
          <span>
            Due {fmtDay(i.dueDate)} <span className="text-ink-muted">({when(i)})</span>
          </span>
        )}
      </div>
      {i.ownerName && <p className="text-ink-muted mt-1 text-sm">Owner: {i.ownerName}</p>}
      {i.description && (
        <p className="text-ink-muted mt-3 text-sm whitespace-pre-line">{i.description}</p>
      )}

      {editing ? (
        <div className="mt-4">
          <ItemForm
            item={i}
            people={people}
            onClose={() => setEditing(false)}
            onSaved={() => {
              setEditing(false);
              void reload();
              onChanged();
            }}
          />
        </div>
      ) : (
        <div className="mt-3 flex flex-wrap gap-2">
          <Btn size="sm" onClick={() => setEditing(true)}>
            Edit
          </Btn>
          {i.active ? (
            <Btn
              size="sm"
              variant="ghost"
              busy={busy === "archive"}
              onClick={() => void archive(false)}
            >
              <Archive className="size-3.5" aria-hidden /> Archive
            </Btn>
          ) : (
            <Btn
              size="sm"
              variant="ghost"
              busy={busy === "archive"}
              onClick={() => void archive(true)}
            >
              <RotateCcw className="size-3.5" aria-hidden /> Restore
            </Btn>
          )}
          <Btn size="sm" variant="ghost" busy={busy === "delete"} onClick={() => void remove()}>
            <Trash2 className="size-3.5" aria-hidden /> Delete
          </Btn>
        </div>
      )}

      {i.active && i.state !== "DONE" && (
        <div className="mt-5 rounded-xl bg-black/[0.03] p-3">
          <p className="mb-2 text-sm font-medium">Mark as done</p>
          <div className="grid gap-2 sm:grid-cols-2">
            <Field label="Done on">
              <input
                className={inputCls}
                type="date"
                max={today()}
                value={on}
                onChange={(e) => setOn(e.target.value)}
              />
            </Field>
            <Field label="Certificate valid until">
              <input
                className={inputCls}
                type="date"
                value={expires}
                onChange={(e) => setExpires(e.target.value)}
                disabled={!file}
              />
            </Field>
          </div>
          <textarea
            className={`${inputCls} mt-2`}
            rows={2}
            maxLength={1000}
            placeholder="Note (optional)"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            aria-label="Completion note"
          />
          <label className="mt-2 inline-flex h-10 cursor-pointer items-center gap-1.5 rounded-full bg-white px-4 text-sm font-medium ring-1 ring-black/10">
            <Upload className="size-4" aria-hidden />{" "}
            {file ? file.name : "Attach evidence (PDF / photo)"}
            <input
              type="file"
              accept="application/pdf,image/jpeg,image/png"
              className="sr-only"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </label>
          <div className="mt-3">
            <Btn variant="primary" busy={busy === "done"} onClick={() => void complete()}>
              <Check className="size-4" aria-hidden /> Mark done
            </Btn>
          </div>
          {i.recurrence !== "ONCE" && (
            <p className="text-ink-muted mt-2 text-xs">
              The next due date moves forward automatically (
              {(FREQ[i.recurrence] ?? "").toLowerCase()}).
            </p>
          )}
        </div>
      )}

      {d.documents.length > 0 && (
        <div className="mt-5">
          <p className="mb-2 text-sm font-medium">Documents</p>
          <ul className="space-y-1.5">
            {d.documents.map((doc) => (
              <li
                key={doc.id}
                className="flex items-center gap-2 rounded-xl p-2.5 text-sm ring-1 ring-black/10"
              >
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{doc.title}</span>
                  <span className="text-ink-muted text-xs">
                    {doc.expiresOn ? `Valid until ${fmtDay(doc.expiresOn)}` : doc.filename}
                  </span>
                </span>
                <Badge tone={EXPIRY_TONE[doc.expiry]}>{EXPIRY_LABEL[doc.expiry]}</Badge>
                <Btn
                  size="sm"
                  variant="ghost"
                  onClick={() => void run("dl" + doc.id, () => downloadDoc(doc.id))}
                  aria-label={`Download ${doc.title}`}
                >
                  <Download className="size-3.5" aria-hidden />
                </Btn>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="mt-5">
        <p className="mb-2 text-sm font-medium">History</p>
        {d.completions.length === 0 ? (
          <p className="text-ink-muted text-sm">Not completed yet.</p>
        ) : (
          <ol className="space-y-3 border-l border-black/10 pl-4">
            {d.completions.map((c) => (
              <li key={c.id} className="relative text-sm">
                <span
                  className="absolute top-1.5 -left-[1.3rem] size-2 rounded-full bg-emerald-500"
                  aria-hidden
                />
                <p className="font-medium">
                  Done {fmtDay(c.completedOn)}
                  {c.dueDate ? (
                    <span className="text-ink-muted font-normal">
                      {" "}
                      · was due {fmtDay(c.dueDate)}
                    </span>
                  ) : null}
                </p>
                {c.note && <p className="text-ink-muted">{c.note}</p>}
                <p className="text-ink-muted text-xs">
                  {c.completedByName ?? ""}
                  {c.documentTitle ? ` · ${c.documentTitle}` : ""}
                </p>
              </li>
            ))}
          </ol>
        )}
      </div>
    </Card>
  );
}

function Vault({ items, onChanged }: { items: Item[]; onChanged: () => void }) {
  const [docs, setDocs] = useState<Doc[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [up, setUp] = useState(false);
  const [f, setF] = useState({
    title: "",
    category: "SAFETY",
    itemId: "",
    referenceNo: "",
    issuedOn: "",
    expiresOn: "",
  });
  const [file, setFile] = useState<File | null>(null);
  const { busy, run } = useAction();

  const load = useCallback(async () => {
    try {
      setDocs(await sb<Doc[]>("compliance/documents"));
    } catch (e) {
      setErr((e as Error).message);
    }
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  async function upload(e: React.FormEvent) {
    e.preventDefault();
    if (!file) return;
    const form = new FormData();
    form.set("file", file);
    for (const [k, v] of Object.entries(f)) if (v) form.set(k, v);
    const r = await run("up", () => sb<Doc>("compliance/documents", { form }), "Document uploaded");
    if (r) {
      setUp(false);
      setFile(null);
      setF({
        title: "",
        category: "SAFETY",
        itemId: "",
        referenceNo: "",
        issuedOn: "",
        expiresOn: "",
      });
      void load();
      onChanged();
    }
  }
  async function remove(d: Doc) {
    if (!confirm(`Delete “${d.title}”? This cannot be undone.`)) return;
    const r = await run(
      "del" + d.id,
      () => sb(`compliance/documents/${d.id}`, { method: "DELETE" }).then(() => true),
      "Deleted",
    );
    if (r) {
      void load();
      onChanged();
    }
  }

  if (err) return <Empty>{err}</Empty>;
  if (!docs) return <Loading />;
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF({ ...f, [k]: e.target.value });

  return (
    <div>
      {up ? (
        <Card className="mb-5">
          <form onSubmit={(e) => void upload(e)}>
            <h2 className="text-lg font-semibold">Upload a certificate or proof</h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <div className="sm:col-span-2">
                <Field label="Title *">
                  <input
                    className={inputCls}
                    required
                    maxLength={160}
                    value={f.title}
                    onChange={set("title")}
                    placeholder="e.g. Fire NOC 2026–27"
                  />
                </Field>
              </div>
              <Field label="Category">
                <select className={inputCls} value={f.category} onChange={set("category")}>
                  {Object.entries(CATEGORY).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="For calendar item">
                <select className={inputCls} value={f.itemId} onChange={set("itemId")}>
                  <option value="">None</option>
                  {items.map((i) => (
                    <option key={i.id} value={i.id}>
                      {i.title}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Certificate / reference no.">
                <input
                  className={inputCls}
                  maxLength={120}
                  value={f.referenceNo}
                  onChange={set("referenceNo")}
                />
              </Field>
              <Field label="Issued on">
                <input
                  className={inputCls}
                  type="date"
                  max={today()}
                  value={f.issuedOn}
                  onChange={set("issuedOn")}
                />
              </Field>
              <Field label="Valid until">
                <input
                  className={inputCls}
                  type="date"
                  value={f.expiresOn}
                  onChange={set("expiresOn")}
                />
              </Field>
              <div className="sm:col-span-2">
                <Field label="File (PDF, JPG or PNG, up to 10 MB) *">
                  <input
                    type="file"
                    required
                    accept="application/pdf,image/jpeg,image/png"
                    onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                    className="block w-full text-sm"
                  />
                </Field>
              </div>
            </div>
            <div className="mt-4 flex gap-2">
              <Btn type="submit" variant="primary" busy={busy === "up"} disabled={!file}>
                <Upload className="size-4" aria-hidden /> Upload
              </Btn>
              <Btn variant="ghost" onClick={() => setUp(false)}>
                Cancel
              </Btn>
            </div>
          </form>
        </Card>
      ) : (
        <Btn className="mb-4" onClick={() => setUp(true)}>
          <Upload className="size-4" aria-hidden /> Upload document
        </Btn>
      )}
      {docs.length === 0 ? (
        <Empty>
          No documents yet. Upload certificates (fire NOC, building safety, water test reports…)
          with their expiry dates to get reminders before they lapse.
        </Empty>
      ) : (
        <div className="overflow-x-auto rounded-2xl bg-white ring-1 ring-black/5">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-ink-muted text-left text-xs">
                <th className="px-3 py-2 font-medium">Document</th>
                <th className="px-3 py-2 font-medium">Category</th>
                <th className="px-3 py-2 font-medium">Valid until</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {docs.map((d) => (
                <tr key={d.id} className="border-t border-black/5">
                  <td className="px-3 py-2.5">
                    <span className="font-medium">{d.title}</span>
                    <span className="text-ink-muted block text-xs">
                      {[d.referenceNo, d.itemTitle, `${d.filename} · ${kb(d.sizeBytes)}`]
                        .filter(Boolean)
                        .join(" · ")}
                    </span>
                  </td>
                  <td className="px-3 py-2.5">{CATEGORY[d.category] ?? d.category}</td>
                  <td className="px-3 py-2.5 whitespace-nowrap">
                    {fmtDay(d.expiresOn)}
                    {d.daysLeft != null && d.daysLeft >= 0 && d.daysLeft <= 60 && (
                      <span className="block text-xs text-amber-700">{d.daysLeft} days left</span>
                    )}
                  </td>
                  <td className="px-3 py-2.5">
                    <Badge tone={EXPIRY_TONE[d.expiry]}>{EXPIRY_LABEL[d.expiry]}</Badge>
                  </td>
                  <td className="px-3 py-2.5 text-right whitespace-nowrap">
                    <Btn
                      size="sm"
                      variant="ghost"
                      busy={busy === "dl" + d.id}
                      onClick={() => void run("dl" + d.id, () => downloadDoc(d.id))}
                      aria-label={`Download ${d.title}`}
                    >
                      <Download className="size-3.5" aria-hidden />
                    </Btn>
                    <Btn
                      size="sm"
                      variant="ghost"
                      busy={busy === "del" + d.id}
                      onClick={() => void remove(d)}
                      aria-label={`Delete ${d.title}`}
                    >
                      <Trash2 className="size-3.5" aria-hidden />
                    </Btn>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function Suggestions({ onAdded }: { onAdded: () => void }) {
  const [list, setList] = useState<Template[] | null>(null);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const { busy, run } = useAction();
  useEffect(() => {
    sb<Template[]>("compliance/templates")
      .then((t) => {
        setList(t);
        setPicked(new Set(t.map((x) => x.key)));
      })
      .catch(() => setList([]));
  }, []);
  if (!list) return <Loading />;
  const toggle = (k: string) => {
    const n = new Set(picked);
    if (n.has(k)) n.delete(k);
    else n.add(k);
    setPicked(n);
  };
  async function add() {
    const r = await run("add", () =>
      sb<{ added: number }>("compliance/templates", { body: { keys: [...picked] } }),
    );
    if (r) onAdded();
  }
  return (
    <Card>
      <h2 className="flex items-center gap-2 text-lg font-semibold">
        <Sparkles className="text-primary size-5" aria-hidden /> Common obligations for schools in
        India
      </h2>
      <p className="text-ink-muted mt-1 text-sm">
        Pick the ones that apply to you; they’re added without dates so you can set each due date.
        Frequencies are typical, so check them against your board’s and local authority’s rules.
        Items you already have are skipped.
      </p>
      <ul className="mt-4 grid gap-2 sm:grid-cols-2">
        {list.map((t) => (
          <li key={t.key}>
            <label className="grid cursor-pointer grid-cols-[auto_1fr] items-start gap-x-3 rounded-xl p-3 ring-1 ring-black/10 hover:ring-black/20">
              <input
                type="checkbox"
                className="row-span-2 mt-0.5 size-4"
                checked={picked.has(t.key)}
                onChange={() => toggle(t.key)}
              />
              <span className="text-sm font-medium">{t.title}</span>
              <span className="text-ink-muted text-xs">
                {[CATEGORY[t.category], FREQ[t.recurrence], t.authority]
                  .filter(Boolean)
                  .join(" · ")}
              </span>
            </label>
          </li>
        ))}
      </ul>
      <Btn
        variant="primary"
        className="mt-4"
        busy={busy === "add"}
        disabled={picked.size === 0}
        onClick={() => void add()}
      >
        <Plus className="size-4" aria-hidden /> Add {picked.size} to calendar
      </Btn>
    </Card>
  );
}
