# ADR 0008: Platform website (shikshablox.com) and host map

**Status:** accepted, 2026-11-26

## Context
ShikshaBlox is the core multi-tenant product (by Aurora Blossom); schools are tenants. Until now every public
host was rendered by `apps/site` as a school website. We need a product website, shikshablox.com, for sales
demos. It must be fast (Lighthouse SEO 100, performance ≥ 95 target), cheap to host, and independent of the tenant
renderer. This mirrors ClinicFlow247, where the core product site is separate from the clinics it powers.

## Decision
1. A new workspace app **`apps/www`** (Next.js App Router, TypeScript, Tailwind v4). Every page is **statically
   generated** at build time, with no runtime API calls. The only server code is `POST /api/demo-request` and
   `/api/health`.
2. **Host map** (production):

   | Host | Served by |
   |---|---|
   | `shikshablox.com`, `www.shikshablox.com` | `apps/www` |
   | `studio.shikshablox.com` | `apps/admin` (Studio, shared by all schools) |
   | `api.shikshablox.com` | backend |
   | `<school>.shikshablox.com` | `apps/site` (Web Free) |
   | school custom domain | `apps/site` (Web Pro, ADR 0004 verification) |

   `www`, `studio` and `app` are reserved subdomains in both `apps/site` (`isReservedSubdomain`) and the backend
   (`OrganizationService.RESERVED_SLUGS`), so no school can claim them.
3. **Demo requests use our own product.** The form posts to `apps/www`'s route handler, which validates and
   forwards to the existing public enquiry API with `host=<sb-sales tenant host>`. Leads land in the Studio enquiry
   inbox of the internal tenant **`sb-sales`** (seeded by `DemoSeeder`). The browser never calls the API directly and
   never names a tenant, so no CORS change is needed.
4. **Content is data**: modules, availability and targets (`src/data/modules.ts`), plans (`src/data/plans.ts`,
   backed by `docs/PRICING-MATH.md`), and showcased schools (`src/data/schools.ts`). Only M01 is shown as live;
   everything else carries a target quarter.
5. **Images** are pre-generated WebP variants (`scripts/gen-images.py`) served as static files through a custom
   `next/image` loader. There's no request-time resizing, so a cold Cloud Run instance is as fast as a warm one.
6. **Indexing**: `SB_WWW_NOINDEX=true` is baked into staging images (robots `Disallow: /`, `X-Robots-Tag: noindex`,
   meta robots). Production builds omit it. `SB_WWW_SITE_URL` sets canonical URLs and the sitemap.
7. **Staging**: Cloud Run service `shikshablox-www` (min 0, max 2 instances), so it costs nothing when idle.

## Consequences
- Marketing changes deploy without touching school sites, Studio or the API.
- Adding a showcased school is one entry in `schools.ts` (with the school's permission).
- When a module ships, update its `release` in `modules.ts` and the plan's status in `plans.ts`.
