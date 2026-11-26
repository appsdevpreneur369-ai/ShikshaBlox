# ShikshaBlox — Progress & resume point

**Updated 2026-11-26: website messaging changed (no release dates, no real schools), Greenfield Demo School,
and M03 Admissions CRM (small + medium depth) on staging.**

## M03 Admissions CRM, 2026-11-26
- Backend module `admissions`:
  - migration `V2026_11_26_0006`: `admission_lead`, `admission_activity`, `admission_seat`, all with RLS;
  - role `ADMISSIONS_COUNSELLOR` and permission `admissions:manage`;
  - catalogue module `ADMISSIONS_CRM`, plans `LITE` / `STANDARD` / `GLOBAL`.
- Website enquiries become leads automatically when the plan includes the CRM. Studio can also import existing enquiries.
- Leads can be walk-in, phone, WhatsApp, referral or campaign. Pipeline: New → Contacted → Visit booked → Visited →
  Applied → Offered → Admitted, or Lost (with a reason).
- Activities: call, visit, message, note. Next follow-up per lead, with an 8 am IST email digest to each counsellor.
- Also: CSV export (formula-safe), counsellor assignment, priority.
- Standard plan adds seats per class (blocks Admitted once a class is full) and the funnel (by stage and source,
  conversion %).
- Studio → Admissions has these tabs: Pipeline board, Follow-ups, Funnel and Seats. The lead panel offers
  call/WhatsApp/email links, stage moves, a log form and a timeline.
- Demo: Greenfield is on STANDARD, with 10 sample leads and seats. Sign in as `admin@greenfield.test` or
  `counsellor@greenfield.test` (seed password).
- Next in Front door & engagement: M03b online application form + documents, then M02 compliance calendar, M43 front office,
  M26 PTM/helpdesk, M13 communication hub, M12 parent portal.

## Website changes, 2026-11-26 (product owner decisions)
- No release quarters, roadmap page or "pre-book" labels anywhere on shikshablox.com. The e2e test fails if any
  appear. Internal build order stays in `modules.ts` (`release`) and PRICING-MATH.md.
- Moriah School is no longer shown. The showcase is **Greenfield Demo School** (fictional): tenant `greenfield`,
  seed `backend/src/main/resources/seed/greenfield` (Unsplash photos, credits in that folder), Cloud Run service
  `greenfield` (min 0). Screenshots are in `apps/www/public/product/demo-*`.
- Sales contacts are on the site: WhatsApp/phone +91 81216 06633, info@aurora-blossom.com.

## Product website (apps/www), 2026-11-26
- Static Next.js site for sales demos: 27 pages (home, features + 12 module pages, 4 solution pages, pricing,
  demo school, why us, security, about, contact, privacy, terms). Architecture: ADR 0008.
- (Superseded 2026-11-26: no availability labels or dates are published; see "Website changes" above.)
  Prices and roadmap estimates: `docs/PRICING-MATH.md` (founding offer changed to −25% for 25 schools; included
  WhatsApp credits removed; ERP plans are pre-booked and billed only at go-live).
- Demo form → `/api/demo-request` → public enquiry API as internal tenant `sb-sales` (seeded) → Studio inbox
  (sign in as `admin@sb-sales.test`).
- Images: free Unsplash photos, self-hosted, pre-generated WebP variants (`scripts/gen-images.py`); credits in
  `apps/www/IMAGE-CREDITS.md`. Product screenshots are of the Greenfield demo site.
- Tests: `e2e/tests/www.spec.ts` (7 device profiles: pages/H1/meta/canonical/footer/overflow, images, links,
  axe WCAG 2.1 AA, desktop + touch navigation, pricing, robots, form) via `playwright.www.config.ts`.
- Staging: Cloud Run `shikshablox-www` (min 0), built with `SB_WWW_NOINDEX=true`.
- Domain go-live checklist (shikshablox.com is not purchased yet):
  1. Buy shikshablox.com; in Cloud Run create domain mappings for `shikshablox.com` and `www.shikshablox.com`
     (or a global HTTPS load balancer with a serverless NEG), add the DNS records Google shows, wait for TLS.
  2. Redirect `www` → apex (load balancer URL map, or a tiny redirect service).
  3. Rebuild www **without** `SB_WWW_NOINDEX` and with `SB_WWW_SITE_URL=https://shikshablox.com`
     (cloudbuild `_WWW_URL`).
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
0. Product website: buy the domain (checklist above).
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
