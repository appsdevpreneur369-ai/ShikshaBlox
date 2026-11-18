"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Check, ImagePlus, Upload, X } from "lucide-react";
import { sb, thumb, type Media } from "@/lib/studio";
import { Btn, Loading, useAction } from "./kit";

/** Cache of media looked up by id, shared by all pickers on a screen. */
const cache = new Map<string, Media>();

export function useMedia(ids: string[]) {
  const [, force] = useState(0);
  const key = ids.join(",");
  useEffect(() => {
    const missing = ids.filter((id) => id && !cache.has(id));
    if (!missing.length) return;
    let alive = true;
    void Promise.all(missing.map((id) => sb<Media>(`media/${id}`).then((m) => cache.set(id, m)).catch(() => null))).then(
      () => alive && force((n) => n + 1),
    );
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  return (id: string) => cache.get(id);
}

export function MediaLibraryDialog({
  multiple,
  kind = "IMAGE",
  selected,
  onClose,
  onPick,
}: {
  multiple?: boolean;
  kind?: "IMAGE" | "DOCUMENT" | "VIDEO" | "ANY";
  selected: string[];
  onClose: () => void;
  onPick: (ids: string[]) => void;
}) {
  const [items, setItems] = useState<Media[] | null>(null);
  const [sel, setSel] = useState<string[]>(selected);
  const fileRef = useRef<HTMLInputElement>(null);
  const { busy, run } = useAction();

  const load = useCallback(
    () => sb<Media[]>(`media?limit=200${kind === "ANY" ? "" : `&kind=${kind}`}`).then((m) => {
      m.forEach((x) => cache.set(x.id, x));
      setItems(m);
    }),
    [kind],
  );
  useEffect(() => {
    void load();
  }, [load]);

  async function upload(files: FileList | null) {
    if (!files?.length) return;
    await run(
      "upload",
      async () => {
        for (const f of Array.from(files)) {
          const form = new FormData();
          form.append("file", f);
          const m = await sb<Media>("media?folder=Uploads", { form });
          cache.set(m.id, m);
          setSel((s) => (multiple ? [...s, m.id] : [m.id]));
        }
        await load();
      },
      "Uploaded",
    );
  }

  function toggle(id: string) {
    setSel((s) => (multiple ? (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]) : [id]));
  }

  return (
    <div role="dialog" aria-modal="true" aria-label="Media library" className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="flex max-h-[90vh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-black/5 px-5 py-4">
          <h2 className="text-lg font-semibold">Media library</h2>
          <div className="flex items-center gap-2">
            <input
              ref={fileRef}
              type="file"
              hidden
              multiple={multiple}
              accept={kind === "IMAGE" ? "image/*" : kind === "DOCUMENT" ? ".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx" : undefined}
              onChange={(e) => void upload(e.target.files)}
            />
            <Btn busy={busy === "upload"} onClick={() => fileRef.current?.click()}>
              <Upload className="size-4" aria-hidden /> Upload
            </Btn>
            <Btn variant="ghost" onClick={onClose} aria-label="Close">
              <X className="size-4" aria-hidden />
            </Btn>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto p-5">
          {!items ? (
            <Loading />
          ) : (
            <ul className="grid grid-cols-3 gap-3 sm:grid-cols-4 md:grid-cols-6">
              {items.map((m) => {
                const on = sel.includes(m.id);
                return (
                  <li key={m.id}>
                    <button
                      type="button"
                      onClick={() => toggle(m.id)}
                      className={`relative block aspect-square w-full overflow-hidden rounded-xl ring-2 transition ${on ? "ring-primary" : "ring-transparent hover:ring-black/20"}`}
                      title={m.altText || m.filename}
                    >
                      {m.kind === "IMAGE" ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img src={thumb(m)} alt={m.altText ?? ""} className="h-full w-full object-cover" />
                      ) : (
                        <span className="bg-surface-muted text-ink-muted flex h-full w-full items-center justify-center p-2 text-xs break-all">{m.filename}</span>
                      )}
                      {on && (
                        <span className="bg-primary absolute top-1.5 right-1.5 grid size-6 place-items-center rounded-full text-white">
                          <Check className="size-4" aria-hidden />
                        </span>
                      )}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
        <div className="flex items-center justify-between border-t border-black/5 px-5 py-3">
          <span className="text-ink-muted text-sm">{sel.length} selected</span>
          <div className="flex gap-2">
            <Btn onClick={onClose}>Cancel</Btn>
            <Btn variant="primary" onClick={() => onPick(sel)}>
              Use {multiple ? "selected" : "image"}
            </Btn>
          </div>
        </div>
      </div>
    </div>
  );
}

export function MediaField({
  value,
  onChange,
  kind = "IMAGE",
}: {
  value?: string;
  onChange: (id: string | undefined) => void;
  kind?: "IMAGE" | "DOCUMENT" | "VIDEO" | "ANY";
}) {
  const [open, setOpen] = useState(false);
  const get = useMedia(value ? [value] : []);
  const m = value ? get(value) : undefined;
  return (
    <div className="flex items-center gap-3">
      {value ? (
        m?.kind === "IMAGE" || !m ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={thumb(m)} alt="" className="bg-surface-muted size-20 rounded-xl object-cover ring-1 ring-black/5" />
        ) : (
          <span className="bg-surface-muted rounded-xl px-3 py-2 text-xs">{m.filename}</span>
        )
      ) : (
        <span className="bg-surface-muted text-ink-muted grid size-20 place-items-center rounded-xl">
          <ImagePlus className="size-6" aria-hidden />
        </span>
      )}
      <div className="flex gap-2">
        <Btn size="sm" onClick={() => setOpen(true)}>
          {value ? "Change" : "Choose"}
        </Btn>
        {value && (
          <Btn size="sm" variant="ghost" onClick={() => onChange(undefined)}>
            Remove
          </Btn>
        )}
      </div>
      {open && (
        <MediaLibraryDialog
          kind={kind}
          selected={value ? [value] : []}
          onClose={() => setOpen(false)}
          onPick={(ids) => {
            onChange(ids[0]);
            setOpen(false);
          }}
        />
      )}
    </div>
  );
}

export function MediaListField({ value, onChange }: { value: string[]; onChange: (ids: string[]) => void }) {
  const [open, setOpen] = useState(false);
  const get = useMedia(value);
  return (
    <div className="space-y-2">
      <ul className="flex flex-wrap gap-2">
        {value.map((id, i) => (
          <li key={id} className="group relative">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={thumb(get(id))} alt="" className="bg-surface-muted size-16 rounded-lg object-cover ring-1 ring-black/5" />
            <button
              type="button"
              aria-label="Remove image"
              onClick={() => onChange(value.filter((_, j) => j !== i))}
              className="absolute -top-1.5 -right-1.5 hidden size-5 place-items-center rounded-full bg-red-600 text-white group-hover:grid"
            >
              <X className="size-3" aria-hidden />
            </button>
          </li>
        ))}
      </ul>
      <Btn size="sm" onClick={() => setOpen(true)}>
        <ImagePlus className="size-4" aria-hidden /> {value.length ? "Add or change photos" : "Choose photos"}
      </Btn>
      {open && (
        <MediaLibraryDialog
          multiple
          selected={value}
          onClose={() => setOpen(false)}
          onPick={(ids) => {
            onChange(ids);
            setOpen(false);
          }}
        />
      )}
    </div>
  );
}
