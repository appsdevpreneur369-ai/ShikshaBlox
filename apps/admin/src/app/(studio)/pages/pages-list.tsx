"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ExternalLink, FileText, Plus, Pencil } from "lucide-react";
import { sb, fmtDate } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Field, Loading, PageHeader, StatusBadge, inputCls, useAction } from "@/components/kit";

type PageSummary = {
  id: string;
  slug: string;
  kind: "STANDARD" | "CUSTOM";
  templateKey?: string;
  title: string;
  status: string;
  hasUnpublishedChanges: boolean;
  publishedAt?: string;
  updatedAt: string;
};

export function PagesList({ site, canEdit }: { site: string; canEdit: boolean; canPublish: boolean }) {
  const [pages, setPages] = useState<PageSummary[] | null>(null);
  const [creating, setCreating] = useState(false);
  const [title, setTitle] = useState("");
  const [slug, setSlug] = useState("");
  const { busy, run } = useAction();

  const load = useCallback(() => sb<PageSummary[]>("pages?lang=en").then(setPages), []);
  useEffect(() => {
    void load();
  }, [load]);

  async function create() {
    const p = await run(
      "create",
      () => sb<{ id: string }>("pages?lang=en", { body: { title, slug } }),
      "Page created",
    );
    if (p) window.location.href = `/pages/${p.id}`;
  }

  return (
    <div>
      <PageHeader
        title="Pages"
        subtitle="Every page on your website. Standard pages are included in every plan."
        actions={
          <>
            <a href={site} target="_blank" rel="noreferrer">
              <Btn>
                <ExternalLink className="size-4" aria-hidden /> View website
              </Btn>
            </a>
            {canEdit && (
              <Btn variant="primary" onClick={() => setCreating((v) => !v)}>
                <Plus className="size-4" aria-hidden /> New page
              </Btn>
            )}
          </>
        }
      />

      {creating && (
        <Card className="mb-6">
          <div className="grid gap-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
            <Field label="Page title">
              <input
                className={inputCls}
                value={title}
                onChange={(e) => {
                  setTitle(e.target.value);
                  setSlug(e.target.value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, ""));
                }}
                placeholder="Sports Day 2026"
              />
            </Field>
            <Field label="Address" hint={`${site.replace(/^https?:\/\//, "")}/${slug || "…"}`}>
              <input className={inputCls} value={slug} onChange={(e) => setSlug(e.target.value)} />
            </Field>
            <Btn variant="primary" busy={busy === "create"} disabled={!title || !slug} onClick={create}>
              Create
            </Btn>
          </div>
        </Card>
      )}

      {!pages ? (
        <Loading />
      ) : pages.length === 0 ? (
        <Empty>No pages yet.</Empty>
      ) : (
        <Card className="overflow-hidden p-0">
          <ul className="divide-y divide-black/5">
            {pages.map((p) => (
              <li key={p.id} className="flex flex-wrap items-center gap-4 px-5 py-4 transition hover:bg-black/[0.015]">
                <span className="bg-primary/10 text-primary grid size-10 shrink-0 place-items-center rounded-xl">
                  <FileText className="size-5" aria-hidden />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <Link href={`/pages/${p.id}`} className="font-semibold hover:underline">
                      {p.title}
                    </Link>
                    <StatusBadge status={p.status} />
                    {p.hasUnpublishedChanges && p.publishedAt && <Badge tone="amber">Unpublished changes</Badge>}
                    {p.kind === "CUSTOM" && <Badge tone="blue">Custom</Badge>}
                  </div>
                  <p className="text-ink-muted mt-0.5 text-xs">
                    /{p.slug} · Updated {fmtDate(p.updatedAt)}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <a href={`${site}/${p.slug}`} target="_blank" rel="noreferrer" aria-label={`View ${p.title}`}>
                    <Btn variant="ghost" size="sm">
                      <ExternalLink className="size-4" aria-hidden />
                    </Btn>
                  </a>
                  <Link href={`/pages/${p.id}`}>
                    <Btn size="sm">
                      <Pencil className="size-3.5" aria-hidden /> Edit
                    </Btn>
                  </Link>
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
