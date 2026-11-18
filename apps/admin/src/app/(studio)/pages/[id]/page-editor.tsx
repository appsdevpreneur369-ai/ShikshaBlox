"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  CalendarClock,
  ChevronDown,
  ChevronRight,
  Copy,
  Eye,
  History,
  Plus,
  Rocket,
  Save,
  Send,
  Trash2,
  Undo2,
  X,
} from "lucide-react";
import { BLOCK_TYPES, type Block, type BlockType } from "@shikshablox/blocks";
import { sb, fmtDate } from "@/lib/studio";
import { Badge, Btn, Card, Field, Loading, StatusBadge, inputCls, useAction } from "@/components/kit";
import { BLOCK_INFO, BLOCK_PROPS, SchemaFields, defaultFor } from "@/components/schema-form";

type PageDetail = {
  id: string;
  slug: string;
  kind: string;
  templateKey?: string;
  title: string;
  seo: Record<string, unknown>;
  draftDocument: { schemaVersion: 1; blocks: Block[] };
  publishedDocument?: unknown;
  status: string;
  publishedAt?: string;
  updatedAt: string;
};
type Revision = { id: string; title: string; action: string; note?: string; createdAt: string };

const newId = () => Math.random().toString(36).slice(2, 10);

function summary(b: Block): string {
  const p = b.props as Record<string, unknown>;
  const text = (p.heading ?? p.title ?? (typeof p.html === "string" ? p.html.replace(/<[^>]+>/g, " ") : "")) as string;
  return (text || "").trim().slice(0, 90);
}

export function PageEditor({
  id,
  site,
  canEdit,
  canPublish,
  isAdmin,
}: {
  id: string;
  site: string;
  canEdit: boolean;
  canPublish: boolean;
  isAdmin: boolean;
}) {
  const [page, setPage] = useState<PageDetail | null>(null);
  const [title, setTitle] = useState("");
  const [blocks, setBlocks] = useState<Block[]>([]);
  const [seo, setSeo] = useState<Record<string, unknown>>({});
  const [open, setOpen] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);
  const [adding, setAdding] = useState<number | null>(null);
  const [panel, setPanel] = useState<"none" | "history" | "schedule" | "seo">("none");
  const [revisions, setRevisions] = useState<Revision[] | null>(null);
  const [when, setWhen] = useState("");
  const { busy, run } = useAction();

  const apply = useCallback((p: PageDetail) => {
    setPage(p);
    setTitle(p.title);
    setBlocks(p.draftDocument.blocks);
    setSeo(p.seo ?? {});
    setDirty(false);
  }, []);

  useEffect(() => {
    void sb<PageDetail>(`pages/${id}?lang=en`).then(apply);
  }, [id, apply]);

  useEffect(() => {
    const h = (e: BeforeUnloadEvent) => {
      if (dirty) e.preventDefault();
    };
    window.addEventListener("beforeunload", h);
    return () => window.removeEventListener("beforeunload", h);
  }, [dirty]);

  const allowed = useMemo(
    () => BLOCK_TYPES.filter((t) => t !== "reusable" && (t !== "customHtml" || isAdmin)),
    [isAdmin],
  );

  function update(next: Block[]) {
    setBlocks(next);
    setDirty(true);
  }

  function addBlock(type: BlockType, at: number) {
    const b = { id: newId(), type, props: defaultFor(BLOCK_PROPS[type]!) } as Block;
    const next = [...blocks];
    next.splice(at, 0, b);
    update(next);
    setOpen(b.id);
    setAdding(null);
  }

  function move(i: number, d: number) {
    const next = [...blocks];
    const [x] = next.splice(i, 1);
    next.splice(i + d, 0, x!);
    update(next);
  }

  async function save(quiet = false) {
    const p = await run(
      "save",
      () =>
        sb<PageDetail>(`pages/${id}?lang=en`, {
          method: "PUT",
          body: { title, seo, document: { schemaVersion: 1, blocks } },
        }),
      quiet ? undefined : "Draft saved",
    );
    if (p) apply(p);
    return p;
  }

  async function ensureSaved() {
    if (!dirty) return page;
    return save(true);
  }

  async function preview() {
    const win = window.open("about:blank", "_blank");
    const saved = await ensureSaved();
    if (!saved) {
      win?.close();
      return;
    }
    const t = await run("preview", () => sb<{ token: string }>(`pages/${id}/preview-token?lang=en`, { method: "POST" }));
    if (t && win) win.location.href = `${site}/preview/${t.token}`;
    else win?.close();
  }

  async function act(key: string, path: string, body: unknown, ok: string) {
    if (!(await ensureSaved())) return;
    const p = await run(key, () => sb<PageDetail>(`pages/${id}/${path}?lang=en`, { method: "POST", body }), ok);
    if (p) apply(p);
    if (panel === "history") void loadHistory();
  }

  async function loadHistory() {
    setRevisions(await sb<Revision[]>(`pages/${id}/revisions?lang=en`));
  }

  if (!page) return <Loading />;
  const readOnly = !canEdit;

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <Link href="/pages" aria-label="Back to pages">
            <Btn variant="ghost" size="sm">
              <ArrowLeft className="size-4" aria-hidden />
            </Btn>
          </Link>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="truncate text-xl font-bold">{title || "Untitled"}</h1>
              <StatusBadge status={page.status} />
              {dirty && <Badge tone="amber">Unsaved changes</Badge>}
            </div>
            <a href={`${site}/${page.slug}`} target="_blank" rel="noreferrer" className="text-ink-muted text-xs hover:underline">
              {site.replace(/^https?:\/\//, "")}/{page.slug}
            </a>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Btn
            variant="ghost"
            onClick={() => {
              setPanel(panel === "history" ? "none" : "history");
              void loadHistory();
            }}
          >
            <History className="size-4" aria-hidden /> History
          </Btn>
          <Btn onClick={preview} busy={busy === "preview"}>
            <Eye className="size-4" aria-hidden /> Preview
          </Btn>
          {canEdit && (
            <Btn onClick={() => save()} busy={busy === "save"} disabled={!dirty}>
              <Save className="size-4" aria-hidden /> Save draft
            </Btn>
          )}
          {canPublish ? (
            <>
              <Btn variant="ghost" onClick={() => setPanel(panel === "schedule" ? "none" : "schedule")} aria-label="Schedule">
                <CalendarClock className="size-4" aria-hidden />
              </Btn>
              <Btn variant="success" busy={busy === "publish"} onClick={() => act("publish", "publish", { note: "Published from Studio" }, "Published — your website is updated")}>
                <Rocket className="size-4" aria-hidden /> Publish
              </Btn>
            </>
          ) : (
            canEdit && (
              <Btn variant="primary" busy={busy === "submit"} disabled={page.status === "IN_REVIEW" && !dirty} onClick={() => act("submit", "submit", { note: null }, "Sent for approval")}>
                <Send className="size-4" aria-hidden /> Submit for approval
              </Btn>
            )
          )}
        </div>
      </div>

      {page.status === "IN_REVIEW" && (
        <Card className="mb-4 flex flex-wrap items-center justify-between gap-3 bg-amber-50 ring-amber-200">
          <p className="text-sm text-amber-900">This draft is waiting for approval.</p>
          {canPublish && (
            <div className="flex gap-2">
              <Btn size="sm" variant="danger" busy={busy === "reject"} onClick={() => act("reject", "reject", { note: window.prompt("Note for the editor (optional)") }, "Sent back to the editor")}>
                Request changes
              </Btn>
              <Btn size="sm" variant="success" busy={busy === "publish"} onClick={() => act("publish", "publish", { note: "Approved" }, "Approved and published")}>
                Approve & publish
              </Btn>
            </div>
          )}
        </Card>
      )}
      {page.status === "SCHEDULED" && (
        <Card className="mb-4 flex flex-wrap items-center justify-between gap-3 bg-purple-50 ring-purple-200">
          <p className="text-sm text-purple-900">This draft is scheduled to publish automatically.</p>
          {canPublish && (
            <Btn size="sm" onClick={() => act("cancel", "schedule/cancel", undefined, "Schedule cancelled")}>
              Cancel schedule
            </Btn>
          )}
        </Card>
      )}

      {panel === "schedule" && (
        <Card className="mb-4">
          <div className="flex flex-wrap items-end gap-3">
            <Field label="Publish on">
              <input type="datetime-local" className={inputCls} value={when} onChange={(e) => setWhen(e.target.value)} />
            </Field>
            <Btn variant="primary" disabled={!when} busy={busy === "schedule"} onClick={() => act("schedule", "schedule", { at: new Date(when).toISOString() }, "Scheduled")}>
              Schedule publish
            </Btn>
            <Btn variant="ghost" onClick={() => setPanel("none")}>
              <X className="size-4" aria-hidden />
            </Btn>
          </div>
        </Card>
      )}

      {panel === "history" && (
        <Card className="mb-4">
          <h2 className="mb-3 font-semibold">Published versions</h2>
          {!revisions ? (
            <Loading />
          ) : revisions.length === 0 ? (
            <p className="text-ink-muted text-sm">Not published yet.</p>
          ) : (
            <ul className="divide-y divide-black/5">
              {revisions.map((r, i) => (
                <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5 text-sm">
                  <span>
                    <span className="font-medium">{fmtDate(r.createdAt)}</span>
                    <span className="text-ink-muted"> · {r.note ?? r.action.toLowerCase()}</span>
                    {i === 0 && <Badge tone="green">Live</Badge>}
                  </span>
                  {i > 0 && canPublish && (
                    <Btn size="sm" busy={busy === `rb-${r.id}`} onClick={() => act(`rb-${r.id}`, "rollback", { revisionId: r.id }, "Restored this version")}>
                      <Undo2 className="size-3.5" aria-hidden /> Restore
                    </Btn>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
        <div className="space-y-2">
          <InsertBar visible={!readOnly} onClick={() => setAdding(0)} active={adding === 0} />
          {adding === 0 && <BlockChooser types={allowed} onPick={(t) => addBlock(t, 0)} onClose={() => setAdding(null)} />}
          {blocks.map((b, i) => {
            const info = BLOCK_INFO[b.type];
            const isOpen = open === b.id;
            return (
              <div key={b.id}>
                <Card className={`p-0 transition ${isOpen ? "ring-primary/40 ring-2" : ""}`}>
                  <div className="flex items-center gap-2 px-4 py-3">
                    <button type="button" className="flex min-w-0 flex-1 items-center gap-3 text-left" onClick={() => setOpen(isOpen ? null : b.id)} aria-expanded={isOpen}>
                      {isOpen ? <ChevronDown className="size-4 shrink-0" aria-hidden /> : <ChevronRight className="size-4 shrink-0" aria-hidden />}
                      <span className="bg-primary/10 text-primary shrink-0 rounded-lg px-2 py-1 text-xs font-semibold">{info?.label ?? b.type}</span>
                      <span className="text-ink-muted truncate text-sm">{summary(b)}</span>
                    </button>
                    {!readOnly && (
                      <div className="flex shrink-0 gap-0.5">
                        <Btn size="sm" variant="ghost" disabled={i === 0} onClick={() => move(i, -1)} aria-label="Move up">
                          <ArrowUp className="size-3.5" aria-hidden />
                        </Btn>
                        <Btn size="sm" variant="ghost" disabled={i === blocks.length - 1} onClick={() => move(i, 1)} aria-label="Move down">
                          <ArrowDown className="size-3.5" aria-hidden />
                        </Btn>
                        <Btn
                          size="sm"
                          variant="ghost"
                          aria-label="Duplicate"
                          onClick={() => {
                            const next = [...blocks];
                            next.splice(i + 1, 0, { ...structuredClone(b), id: newId() });
                            update(next);
                          }}
                        >
                          <Copy className="size-3.5" aria-hidden />
                        </Btn>
                        <Btn size="sm" variant="ghost" aria-label="Delete" onClick={() => window.confirm("Remove this section?") && update(blocks.filter((x) => x.id !== b.id))}>
                          <Trash2 className="size-3.5 text-red-600" aria-hidden />
                        </Btn>
                      </div>
                    )}
                  </div>
                  {isOpen && (
                    <div className="border-t border-black/5 px-5 py-5">
                      <fieldset disabled={readOnly}>
                        <SchemaFields
                          schema={BLOCK_PROPS[b.type]!}
                          value={b.props as Record<string, unknown>}
                          onChange={(props) => update(blocks.map((x) => (x.id === b.id ? ({ ...x, props } as Block) : x)))}
                        />
                        <details className="mt-5">
                          <summary className="text-ink-muted cursor-pointer text-xs font-medium">Section style</summary>
                          <div className="mt-3 grid gap-3 sm:grid-cols-3">
                            <Field label="Background">
                              <select
                                className={inputCls}
                                value={b.settings?.background ?? "default"}
                                onChange={(e) => update(blocks.map((x) => (x.id === b.id ? ({ ...x, settings: { ...x.settings, background: e.target.value as "default" } } as Block) : x)))}
                              >
                                <option value="default">White</option>
                                <option value="muted">Light grey</option>
                                <option value="primary">School colour</option>
                                <option value="accent">Accent tint</option>
                              </select>
                            </Field>
                            <Field label="Spacing">
                              <select
                                className={inputCls}
                                value={b.settings?.spacing ?? "normal"}
                                onChange={(e) => update(blocks.map((x) => (x.id === b.id ? ({ ...x, settings: { ...x.settings, spacing: e.target.value as "normal" } } as Block) : x)))}
                              >
                                <option value="compact">Compact</option>
                                <option value="normal">Normal</option>
                                <option value="spacious">Spacious</option>
                              </select>
                            </Field>
                            <Field label="Anchor (for #links)">
                              <input
                                className={inputCls}
                                value={b.settings?.anchor ?? ""}
                                onChange={(e) => {
                                  const v = e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, "");
                                  update(blocks.map((x) => (x.id === b.id ? ({ ...x, settings: { ...x.settings, anchor: v || undefined } } as Block) : x)));
                                }}
                              />
                            </Field>
                          </div>
                        </details>
                      </fieldset>
                    </div>
                  )}
                </Card>
                <InsertBar visible={!readOnly} onClick={() => setAdding(i + 1)} active={adding === i + 1} />
                {adding === i + 1 && <BlockChooser types={allowed} onPick={(t) => addBlock(t, i + 1)} onClose={() => setAdding(null)} />}
              </div>
            );
          })}
        </div>

        <aside className="space-y-4">
          <Card>
            <h2 className="mb-3 text-sm font-semibold">Page</h2>
            <div className="space-y-3">
              <Field label="Title">
                <input className={inputCls} value={title} disabled={readOnly} onChange={(e) => (setTitle(e.target.value), setDirty(true))} />
              </Field>
              <p className="text-ink-muted text-xs">
                Last published: {fmtDate(page.publishedAt)}
                <br />
                Last saved: {fmtDate(page.updatedAt)}
              </p>
            </div>
          </Card>
          <Card>
            <h2 className="mb-3 text-sm font-semibold">Search engines</h2>
            <div className="space-y-3">
              <Field label="Search title" hint="Up to 70 characters">
                <input className={inputCls} maxLength={70} disabled={readOnly} value={(seo.metaTitle as string) ?? ""} onChange={(e) => (setSeo({ ...seo, metaTitle: e.target.value }), setDirty(true))} />
              </Field>
              <Field label="Description" hint="Up to 200 characters">
                <textarea className={inputCls} rows={3} maxLength={200} disabled={readOnly} value={(seo.metaDescription as string) ?? ""} onChange={(e) => (setSeo({ ...seo, metaDescription: e.target.value }), setDirty(true))} />
              </Field>
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}

function InsertBar({ visible, onClick, active }: { visible: boolean; onClick: () => void; active: boolean }) {
  if (!visible) return <div className="h-2" />;
  return (
    <div className="group flex h-7 items-center justify-center">
      <button
        type="button"
        onClick={onClick}
        className={`text-primary inline-flex items-center gap-1 rounded-full bg-white px-3 py-1 text-xs font-medium shadow-sm ring-1 ring-black/10 transition ${active ? "opacity-100" : "opacity-0 group-hover:opacity-100 focus-visible:opacity-100"}`}
      >
        <Plus className="size-3.5" aria-hidden /> Add section
      </button>
    </div>
  );
}

function BlockChooser({ types, onPick, onClose }: { types: BlockType[]; onPick: (t: BlockType) => void; onClose: () => void }) {
  return (
    <Card className="mb-2">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold">Add a section</h3>
        <Btn size="sm" variant="ghost" onClick={onClose} aria-label="Close">
          <X className="size-4" aria-hidden />
        </Btn>
      </div>
      <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {types.map((t) => (
          <li key={t}>
            <button type="button" onClick={() => onPick(t)} className="hover:ring-primary/40 hover:bg-primary/5 w-full rounded-xl p-3 text-left ring-1 ring-black/10 transition">
              <span className="block text-sm font-semibold">{BLOCK_INFO[t]?.label ?? t}</span>
              <span className="text-ink-muted block text-xs">{BLOCK_INFO[t]?.hint}</span>
            </button>
          </li>
        ))}
      </ul>
    </Card>
  );
}
