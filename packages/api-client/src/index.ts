import createClient, { type ClientOptions } from "openapi-fetch";
import type { paths } from "./schema";

export type { paths, components } from "./schema";

/**
 * Typed client generated from the backend's OpenAPI document (docs/api/openapi.json).
 * Regenerate with `pnpm api:generate` after backend API changes; CI fails on drift.
 */
export function createApiClient(options: ClientOptions) {
  return createClient<paths>(options);
}
