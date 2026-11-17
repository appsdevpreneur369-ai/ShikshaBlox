# ShikshaBlox M01 — Progress & resume point

**Saved 2026-11-17 at user request (power outage). Resume here when the user says "resume".**

## Done and pushed (GitHub appsdevpreneur369-ai/ShikshaBlox)
- `main`: M0 dd1660f (scaffold), M1a d03e3a2 (tenancy, RLS, auth: password/email OTP/TOTP, JWT+rotating refresh, roles, admin BFF login, seed), M1b 82596d1 (plans Web Free/Pro + entitlements, config hierarchy, append-only audit, media pipeline with WebP variants via cwebp + private originals, consent registry, notifications, studio Plan/Settings/Audit pages).
- Branch `wip/m2-cms` (pushed): M2 backend done, 50/50 backend tests green:
  - `packages/blocks/src/index.ts`: 24 block types (hero, richText, imageText, image, galleryStrip, cards, cta, stats, testimonials, steps, faq, map, video, downloads, contactDetails, noticesFeed, newsFeed, eventsFeed, albumsFeed, staffGrid, careersFeed, enquiryForm, customHtml, reusable); `pnpm --filter @shikshablox/blocks gen:schema` writes `backend/src/main/resources/schemas/page-document.schema.json`.
  - Migration `V2026_11_17_0004__website_cms.sql` (site, page, page_content per language, page_revision, menu, reusable_block; RLS).
  - `modules/website`: DocumentValidator (networknt 1.5.9 + OWASP sanitizer), SiteService, SiteTemplates (14 standard pages + default menus), PageService (custom_pages UsageMeter), NavigationService, OnboardingService, PublicSiteService/Controller (`/api/public/v1/site`, `/site/page`, `/site/paths`), SiteRevalidator (POST to SITE_REVALIDATE_URL with X-Revalidate-Secret, tags `host:<host>`), WebsiteAdminController (`/api/admin/v1/site|pages|menus|reusable-blocks`), WebsiteIT.

## Next steps (M2 remainder), in order
1. `apps/site` renderer: fetch site+page with tags `host:<host>`; themes classic/modern/playful via CSS vars + data-theme; next/font set (Inter, Poppins, Merriweather, Playfair Display, Nunito, Lora, Baloo 2, preload false); header w/ dropdowns + mobile nav, footer ("© {year} school" + "aurora-blossom.com" credit always; ShikshaBlox badge unless showBadge=false); block renderers with rounded corners, hover lift/zoom, CSS scroll reveal, prefers-reduced-motion; `<img srcset>` from media map (no Next optimizer); `app/api/revalidate/route.ts` using `revalidateTag(tag, { expire: 0 })` (Next 16 signature needs 2nd arg); proxy matcher must exclude `api/`; CSP allowing media host + youtube-nocookie/vimeo/google maps.
2. Admin CMS UI: onboarding wizard, pages list, block editor (generic field-spec forms, @dnd-kit ordering, TipTap rich text), menu builder, theme & brand page, media library.
3. Moriah seed: curated best images from `old legacy Site Content/Images` copied into backend seed resources; restructure content into the 14 pages (no individuals' personal contacts/staff names; identifiable children photos need consent record — flag "migrated, school to confirm"). Moriah = WEB_PRO.
4. Compose: add SITE_REVALIDATE_URL/SECRET to backend and site; then E2E; merge wip branch to main as "M2"; then M3..M7, then staging.

## Staging (see shikshablox-staging)
GCP project `project-87cd233e-951b-43e4-886` (APIs run/artifactregistry/secretmanager/iam already enabled). Waiting on user: Supabase `STAGING_DB_URL` in `.env` (not yet provided as of save).

## Gotchas
- Bash heredocs with apostrophes break in Git Bash: use Write tool / python for files.
- Windows Python/Node don't resolve *.localhost: patch getaddrinfo to 127.0.0.1 (scratchpad media_check.py pattern); curl works.
- Spotless rewraps Java after edits — re-read before string replacements; run `mvnw -q spotless:apply` before verify. Maven: `MAVEN_OPTS="-Xmx768m -XX:+UseSerialGC"`, `-DargLine="-Xmx512m -XX:+UseSerialGC"`; build Docker images one at a time.
- Editing an already-applied Flyway migration locally requires removing volume `shikshablox_pgdata` (dev data only).
- `TenantContext` must be opened before a transaction (throws inside tx). FORCE RLS binds the owner too.

Related: m01-decisions, shikshablox-staging
