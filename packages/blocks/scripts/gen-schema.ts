// Writes the page-document JSON Schema used by the backend validator.
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { toJsonSchema } from "../src/index";

const out = resolve(
  import.meta.dirname,
  "../../../backend/src/main/resources/schemas/page-document.schema.json",
);
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify(toJsonSchema(), null, 2) + "\n");
console.log(`Wrote ${out}`);
