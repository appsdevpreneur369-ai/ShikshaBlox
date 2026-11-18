# ShikshaBlox M01 — Progress & resume point

**Updated 2026-11-18 (evening before the Moriah demo on 2026-11-19).**

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
