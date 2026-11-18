"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AlertTriangle, ShieldCheck, Upload } from "lucide-react";
import { sb, thumb, type Media } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Field, Loading, PageHeader, inputCls, useAction } from "@/components/kit";

export default function MediaPage() {
  const [items, setItems] = useState<Media[] | null>(null);
  const [sel, setSel] = useState<Media | null>(null);
  const [alt, setAlt] = useState("");
  const [caption, setCaption] = useState("");
  const [students, setStudents] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const { busy, run } = useAction();

  const load = useCallback(() => sb<Media[]>("media?limit=200").then(setItems), []);
  useEffect(() => {
    void load();
  }, [load]);

  function pick(m: Media) {
    setSel(m);
    setAlt(m.altText ?? "");
    setCaption(m.caption ?? "");
    setStudents(m.containsIdentifiableStudents);
  }

  async function upload(files: FileList | null) {
    if (!files?.length) return;
    await run(
      "up",
      async () => {
        for (const f of Array.from(files)) {
          const form = new FormData();
          form.append("file", f);
          await sb("media?folder=Uploads", { form });
        }
      },
      `Uploaded ${files.length} file${files.length > 1 ? "s" : ""}`,
    );
    void load();
  }

  async function save() {
    if (!sel) return;
    const m = await run(
      "save",
      () => sb<Media>(`media/${sel.id}`, { method: "PATCH", body: { altText: alt, caption, containsIdentifiableStudents: students } }),
      "Saved",
    );
    if (m) {
      setSel(m);
      void load();
    }
  }

  return (
    <div>
      <PageHeader
        title="Media library"
        subtitle="Images are resized automatically for fast loading on phones."
        actions={
          <>
            <input ref={fileRef} type="file" multiple hidden onChange={(e) => void upload(e.target.files)} />
            <Btn variant="primary" busy={busy === "up"} onClick={() => fileRef.current?.click()}>
              <Upload className="size-4" aria-hidden /> Upload
            </Btn>
          </>
        }
      />
      <div className="grid gap-5 lg:grid-cols-[1fr_340px]">
        <div>
          {!items ? (
            <Loading />
          ) : items.length === 0 ? (
            <Empty>No media yet.</Empty>
          ) : (
            <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-5">
              {items.map((m) => (
                <li key={m.id}>
                  <button
                    type="button"
                    onClick={() => pick(m)}
                    className={`group relative block aspect-square w-full overflow-hidden rounded-xl bg-white ring-2 transition ${sel?.id === m.id ? "ring-primary" : "ring-transparent hover:ring-black/15"}`}
                  >
                    {m.kind === "IMAGE" ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={thumb(m)} alt={m.altText ?? ""} className="h-full w-full object-cover transition duration-500 group-hover:scale-105" />
                    ) : (
                      <span className="text-ink-muted flex h-full items-center justify-center p-3 text-xs break-all">{m.filename}</span>
                    )}
                    {!m.publishable && (
                      <span className="absolute top-1.5 left-1.5 rounded-full bg-amber-400 px-2 py-0.5 text-[10px] font-bold text-amber-950">Needs consent</span>
                    )}
                    {m.needsAltText && m.publishable && (
                      <span className="absolute top-1.5 left-1.5 rounded-full bg-white/90 px-2 py-0.5 text-[10px] font-bold text-gray-700">No alt text</span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        {sel && (
          <Card className="h-fit lg:sticky lg:top-24">
            {sel.kind === "IMAGE" && (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={sel.variants?.medium?.url ?? thumb(sel)} alt="" className="mb-4 w-full rounded-xl" />
            )}
            <p className="truncate text-sm font-medium">{sel.filename}</p>
            <p className="text-ink-muted mb-4 text-xs">{Math.round(sel.sizeBytes / 1024)} KB</p>
            <div className="space-y-3">
              <Field label="Alt text" hint="Describe the image for visitors using screen readers.">
                <input className={inputCls} value={alt} onChange={(e) => setAlt(e.target.value)} maxLength={300} />
              </Field>
              <Field label="Caption">
                <input className={inputCls} value={caption} onChange={(e) => setCaption(e.target.value)} maxLength={1000} />
              </Field>
              <label className="flex items-start gap-2 text-sm">
                <input type="checkbox" className="accent-primary mt-0.5 size-4" checked={students} onChange={(e) => setStudents(e.target.checked)} />
                <span>Shows students who can be identified</span>
              </label>
              {sel.publishable ? (
                <Badge tone="green">
                  <ShieldCheck className="mr-1 size-3.5" aria-hidden /> Can appear on the website
                </Badge>
              ) : (
                <p className="flex gap-2 rounded-xl bg-amber-50 p-3 text-xs text-amber-900">
                  <AlertTriangle className="size-4 shrink-0" aria-hidden /> Hidden from the website until a consent record covers it (Settings → Consent).
                </p>
              )}
              <Btn variant="primary" busy={busy === "save"} onClick={() => void save()}>
                Save
              </Btn>
            </div>
          </Card>
        )}
      </div>
    </div>
  );
}
