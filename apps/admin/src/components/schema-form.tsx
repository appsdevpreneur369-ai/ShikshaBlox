"use client";

import { ArrowDown, ArrowUp, Plus, Trash2 } from "lucide-react";
import { toJsonSchema, type BlockType } from "@shikshablox/blocks";
import { Btn, Field, inputCls } from "./kit";
import { MediaField, MediaListField } from "./media-picker";
import { RichText } from "./rich-text";

/**
 * Forms generated from the block JSON Schema (the same schema the backend validates against), so
 * every block type gets an editor without hand-written forms.
 */

export type JsonSchema = {
  type?: string;
  enum?: string[];
  const?: unknown;
  anyOf?: JsonSchema[];
  properties?: Record<string, JsonSchema>;
  required?: string[];
  items?: JsonSchema;
  format?: string;
  maxLength?: number;
  minLength?: number;
  maxItems?: number;
  minimum?: number;
  maximum?: number;
};

type Root = { properties: { blocks: { items: { oneOf: JsonSchema[] } } } };
const ROOT = toJsonSchema() as Root;

export const BLOCK_PROPS: Record<string, JsonSchema> = Object.fromEntries(
  ROOT.properties.blocks.items.oneOf.map((b) => [b.properties!.type!.const as string, b.properties!.props!]),
);

export const BLOCK_INFO: Partial<Record<BlockType, { label: string; hint: string }>> = {
  hero: { label: "Hero banner", hint: "Big heading with image and buttons" },
  richText: { label: "Text", hint: "Paragraphs, headings and lists" },
  imageText: { label: "Image + text", hint: "Picture beside a paragraph" },
  image: { label: "Image", hint: "A single photo with caption" },
  galleryStrip: { label: "Photo gallery", hint: "Grid of photos" },
  cards: { label: "Cards", hint: "Features, programmes or highlights" },
  cta: { label: "Call to action", hint: "Coloured banner with buttons" },
  stats: { label: "Numbers", hint: "Key facts and figures" },
  testimonials: { label: "Testimonials", hint: "Quotes from parents and alumni" },
  steps: { label: "Steps", hint: "Numbered process, e.g. how to apply" },
  faq: { label: "FAQs", hint: "Questions and answers" },
  map: { label: "Map", hint: "Google map of a place" },
  video: { label: "Video", hint: "YouTube, Vimeo or uploaded MP4" },
  downloads: { label: "Downloads", hint: "Documents to download" },
  contactDetails: { label: "Contact details", hint: "Address, phone, email and map" },
  noticesFeed: { label: "Notice board", hint: "Latest notices" },
  newsFeed: { label: "News", hint: "Latest news stories" },
  eventsFeed: { label: "Events", hint: "Upcoming or past events" },
  albumsFeed: { label: "Photo albums", hint: "Gallery albums" },
  staffGrid: { label: "Staff directory", hint: "Teachers and staff" },
  careersFeed: { label: "Job openings", hint: "Current vacancies" },
  enquiryForm: { label: "Enquiry form", hint: "Admissions enquiry form" },
  customHtml: { label: "Custom HTML", hint: "Embed code (administrators only)" },
};

const LABELS: Record<string, string> = {
  imageId: "Image",
  imageIds: "Photos",
  primaryCta: "Main button",
  secondaryCta: "Second button",
  primary: "Main button",
  secondary: "Second button",
  cta: "Button",
  href: "Link",
  html: "Text",
  subheading: "Text under heading",
  eyebrow: "Small label above heading",
  imagePosition: "Image side",
  mediaId: "File",
  videoId: "Video ID",
  refId: "Reusable block",
  showMap: "Show map",
};

const ENUM_LABELS: Record<string, string> = {
  fullBleed: "Full-width photo",
  split: "Photo beside text",
  centered: "Centred text",
  narrow: "Narrow",
  wide: "Wide",
  full: "Full width",
  medium: "Medium",
  grid: "Grid",
  masonry: "Masonry",
  inline: "Written here",
  faqs: "From the FAQ list",
  upcoming: "Upcoming",
  past: "Past",
  all: "All",
};

function label(key: string) {
  return LABELS[key] ?? key.replace(/([A-Z])/g, " $1").replace(/^./, (c) => c.toUpperCase());
}

/** A sensible empty value for a schema (used when adding blocks and list items). */
export function defaultFor(s: JsonSchema, key = ""): unknown {
  if (s.enum) return s.enum[0];
  if (s.anyOf) return (s.anyOf.find((a) => a.const === 3) ?? s.anyOf[0])?.const;
  switch (s.type) {
    case "string":
      return s.minLength ? (key === "href" ? "/" : key === "label" ? "Learn more" : key === "query" ? "Guntur, Andhra Pradesh" : `New ${label(key).toLowerCase()}`) : undefined;
    case "number":
    case "integer":
      return s.minimum && s.minimum > 1 ? s.minimum : 6;
    case "boolean":
      return false;
    case "array":
      return [];
    case "object": {
      const o: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(s.properties ?? {})) {
        if (s.required?.includes(k)) o[k] = defaultFor(v, k);
      }
      return o;
    }
  }
  return undefined;
}

export function SchemaFields({
  schema,
  value,
  onChange,
}: {
  schema: JsonSchema;
  value: Record<string, unknown>;
  onChange: (v: Record<string, unknown>) => void;
}) {
  return (
    <div className="grid gap-4">
      {Object.entries(schema.properties ?? {}).map(([key, s]) => (
        <SchemaField
          key={key}
          name={key}
          schema={s}
          required={schema.required?.includes(key) ?? false}
          value={value[key]}
          onChange={(v) => {
            const next = { ...value };
            if (v === undefined || v === "") delete next[key];
            else next[key] = v;
            onChange(next);
          }}
        />
      ))}
    </div>
  );
}

function SchemaField({
  name,
  schema: s,
  required,
  value,
  onChange,
}: {
  name: string;
  schema: JsonSchema;
  required: boolean;
  value: unknown;
  onChange: (v: unknown) => void;
}) {
  const lbl = `${label(name)}${required ? "" : " (optional)"}`;

  if (s.enum) {
    return (
      <Field label={lbl}>
        <select className={inputCls} value={(value as string) ?? ""} onChange={(e) => onChange(e.target.value || undefined)}>
          {!required && <option value="">—</option>}
          {s.enum.map((o) => (
            <option key={o} value={o}>
              {ENUM_LABELS[o] ?? label(o)}
            </option>
          ))}
        </select>
      </Field>
    );
  }
  if (s.anyOf) {
    return (
      <Field label={lbl}>
        <select className={inputCls} value={String(value ?? "")} onChange={(e) => onChange(Number(e.target.value))}>
          {s.anyOf.map((o) => (
            <option key={String(o.const)} value={String(o.const)}>
              {String(o.const)}
            </option>
          ))}
        </select>
      </Field>
    );
  }
  if (s.type === "string" && s.format === "uuid") {
    if (name === "refId") return null;
    return (
      <Field label={lbl}>
        <MediaField value={value as string} onChange={onChange} kind={name === "mediaId" ? "ANY" : "IMAGE"} />
      </Field>
    );
  }
  if (s.type === "string" && (name === "html" || name === "bodyHtml")) {
    return (
      <div className="space-y-1.5">
        <span className="text-sm font-medium">{lbl}</span>
        <RichText value={(value as string) ?? ""} onChange={onChange} />
      </div>
    );
  }
  if (s.type === "string") {
    const long = (s.maxLength ?? 0) > 200;
    return (
      <Field label={lbl}>
        {long ? (
          <textarea className={inputCls} rows={3} maxLength={s.maxLength} value={(value as string) ?? ""} onChange={(e) => onChange(e.target.value)} />
        ) : (
          <input className={inputCls} maxLength={s.maxLength} value={(value as string) ?? ""} onChange={(e) => onChange(e.target.value)} />
        )}
      </Field>
    );
  }
  if (s.type === "number" || s.type === "integer") {
    return (
      <Field label={lbl}>
        <input
          type="number"
          className={inputCls}
          min={s.minimum}
          max={s.maximum}
          value={(value as number) ?? ""}
          onChange={(e) => onChange(e.target.value === "" ? undefined : Number(e.target.value))}
        />
      </Field>
    );
  }
  if (s.type === "boolean") {
    return (
      <label className="flex items-center gap-2 text-sm font-medium">
        <input type="checkbox" checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} className="accent-primary size-4" />
        {label(name)}
      </label>
    );
  }
  if (s.type === "object") {
    const v = (value as Record<string, unknown>) ?? undefined;
    return (
      <fieldset className="rounded-xl border border-black/10 p-3">
        <legend className="px-1 text-sm font-medium">{lbl}</legend>
        {v ? (
          <div className="space-y-3">
            <SchemaFields schema={s} value={v} onChange={onChange} />
            {!required && (
              <Btn size="sm" variant="ghost" onClick={() => onChange(undefined)}>
                Remove {label(name).toLowerCase()}
              </Btn>
            )}
          </div>
        ) : (
          <Btn size="sm" onClick={() => onChange(defaultFor(s))}>
            <Plus className="size-3.5" aria-hidden /> Add {label(name).toLowerCase()}
          </Btn>
        )}
      </fieldset>
    );
  }
  if (s.type === "array" && s.items?.format === "uuid") {
    return (
      <div className="space-y-1.5">
        <span className="text-sm font-medium">{lbl}</span>
        <MediaListField value={(value as string[]) ?? []} onChange={onChange} />
      </div>
    );
  }
  if (s.type === "array" && s.items?.type === "object") {
    const list = (value as Record<string, unknown>[]) ?? [];
    const move = (i: number, d: number) => {
      const next = [...list];
      const [x] = next.splice(i, 1);
      next.splice(i + d, 0, x!);
      onChange(next);
    };
    return (
      <div className="space-y-2">
        <span className="text-sm font-medium">{lbl}</span>
        {list.map((item, i) => (
          <div key={i} className="rounded-xl border border-black/10 bg-black/[0.015] p-3">
            <div className="mb-2 flex items-center justify-between">
              <span className="text-ink-muted text-xs font-semibold">
                {label(name).replace(/s$/, "")} {i + 1}
              </span>
              <div className="flex gap-1">
                <Btn size="sm" variant="ghost" disabled={i === 0} onClick={() => move(i, -1)} aria-label="Move up">
                  <ArrowUp className="size-3.5" aria-hidden />
                </Btn>
                <Btn size="sm" variant="ghost" disabled={i === list.length - 1} onClick={() => move(i, 1)} aria-label="Move down">
                  <ArrowDown className="size-3.5" aria-hidden />
                </Btn>
                <Btn size="sm" variant="ghost" onClick={() => onChange(list.filter((_, j) => j !== i))} aria-label="Remove">
                  <Trash2 className="size-3.5 text-red-600" aria-hidden />
                </Btn>
              </div>
            </div>
            <SchemaFields schema={s.items!} value={item} onChange={(v) => onChange(list.map((x, j) => (j === i ? v : x)))} />
          </div>
        ))}
        {(s.maxItems === undefined || list.length < s.maxItems) && (
          <Btn size="sm" onClick={() => onChange([...list, defaultFor(s.items!)])}>
            <Plus className="size-3.5" aria-hidden /> Add {label(name).toLowerCase().replace(/s$/, "")}
          </Btn>
        )}
      </div>
    );
  }
  return null;
}
