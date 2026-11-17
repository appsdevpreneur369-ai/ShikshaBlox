import { z } from "zod";

/**
 * Block schema registry: the single source of truth for page content.
 *
 * Pages are stored as a versioned JSON document `{ schemaVersion, blocks: Block[] }`, one per
 * language. The backend validates documents against the JSON Schema generated from this file
 * (`pnpm --filter @shikshablox/blocks gen:schema`) and re-sanitises every HTML field.
 * Images are referenced by media id; the public API resolves ids to URLs and drops media that
 * may not be published (consent rule).
 */
export const BLOCK_SCHEMA_VERSION = 1 as const;

const id = z.string().min(1).max(64);
const mediaId = z.uuid();
const text = (max: number) => z.string().max(max);
const html = z.string().max(100_000);
/** Internal path ("/admissions"), anchor ("#enquire"), mailto/tel, or https URL. */
const href = z
  .string()
  .max(500)
  .regex(/^(\/[^\s]*|#[\w-]*|mailto:[^\s]+|tel:[+\d\s-]+|https:\/\/[^\s]+)$/);
const link = z.object({ label: text(60).min(1), href });

export const blockSettings = z
  .object({
    background: z.enum(["default", "muted", "primary", "accent"]).optional(),
    anchor: z
      .string()
      .regex(/^[a-z0-9-]{1,40}$/)
      .optional(),
    spacing: z.enum(["compact", "normal", "spacious"]).optional(),
  })
  .optional();

function def<T extends string, P extends z.ZodRawShape>(type: T, props: P) {
  return z.object({
    id,
    type: z.literal(type),
    props: z.object(props),
    settings: blockSettings,
  });
}

const heading = text(160).optional();
const intro = text(600).optional();

export const blocks = {
  hero: def("hero", {
    variant: z.enum(["centered", "split", "fullBleed"]),
    eyebrow: text(80).optional(),
    heading: text(160).min(1),
    subheading: text(400).optional(),
    imageId: mediaId.optional(),
    primaryCta: link.optional(),
    secondaryCta: link.optional(),
  }),
  richText: def("richText", { html, width: z.enum(["narrow", "wide"]).optional() }),
  imageText: def("imageText", {
    eyebrow: text(80).optional(),
    heading,
    html,
    imageId: mediaId.optional(),
    imagePosition: z.enum(["left", "right"]),
    cta: link.optional(),
  }),
  image: def("image", {
    imageId: mediaId,
    caption: text(300).optional(),
    size: z.enum(["medium", "wide", "full"]),
  }),
  galleryStrip: def("galleryStrip", {
    heading,
    intro,
    imageIds: z.array(mediaId).max(24),
    layout: z.enum(["grid", "masonry"]),
    cta: link.optional(),
  }),
  cards: def("cards", {
    heading,
    intro,
    columns: z.union([z.literal(2), z.literal(3), z.literal(4)]),
    items: z
      .array(
        z.object({
          title: text(120).min(1),
          text: text(600).optional(),
          imageId: mediaId.optional(),
          icon: text(40).optional(),
          href: href.optional(),
        }),
      )
      .max(12),
  }),
  cta: def("cta", {
    heading: text(160).min(1),
    text: text(400).optional(),
    primary: link,
    secondary: link.optional(),
  }),
  stats: def("stats", {
    heading,
    items: z.array(z.object({ value: text(20).min(1), label: text(80).min(1) })).max(6),
  }),
  testimonials: def("testimonials", {
    heading,
    items: z
      .array(z.object({ quote: text(600).min(1), author: text(80).min(1), role: text(80).optional() }))
      .max(9),
  }),
  steps: def("steps", {
    heading,
    intro,
    items: z.array(z.object({ title: text(120).min(1), text: text(400).optional() })).max(10),
  }),
  faq: def("faq", {
    heading,
    source: z.enum(["inline", "faqs"]),
    items: z.array(z.object({ question: text(300).min(1), answer: text(2000).min(1) })).max(40),
  }),
  map: def("map", {
    heading,
    query: text(300).min(1),
    height: z.enum(["small", "medium", "large"]).optional(),
  }),
  video: def("video", {
    heading,
    provider: z.enum(["youtube", "vimeo", "upload"]),
    videoId: z
      .string()
      .regex(/^[\w-]{6,20}$/)
      .optional(),
    mediaId: mediaId.optional(),
    title: text(160).min(1),
    caption: text(300).optional(),
  }),
  downloads: def("downloads", {
    heading,
    items: z.array(z.object({ label: text(160).min(1), mediaId })).max(40),
  }),
  contactDetails: def("contactDetails", { heading, showMap: z.boolean().optional() }),
  noticesFeed: def("noticesFeed", { heading, limit: z.number().int().min(1).max(50) }),
  newsFeed: def("newsFeed", { heading, limit: z.number().int().min(1).max(50) }),
  eventsFeed: def("eventsFeed", {
    heading,
    limit: z.number().int().min(1).max(50),
    show: z.enum(["upcoming", "past", "all"]),
  }),
  albumsFeed: def("albumsFeed", { heading, limit: z.number().int().min(1).max(50) }),
  staffGrid: def("staffGrid", { heading, intro, department: text(80).optional() }),
  careersFeed: def("careersFeed", { heading, intro }),
  enquiryForm: def("enquiryForm", { heading, intro }),
  customHtml: def("customHtml", { html }),
  reusable: def("reusable", { refId: z.uuid() }),
} as const;

export type BlockType = keyof typeof blocks;
export const BLOCK_TYPES = Object.keys(blocks) as BlockType[];

export const block = z.discriminatedUnion("type", [
  blocks.hero,
  blocks.richText,
  blocks.imageText,
  blocks.image,
  blocks.galleryStrip,
  blocks.cards,
  blocks.cta,
  blocks.stats,
  blocks.testimonials,
  blocks.steps,
  blocks.faq,
  blocks.map,
  blocks.video,
  blocks.downloads,
  blocks.contactDetails,
  blocks.noticesFeed,
  blocks.newsFeed,
  blocks.eventsFeed,
  blocks.albumsFeed,
  blocks.staffGrid,
  blocks.careersFeed,
  blocks.enquiryForm,
  blocks.customHtml,
  blocks.reusable,
]);
export type Block = z.infer<typeof block>;
export type BlockOf<T extends BlockType> = Extract<Block, { type: T }>;

export const pageDocument = z.object({
  schemaVersion: z.literal(BLOCK_SCHEMA_VERSION),
  blocks: z.array(block).max(200),
});
export type PageDocument = z.infer<typeof pageDocument>;

/** Blocks only a school administrator may add (raw HTML). */
export const ADMIN_ONLY_BLOCKS: BlockType[] = ["customHtml"];

export function toJsonSchema(): unknown {
  return z.toJSONSchema(pageDocument, { target: "draft-2020-12" });
}

export function emptyDocument(): PageDocument {
  return { schemaVersion: BLOCK_SCHEMA_VERSION, blocks: [] };
}
