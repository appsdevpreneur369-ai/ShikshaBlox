"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ArrowDown, ArrowUp, Bell, BookOpen, Briefcase, Calendar, ExternalLink, FileCheck, HelpCircle, Images, Newspaper, Pencil, Plus, Trash2, Users } from "lucide-react";
import { sb, fmtDate } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Field, Loading, PageHeader, StatusBadge, inputCls, useAction } from "@/components/kit";
import { MediaField, MediaListField, useMedia } from "@/components/media-picker";
import { RichText } from "@/components/rich-text";
import { thumb } from "@/lib/studio";

type FieldSpec = { name: string; kind: "TEXT" | "HTML" | "DATE" | "BOOL" | "MEDIA" | "MEDIA_LIST"; required: boolean; max: number; label: string };
type TypeSpec = { type: string; label: string; fields: FieldSpec[]; slugged: boolean };
type Item = { id: string; type: string; slug?: string; status: string; data: Record<string, unknown>; sortOrder: number; itemDate?: string; updatedAt: string };

const ICONS: Record<string, typeof Bell> = {
  notices: Bell,
  news: Newspaper,
  events: Calendar,
  albums: Images,
  staff: Users,
  faqs: HelpCircle,
  careers: Briefcase,
  disclosures: FileCheck,
};
const PUBLIC_PATH: Record<string, string> = { notices: "/news", news: "/news", events: "/events", albums: "/gallery", staff: "/faculty", faqs: "/faqs", careers: "/careers" };
const LONG_TEXT = new Set(["summary"]);
const ORDERED = new Set(["staff", "faqs", "disclosures"]);

export function ContentManager({
  type,
  site,
  canEdit,
  canPublish,
  isAdmin,
}: {
  type: string;
  site: string;
  canEdit: boolean;
  canPublish: boolean;
  isAdmin: boolean;
}) {
  const [types, setTypes] = useState<TypeSpec[] | null>(null);
  const [loaded, setLoaded] = useState<{ type: string; items: Item[] } | null>(null);
  const items = loaded?.type === type ? loaded.items : null;
  const setItems = (next: Item[]) => setLoaded({ type, items: next });
  const [editing, setEditing] = useState<Item | "new" | null>(null);
  const { busy, run } = useAction();

  useEffect(() => {
    void sb<TypeSpec[]>("content/types").then(setTypes);
  }, []);
  const load = useCallback(() => sb<Item[]>(`content/${type}`).then((list) => setLoaded({ type, items: list })), [type]);
  useEffect(() => {
    void load();
  }, [load]);

  const spec = types?.find((t) => t.type === type);

  async function togglePublish(it: Item) {
    const pub = it.status !== "PUBLISHED";
    await run(it.id, () => sb(`content/${type}/${it.id}/${pub ? "publish" : "unpublish"}`, { method: "POST" }), pub ? "Published" : "Unpublished");
    void load();
  }
  async function remove(it: Item) {
    if (!window.confirm(`Delete “${String(it.data.title)}”? This cannot be undone.`)) return;
    await run(it.id, () => sb(`content/${type}/${it.id}`, { method: "DELETE" }), "Deleted");
    void load();
  }
  async function move(i: number, d: number) {
    if (!items) return;
    const next = [...items];
    const [x] = next.splice(i, 1);
    next.splice(i + d, 0, x!);
    setItems(next);
    await run("order", () => sb(`content/${type}/order`, { method: "PUT", body: { ids: next.map((n) => n.id) } }));
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[220px_1fr]">
      <nav aria-label="Content types">
        <ul className="space-y-1">
          {(types ?? []).map((t) => {
            const Icon = ICONS[t.type] ?? BookOpen;
            const active = t.type === type;
            return (
              <li key={t.type}>
                <Link
                  href={`/content/${t.type}`}
                  aria-current={active ? "page" : undefined}
                  className={`flex items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-medium transition ${active ? "bg-primary text-on-primary shadow-sm" : "text-ink-muted hover:text-ink hover:bg-white"}`}
                >
                  <Icon className="size-4" aria-hidden /> {t.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      <div>
        {!spec ? (
          <Loading />
        ) : editing ? (
          <ItemForm
            spec={spec}
            item={editing === "new" ? null : editing}
            canPublish={canPublish}
            onDone={() => {
              setEditing(null);
              void load();
            }}
          />
        ) : (
          <>
            <PageHeader
              title={spec.label}
              subtitle={type === "disclosures" ? <DisclosureReview isAdmin={isAdmin} /> : "Changes appear on your website as soon as you publish."}
              actions={
                <>
                  {PUBLIC_PATH[type] && (
                    <a href={`${site}${PUBLIC_PATH[type]}`} target="_blank" rel="noreferrer">
                      <Btn>
                        <ExternalLink className="size-4" aria-hidden /> View on site
                      </Btn>
                    </a>
                  )}
                  {canEdit && (
                    <Btn variant="primary" onClick={() => setEditing("new")}>
                      <Plus className="size-4" aria-hidden /> Add
                    </Btn>
                  )}
                </>
              }
            />
            {!items ? (
              <Loading />
            ) : items.length === 0 ? (
              <Empty>Nothing here yet. Click “Add” to create the first one.</Empty>
            ) : (
              <Card className="overflow-hidden p-0">
                <ul className="divide-y divide-black/5">
                  {items.map((it, i) => (
                    <ItemRow
                      key={it.id}
                      it={it}
                      busy={busy === it.id}
                      canEdit={canEdit}
                      canPublish={canPublish}
                      ordered={ORDERED.has(type)}
                      first={i === 0}
                      last={i === items.length - 1}
                      onMove={(d) => void move(i, d)}
                      onEdit={() => setEditing(it)}
                      onToggle={() => void togglePublish(it)}
                      onDelete={() => void remove(it)}
                    />
                  ))}
                </ul>
              </Card>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function ItemRow({
  it,
  busy,
  canEdit,
  canPublish,
  ordered,
  first,
  last,
  onMove,
  onEdit,
  onToggle,
  onDelete,
}: {
  it: Item;
  busy: boolean;
  canEdit: boolean;
  canPublish: boolean;
  ordered: boolean;
  first: boolean;
  last: boolean;
  onMove: (d: number) => void;
  onEdit: () => void;
  onToggle: () => void;
  onDelete: () => void;
}) {
  const img = (it.data.imageId as string) ?? (it.data.imageIds as string[] | undefined)?.[0];
  const get = useMedia(img ? [img] : []);
  return (
    <li className="flex flex-wrap items-center gap-4 px-5 py-3.5">
      {img ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={thumb(get(img))} alt="" className="bg-surface-muted size-12 shrink-0 rounded-lg object-cover" />
      ) : null}
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" onClick={onEdit} className="text-left font-semibold hover:underline">
            {String(it.data.title ?? "")}
          </button>
          <StatusBadge status={it.status} />
          {Boolean(it.data.pinned) && <Badge tone="purple">Pinned</Badge>}
          {it.data.category ? <Badge tone="blue">{String(it.data.category)}</Badge> : null}
        </div>
        <p className="text-ink-muted mt-0.5 truncate text-xs">
          {[it.data.date ? String(it.data.date).replace("T", " ") : null, it.data.role, it.data.location, Array.isArray(it.data.imageIds) ? `${it.data.imageIds.length} photos` : null]
            .filter(Boolean)
            .join(" · ") || `Updated ${fmtDate(it.updatedAt)}`}
        </p>
      </div>
      <div className="flex items-center gap-1">
        {ordered && canEdit && (
          <>
            <Btn size="sm" variant="ghost" disabled={first} onClick={() => onMove(-1)} aria-label="Move up">
              <ArrowUp className="size-3.5" aria-hidden />
            </Btn>
            <Btn size="sm" variant="ghost" disabled={last} onClick={() => onMove(1)} aria-label="Move down">
              <ArrowDown className="size-3.5" aria-hidden />
            </Btn>
          </>
        )}
        {canPublish && (
          <Btn size="sm" busy={busy} variant={it.status === "PUBLISHED" ? "secondary" : "success"} onClick={onToggle}>
            {it.status === "PUBLISHED" ? "Unpublish" : "Publish"}
          </Btn>
        )}
        {canEdit && (
          <Btn size="sm" variant="ghost" onClick={onEdit} aria-label="Edit">
            <Pencil className="size-3.5" aria-hidden />
          </Btn>
        )}
        {canPublish && (
          <Btn size="sm" variant="ghost" onClick={onDelete} aria-label="Delete">
            <Trash2 className="size-3.5 text-red-600" aria-hidden />
          </Btn>
        )}
      </div>
    </li>
  );
}

function ItemForm({ spec, item, canPublish, onDone }: { spec: TypeSpec; item: Item | null; canPublish: boolean; onDone: () => void }) {
  const [data, setData] = useState<Record<string, unknown>>(
    item?.data ?? (spec.fields.some((f) => f.name === "date" && f.required) ? { date: new Date().toISOString().slice(0, 10) } : {}),
  );
  const { busy, run } = useAction();
  const set = (k: string, v: unknown) => setData((d) => ({ ...d, [k]: v }));

  async function save(publish: boolean) {
    const ok = await run(
      publish ? "pub" : "save",
      async () => {
        if (item) {
          await sb(`content/${spec.type}/${item.id}`, { method: "PUT", body: { data } });
          if (publish && item.status !== "PUBLISHED") await sb(`content/${spec.type}/${item.id}/publish`, { method: "POST" });
        } else {
          await sb(`content/${spec.type}`, { body: { data, publish } });
        }
        return true;
      },
      publish ? "Published" : "Saved",
    );
    if (ok) onDone();
  }

  return (
    <div>
      <PageHeader title={item ? `Edit ${spec.label.toLowerCase().replace(/s$/, "")}` : `New ${spec.label.toLowerCase().replace(/s$/, "")}`} />
      <Card>
        <div className="grid gap-5 md:grid-cols-2">
          {spec.fields.map((f) => {
            const lbl = `${f.label}${f.required ? " *" : ""}`;
            const v = data[f.name];
            const wide = f.kind === "HTML" || f.kind === "MEDIA_LIST" || LONG_TEXT.has(f.name) || f.name === "title";
            return (
              <div key={f.name} className={wide ? "md:col-span-2" : ""}>
                {f.kind === "TEXT" && (
                  <Field label={lbl}>
                    {LONG_TEXT.has(f.name) ? (
                      <textarea className={inputCls} rows={4} maxLength={f.max} value={(v as string) ?? ""} onChange={(e) => set(f.name, e.target.value)} />
                    ) : (
                      <input className={inputCls} maxLength={f.max} value={(v as string) ?? ""} onChange={(e) => set(f.name, e.target.value)} />
                    )}
                  </Field>
                )}
                {f.kind === "DATE" && (
                  <Field label={lbl}>
                    <input type="date" className={inputCls} value={((v as string) ?? "").slice(0, 10)} onChange={(e) => set(f.name, e.target.value)} />
                  </Field>
                )}
                {f.kind === "BOOL" && (
                  <label className="mt-6 flex items-center gap-2 text-sm font-medium">
                    <input type="checkbox" className="accent-primary size-4" checked={Boolean(v)} onChange={(e) => set(f.name, e.target.checked)} />
                    {f.label}
                  </label>
                )}
                {f.kind === "HTML" && (
                  <div className="space-y-1.5">
                    <span className="text-sm font-medium">{lbl}</span>
                    <RichText value={(v as string) ?? ""} onChange={(h) => set(f.name, h)} />
                  </div>
                )}
                {f.kind === "MEDIA" && (
                  <Field label={lbl}>
                    <MediaField value={v as string} onChange={(id) => set(f.name, id)} kind={f.name === "attachmentId" ? "DOCUMENT" : "IMAGE"} />
                  </Field>
                )}
                {f.kind === "MEDIA_LIST" && (
                  <div className="space-y-1.5">
                    <span className="text-sm font-medium">{lbl}</span>
                    <MediaListField value={(v as string[]) ?? []} onChange={(ids) => set(f.name, ids)} />
                  </div>
                )}
              </div>
            );
          })}
        </div>
        <div className="mt-6 flex flex-wrap justify-end gap-2 border-t border-black/5 pt-5">
          <Btn onClick={onDone}>Cancel</Btn>
          <Btn busy={busy === "save"} onClick={() => void save(false)}>
            {item ? "Save" : "Save as draft"}
          </Btn>
          {canPublish && (
            <Btn variant="success" busy={busy === "pub"} onClick={() => void save(true)}>
              {item?.status === "PUBLISHED" ? "Save & update site" : "Publish"}
            </Btn>
          )}
        </div>
      </Card>
    </div>
  );
}

function DisclosureReview({ isAdmin }: { isAdmin: boolean }) {
  const [state, setState] = useState<{ reviewedAt?: string; due: boolean } | null>(null);
  const { busy, run } = useAction();
  useEffect(() => {
    void sb<{ reviewedAt?: string; due: boolean }>("disclosures/review").then(setState);
  }, []);
  if (!state) return null;
  return (
    <span className="flex flex-wrap items-center gap-2">
      <span>Last reviewed: {state.reviewedAt ? fmtDate(state.reviewedAt) : "never"}</span>
      {state.due && <Badge tone="amber">Review due</Badge>}
      {isAdmin && (
        <Btn size="sm" busy={busy === "rev"} onClick={async () => setState((await run("rev", () => sb<{ reviewedAt?: string; due: boolean }>("disclosures/review", { method: "POST" }), "Marked as reviewed")) ?? state)}>
          Mark reviewed today
        </Btn>
      )}
    </span>
  );
}
