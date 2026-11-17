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
