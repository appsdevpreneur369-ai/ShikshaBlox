# ShikshaBlox M01 — Progress & resume point

**Updated 2026-11-26: product website shikshablox.com (`apps/www`) built and on staging.**

## Product website (apps/www), 2026-11-26
- Static Next.js site for sales demos: 27 pages (home, features + 12 module pages, 4 solution pages, pricing,
  schools/Moriah case study, roadmap, why us, security, about, contact, privacy, terms). Architecture: ADR 0008.
- Honest availability: only M01 is "Live"; every other module shows a target quarter (`src/data/modules.ts`).
  Prices and roadmap estimates: `docs/PRICING-MATH.md` (founding offer changed to −25% for 25 schools; included
  WhatsApp credits removed; ERP plans are pre-booked and billed only at go-live).
- Demo form → `/api/demo-request` → public enquiry API as internal tenant `sb-sales` (seeded) → Studio inbox
  (sign in as `admin@sb-sales.test`).
- Images: free Unsplash photos, self-hosted, pre-generated WebP variants (`scripts/gen-images.py`); credits in
  `apps/www/IMAGE-CREDITS.md`. Product screenshots are of the live Moriah site.
- Tests: `e2e/tests/www.spec.ts` (7 device profiles: pages/H1/meta/canonical/footer/overflow, images, links,
  axe WCAG 2.1 AA, desktop + touch navigation, pricing, robots, form) via `playwright.www.config.ts`.
- Staging: Cloud Run `shikshablox-www` (min 0), built with `SB_WWW_NOINDEX=true`.
- Domain go-live checklist (shikshablox.com is not purchased yet):
  1. Buy shikshablox.com; in Cloud Run create domain mappings for `shikshablox.com` and `www.shikshablox.com`
     (or a global HTTPS load balancer with a serverless NEG), add the DNS records Google shows, wait for TLS.
  2. Redirect `www` → apex (load balancer URL map, or a tiny redirect service).
  3. Rebuild www **without** `SB_WWW_NOINDEX` and with `SB_WWW_SITE_URL=https://shikshablox.com`
     (cloudbuild `_WWW_URL`), plus `SB_WWW_WHATSAPP` / `SB_WWW_PHONE` / `SB_WWW_EMAIL` once sales contacts exist.
  4. Submit `https://shikshablox.com/sitemap.xml` in Google Search Console; re-run PageSpeed Insights.

## Done (pushed to GitHub appsdevpreneur369-ai/ShikshaBlox, `main`)
- M0 scaffold, M1a tenancy/RLS/auth, M1b entitlements/config/audit/media/consent (see git history).
- **M2 CMS core:** block schema (24 blocks), sites/themes/menus/pages/revisions, onboarding with 14 standard pages;
  `apps/site` renderer (themes via CSS tokens, next/font set, header with dropdowns + mobile menu, footer credits
  aurora-blossom.com + ShikshaBlox badge, CSS scroll reveal, reduced motion, `<img srcset>` variants, on-demand
  revalidation `POST /api/revalidate` — revalidates tag + school path tree); Studio: Pages list, block editor
  (schema-driven forms, rich text, media picker, reorder/duplicate/delete, section style), Media library, Design & profile.
- **M3 content types** (`content_item`, one validated JSON store): notices, news, events, albums, staff, FAQs, careers,
  disclosures; public feeds `/api/public/v1/site/feeds/{type}` (+ detail `/{slug}` for news/events/albums);
  Studio Content manager.
- **M4:** enquiry form → `/api/public/v1/enquiries` (rate limits, honeypot, consent), email alert to school admins,
  `EnquiryReceived` event, Studio inbox (status + notes); disclosures type + "last reviewed" + daily reminder job.
- **M5:** submit for review / request changes / approve & publish, scheduled publishing (minute job), rollback to a
  revision, signed 2-hour draft preview links (`/preview/<token>`, separator `~`).
- **Moriah seed** (`MoriahSiteSeeder`, `seed/moriah/site.json` + 32 curated photos): runs once when Moriah isn't
  onboarded. Demo runbook + content-to-confirm list: `docs/DEMO-MORIAH.md`.

## Tests
- Backend `mvnw verify`: 50/50 green (no new backend ITs yet for content/enquiry/publishing — covered by E2E).
- E2E `e2e/tests/cms.spec.ts` (desktop): 4/4 green against the local dev stack.

## Next
0. Product website: add sales WhatsApp/phone/email (build args), buy the domain (checklist above).
1. Backend ITs for ContentService / EnquiryService / PublishingService (RLS, validation, workflow).
2. Menu builder UI; staff photos consent UX; TipTap if richer editing is needed.
3. M6 SEO/i18n/a11y (sitemap.xml, robots, structured data, hreflang, Lighthouse), M7 domains/export/hardening.
4. Staging (GCP Cloud Run + Supabase) — waiting on `STAGING_DB_URL` from the user.

## Gotchas
- Node/Python on Windows don't resolve `*.localhost` (use a browser tab in Playwright, or curl).
- JDK HttpClient must use HTTP/1.1 to call Next (h2c upgrade is dropped).
- Public JSON must not carry HTTP cache headers (ISR is the cache).
- Tailwind v4: custom component CSS must live in `@layer components`, or it overrides utilities.
- Git Bash rewrites `/path` args for node: prefix `MSYS_NO_PATHCONV=1`.
- Sign-in is rate limited (in-memory): restart the API to clear during testing.
- Spotless rewraps Java; Maven `MAVEN_OPTS="-Xmx768m -XX:+UseSerialGC"`.
