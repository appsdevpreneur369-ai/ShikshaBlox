import { z } from "zod";

/**
 * Block schema registry: the single source of truth for page content.
 *
 * Pages are stored as a versioned JSON document `{ schemaVersion, blocks: Block[] }`.
 * The backend validates the same document against the JSON Schema exported from here
 * (see `toJsonSchema`). The full block catalogue lands in M2; M0 ships the envelope and
 * one block so the pipeline (zod -> JSON Schema -> backend validator) is exercised end to end.
 */
export const BLOCK_SCHEMA_VERSION = 1 as const;

const blockBase = {
  id: z.string().min(1).max(64),
};

export const richTextBlock = z.object({
  ...blockBase,
  type: z.literal("richText"),
  props: z.object({
    /** Sanitised HTML produced by the editor. The server re-sanitises on save. */
    html: z.string().max(200_000),
  }),
});

export const block = z.discriminatedUnion("type", [richTextBlock]);
export type Block = z.infer<typeof block>;

export const pageDocument = z.object({
  schemaVersion: z.literal(BLOCK_SCHEMA_VERSION),
  blocks: z.array(block).max(200),
});
export type PageDocument = z.infer<typeof pageDocument>;

export function toJsonSchema(): unknown {
  return z.toJSONSchema(pageDocument, { target: "draft-2020-12" });
}
