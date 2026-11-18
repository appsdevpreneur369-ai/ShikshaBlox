"use client";

import { useCallback, useEffect, useState } from "react";
import { Mail, Phone, Search } from "lucide-react";
import { sb, fmtDate } from "@/lib/studio";
import { Btn, Card, Empty, Loading, PageHeader, STATUS_LABEL, StatusBadge, inputCls, useAction } from "@/components/kit";

type Enquiry = {
  id: string;
  name: string;
  phone: string;
  email?: string;
  gradeSought?: string;
  message: string;
  sourcePage?: string;
  status: string;
  notes?: string;
  createdAt: string;
};
const STATUSES = ["NEW", "CONTACTED", "ENROLLED", "CLOSED", "SPAM"];

export default function EnquiriesPage() {
  const [status, setStatus] = useState("");
  const [q, setQ] = useState("");
  const [items, setItems] = useState<Enquiry[] | null>(null);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [sel, setSel] = useState<Enquiry | null>(null);
  const [notes, setNotes] = useState("");
  const { busy, run } = useAction();

  const load = useCallback(async () => {
    const p = new URLSearchParams();
    if (status) p.set("status", status);
    if (q) p.set("q", q);
    const [list, c] = await Promise.all([sb<Enquiry[]>(`enquiries?${p}`), sb<Record<string, number>>("enquiries/counts")]);
    setItems(list);
    setCounts(c);
  }, [status, q]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 250);
    return () => clearTimeout(t);
  }, [load]);

  async function update(e: Enquiry, patch: { status?: string; notes?: string }) {
    const r = await run("upd", () => sb<Enquiry>(`enquiries/${e.id}`, { method: "PUT", body: patch }), "Saved");
    if (r) {
      setSel(r);
      void load();
    }
  }

  const total = Object.values(counts).reduce((a, b) => a + b, 0);

  return (
    <div>
      <PageHeader title="Enquiries" subtitle="Admissions enquiries from your website. School administrators are emailed when a new one arrives." />
      <div className="mb-4 flex flex-wrap items-center gap-2">
        {[["", `All (${total})`], ...STATUSES.map((s) => [s, `${STATUS_LABEL[s]} (${counts[s] ?? 0})`])].map(([v, l]) => (
          <button
            key={v}
            type="button"
            onClick={() => setStatus(v!)}
            className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition ${status === v ? "bg-primary text-on-primary" : "text-ink-muted bg-white ring-1 ring-black/10 hover:text-ink"}`}
          >
            {l}
          </button>
        ))}
        <div className="relative ml-auto w-full sm:w-64">
          <Search className="text-ink-muted absolute top-2.5 left-3 size-4" aria-hidden />
          <input className={`${inputCls} pl-9`} placeholder="Search name or phone" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search enquiries" />
        </div>
      </div>

      <div className="grid gap-5 lg:grid-cols-[1fr_380px]">
        <div>
          {!items ? (
            <Loading />
          ) : items.length === 0 ? (
            <Empty>No enquiries yet. They will appear here when parents use the form on your website.</Empty>
          ) : (
            <Card className="overflow-hidden p-0">
              <ul className="divide-y divide-black/5">
                {items.map((e) => (
                  <li key={e.id}>
                    <button
                      type="button"
                      onClick={() => {
                        setSel(e);
                        setNotes(e.notes ?? "");
                      }}
                      className={`flex w-full items-start gap-4 px-5 py-4 text-left transition hover:bg-black/[0.02] ${sel?.id === e.id ? "bg-primary/5" : ""}`}
                    >
                      <span className="bg-primary/10 text-primary grid size-10 shrink-0 place-items-center rounded-full text-sm font-bold">
                        {e.name.slice(0, 1).toUpperCase()}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap items-center gap-2">
                          <span className="font-semibold">{e.name}</span>
                          <StatusBadge status={e.status} />
                          {e.gradeSought && <span className="text-ink-muted text-xs">· {e.gradeSought}</span>}
                        </span>
                        <span className="text-ink-muted mt-0.5 block truncate text-sm">{e.message}</span>
                      </span>
                      <span className="text-ink-muted shrink-0 text-xs">{fmtDate(e.createdAt)}</span>
                    </button>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>

        {sel && (
          <Card className="h-fit lg:sticky lg:top-24">
            <h2 className="text-lg font-semibold">{sel.name}</h2>
            <p className="text-ink-muted text-xs">Received {fmtDate(sel.createdAt)}{sel.sourcePage ? ` from ${sel.sourcePage}` : ""}</p>
            <div className="mt-4 space-y-2 text-sm">
              <a href={`tel:${sel.phone.replace(/\s/g, "")}`} className="text-primary flex items-center gap-2 font-medium hover:underline">
                <Phone className="size-4" aria-hidden /> {sel.phone}
              </a>
              {sel.email && (
                <a href={`mailto:${sel.email}`} className="text-primary flex items-center gap-2 font-medium hover:underline">
                  <Mail className="size-4" aria-hidden /> {sel.email}
                </a>
              )}
              {sel.gradeSought && (
                <p>
                  <span className="text-ink-muted">Class sought:</span> {sel.gradeSought}
                </p>
              )}
            </div>
            <p className="bg-surface-muted mt-4 rounded-xl p-3 text-sm whitespace-pre-line">{sel.message}</p>
            <div className="mt-4">
              <p className="mb-2 text-sm font-medium">Status</p>
              <div className="flex flex-wrap gap-1.5">
                {STATUSES.map((s) => (
                  <Btn key={s} size="sm" variant={sel.status === s ? "primary" : "secondary"} onClick={() => void update(sel, { status: s })}>
                    {STATUS_LABEL[s]}
                  </Btn>
                ))}
              </div>
            </div>
            <div className="mt-4 space-y-2">
              <label htmlFor="enq-notes" className="text-sm font-medium">
                Notes (only visible in Studio)
              </label>
              <textarea id="enq-notes" className={inputCls} rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
              <Btn size="sm" busy={busy === "upd"} onClick={() => void update(sel, { notes })}>
                Save notes
              </Btn>
            </div>
          </Card>
        )}
      </div>
    </div>
  );
}
