import { describe, expect, it } from "vitest";
import { pageDocument, toJsonSchema } from "./index";

describe("pageDocument", () => {
  it("accepts a valid document", () => {
    const doc = {
      schemaVersion: 1,
      blocks: [{ id: "b1", type: "richText", props: { html: "<p>Hi</p>" } }],
    };
    expect(pageDocument.parse(doc)).toEqual(doc);
  });

  it("rejects unknown block types", () => {
    const doc = { schemaVersion: 1, blocks: [{ id: "b1", type: "nope", props: {} }] };
    expect(pageDocument.safeParse(doc).success).toBe(false);
  });

  it("exports a JSON Schema for the backend", () => {
    const schema = toJsonSchema() as { type: string };
    expect(schema.type).toBe("object");
  });
});

describe("block catalogue", () => {
  it("validates hero links and rejects javascript: URLs", () => {
    const ok = {
      schemaVersion: 1,
      blocks: [
        {
          id: "h",
          type: "hero",
          props: {
            variant: "split",
            heading: "Welcome",
            primaryCta: { label: "Apply", href: "/admissions" },
          },
        },
      ],
    };
    expect(pageDocument.safeParse(ok).success).toBe(true);
    const bad = structuredClone(ok);
    bad.blocks[0]!.props.primaryCta.href = "javascript:alert(1)";
    expect(pageDocument.safeParse(bad).success).toBe(false);
  });

  it("requires media ids to be UUIDs", () => {
    const doc = {
      schemaVersion: 1,
      blocks: [{ id: "i", type: "image", props: { imageId: "../etc/passwd", size: "wide" } }],
    };
    expect(pageDocument.safeParse(doc).success).toBe(false);
  });
});
