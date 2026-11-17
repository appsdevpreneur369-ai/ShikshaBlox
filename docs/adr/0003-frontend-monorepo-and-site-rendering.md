# ADR 0003: Frontend monorepo and multi-tenant site rendering

- Status: Accepted
- Date: 2026-11-17

## Decision
- pnpm workspace: `apps/site` (public school websites), `apps/admin` (CMS studio), `packages/ui` (design system + tokens), `packages/blocks` (block schemas), `packages/api-client` (generated), `packages/config` (tsconfig/eslint).
- Next.js 16 App Router, React 19, Tailwind CSS 4, TypeScript 5.9 (strict).
- **Host-based routing:** `apps/site/src/proxy.ts` validates the Host and rewrites every request to `/h/<host>/<path>`. ISR therefore caches each school's pages separately. The backend resolves the host to a tenant; the frontend never decides tenancy.
- **ISR + on-demand revalidation:** pages are statically cached; the backend calls a signed revalidation endpoint with cache tags (`site:<id>`, `page:<id>`) when content is published (M2/M5).
- **Block schema as a shared contract:** zod schemas in `packages/blocks` are exported to JSON Schema; the backend validates stored page documents against the same schema.
- **Typed API client:** springdoc produces `docs/api/openapi.json` (written by `ApplicationIT`); `openapi-typescript` generates `packages/api-client/src/schema.d.ts`; CI fails on drift.
- Design tokens are CSS custom properties (`--sb-*`) overridden per site theme, so components never hard-code school colours.
