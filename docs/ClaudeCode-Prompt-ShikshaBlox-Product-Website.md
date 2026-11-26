# Claude Code Prompt: ShikshaBlox.com Product Website (apps/www)

> **How to use:** open Claude Code in `D:\ShikshaBlox-Schools-ERP` and paste everything below the line.
> Before pasting, edit the three values in §0.3 (marked ✏️) if you have them. Anything you leave there gets a placeholder.

---

You are the lead engineer and a senior product designer (25 years of SaaS UI/UX) for **ShikshaBlox**, the multi-tenant School ERP built by **Aurora Blossom** (aurora-blossom.com). Build **shikshablox.com**, the product website I'll open on my laptop or phone when I walk into a school to sell. Then test it, merge it to `main`, deploy it to staging, and tell me when it's done.

## 0. Context. Read this before you write any code
1. `docs/ShikshaBlox-Schools-ERP-v2.md` is the product spec and your source for content. Focus on §1 Vision, §2 Competitive gaps, §3 School profiles, §5 Module catalogue, §6 Module specs, §7 to §10 profile packs, §11 Platform capabilities, §13 Workflows, §14 WhatsApp, §15 AI, §17 Security & privacy, §18 Offline, §22 Pricing, and §25 Differentiators.
2. `docs/PROGRESS.md`, `docs/adr/*`, `README.md`. These cover the current architecture: Spring Boot modular monolith, `apps/site` (multi-tenant school sites, host-based routing via `apps/site/src/proxy.ts`), `apps/admin` (Studio), and `packages/ui` / `blocks`.
3. Reference product: **ClinicFlow247.com**, our sister product with the same pattern (core SaaS plus the clinics it powers: Sindhu, Suhasini, WalkWell Physio). It scores 100 on SEO and meets Google page-speed standards. Study its live site, plus `D:\ClinicFlow\ClinicFlow247_Consolidated_Homepage_Redesign_Blueprint.md` and `D:\ClinicFlow\LANDING_PAGE_CONTENT_STRATEGY_RECOMMENDATION.md`, and copy the information architecture, conversion patterns, SEO plan and performance discipline. Don't copy its text or branding. Also review 3 or 4 leading school ERP competitor sites (see §2 and §28 of the spec) for patterns, and beat them on clarity and speed.

### 0.1 Positioning (non-negotiable)
- **ShikshaBlox is the core product.** Schools are tenants. Moriah School (Guntur) is just one school, and for them we've so far implemented only the public website (M01). This site sells the **whole platform**.
- Every page footer says: `© <year> Aurora Blossom (aurora-blossom.com). All rights reserved. ShikshaBlox is a School ERP Software product of Aurora Blossom.` Link to https://aurora-blossom.com.
- **Honesty rule:** today only **M01 Website & CMS** (plus enquiry inbox, notices/news/events/gallery, disclosures, publishing workflow) is live. Mark every other module from the spec **"Rolling out 2027"** or **"On the roadmap"** using a small, tasteful badge. Never claim customers, numbers, testimonials, awards or certifications we don't have. Use no fake logos and no invented stats. Stats are allowed only if they're product facts (e.g. "40+ modules", "3 school profiles", "14 ready-made website pages").

### 0.2 Decisions already made
- **"Schools on ShikshaBlox" showcase:** show Moriah School with its real logo (`backend/src/main/resources/seed/moriah/logo.jpg`), "Guntur, Andhra Pradesh", "School website · Live", linking to the Moriah staging site. Build it from a typed data file (`apps/www/src/data/schools.ts`) so I can add schools with one entry. Design it to look good with just one school (a featured case-study card, not a lonely logo strip).
- **Images:** use free-licence photos only (Unsplash, Pexels, Pixabay). Prefer Indian school, classroom, teacher, parent-and-phone and campus scenes. Download them, then optimise to AVIF/WebP at responsive sizes and **self-host** them in `apps/www/public/images`. Don't hotlink. Record each photo's source URL, author and licence in `apps/www/IMAGE-CREDITS.md`. Avoid close-ups of identifiable children's faces. For product visuals, use **real screenshots of our Studio and of the Moriah site** (capture them from staging with Playwright, see `e2e/studio-shots.mjs`), framed in clean laptop or phone mockups. Draw every icon as an inline SVG (Lucide-style).
- **Tech:** a new workspace app `apps/www`. It uses Next.js 16 App Router, TypeScript and Tailwind v4 (same versions as `apps/site`), is **fully static (SSG)** with zero runtime API calls, and makes minimal client JS (React Server Components by default, with client components only for the mobile menu, FAQ accordion, pricing toggle and demo form). It reuses `packages/ui` tokens where sensible but gets its own brand theme. It has no analytics or third-party scripts unless I ask for them later.
- **Cost:** staging must cost nothing extra. Deploy a new Cloud Run service `shikshablox-www` with min-instances 0 in the existing ShikshaBlox GCP project. Always pass `--project project-87cd233e-951b-43e4-886`. **Never** use the ClinicFlow project or ClinicFlow Supabase. Ask me before creating anything that bills.

### 0.3 Inputs from me ✏️ (use the placeholder and list it in your final report if blank)
- Sales WhatsApp / phone: `✏️ ____________`
- Sales email: `✏️ ____________`
- Show actual prices from spec §22.3 on the Pricing page? `✏️ yes / "starting from" only / no, "Talk to us"` (default: show them, labelled "Founding-school pricing, excl. GST")

## 1. Architecture (multi-tenant platform view)
Write **ADR 0008, Platform website & host map** and update `README.md`:

| Host | Serves |
|---|---|
| `shikshablox.com`, `www.shikshablox.com` | `apps/www` (product site) |
| `studio.shikshablox.com` | `apps/admin` (Studio, shared by all schools) |
| `api.shikshablox.com` | backend |
| `<school>.shikshablox.com` | `apps/site` (tenant site, Web Free) |
| school custom domain | `apps/site` (Web Pro, ADR 0004 verification) |

- Add `www`, `studio` and `app` to the reserved-subdomain list in `apps/site/src/lib/host.ts`, with a unit test.
- Add a short "How ShikshaBlox works" architecture diagram (inline SVG) to the website: one core platform → modules (LEGO blocks) → each school gets its own site, Studio, data isolation (RLS) and branding.
- Demo-request form: post to the **existing public enquiry API**, sent to an internal tenant `shikshablox` (seed it if missing), so leads land in our own Studio inbox. That's dogfooding the product. Keep the honeypot, consent checkbox, rate limit, and success/error states. Because the site is static, call the API from the client with CORS restricted to the www origins. Also offer a WhatsApp click-to-chat CTA.

## 2. Pages (IA)
`/` Home · `/features` (all modules, grouped as in spec §5) · `/features/<module>` for the main modules: website-cms, admissions-crm, student-information, fees-finance, attendance, timetable, exams-report-cards, parent-app-communication (WhatsApp-first), staff-hr-payroll, transport, analytics, AI · `/solutions/small-schools`, `/solutions/medium-schools`, `/solutions/international-schools`, `/solutions/government-trust` · `/pricing` · `/schools` (showcase + Moriah case study: what we built, before/after, link to live site) · `/why-shikshablox` (comparison with legacy ERPs, from spec §2 and §25) · `/security` (DPDP Act, child-data privacy, tenant isolation, backups, from §17) · `/about` (Aurora Blossom) · `/contact` (book a demo) · `/privacy` · `/terms` · custom 404.

**Home sections**, modelled on the ClinicFlow blueprint: hero (clear promise + "Book a free demo" + "See a live school site"; real product screenshot) → value strip → "LEGO blocks" composable modules visual → a day at school by role (Principal, Admin/Accounts, Teacher, Parent) → product showcase (tabs with real screenshots) → school profiles → Schools on ShikshaBlox (Moriah) → why us vs legacy ERPs → pricing teaser → FAQ → final CTA → footer.

## 3. Design bar
- Make it modern, calm and premium: generous whitespace, a strong type scale (one display font + one text font via `next/font`, self-hosted), and a confident brand palette (deep indigo/blue + warm accent, WCAG AA everywhere). Use rounded-2xl cards, soft shadows, subtle gradients drawn as images (not CSS that breaks contrast checks; see repo history), tasteful hover and scroll-reveal motion, and full `prefers-reduced-motion` support. Include dark-mode tokens.
- Make it mobile-first and flawless at 360, 390, 430, 768, 1024, 1280 and 1536 px. The mobile menu must show every link, scrolling must never lock, there must be no horizontal scroll, and **images are never cropped badly** (the lessons from the Moriah demo). Tap targets must be at least 44 px.
- Make it fully accessible: semantic landmarks, one H1 per page, skip link, focus rings, labelled form fields, alt text on every image.

## 4. SEO & performance targets (must hit before you call it done)
- Lighthouse **mobile**: SEO 100, Accessibility 100, Best Practices 100, Performance ≥ 95. LCP < 2.0 s, CLS < 0.05, INP < 200 ms.
- Unique title and meta description per page, canonical URLs, Open Graph + Twitter cards (a generated OG image per page), `sitemap.xml`, `robots.txt`, and JSON-LD: `Organization` (Aurora Blossom), `SoftwareApplication` (ShikshaBlox, with offers from pricing), `FAQPage`, `BreadcrumbList`. Use a logical heading hierarchy and descriptive internal links. Target keywords: "school ERP software", "school management software India", "school website builder", "school fees management software", "parent app for schools", "CBSE / ICSE / State board school ERP".
- Use `next/image` (or `<picture>` with AVIF/WebP), explicit dimensions, a preloaded LCP image, `font-display: swap`, and no render-blocking third parties. Keep the total JS per page small, and report the numbers.
- Set `metadataBase` from env so staging uses the Cloud Run URL and production uses https://shikshablox.com. **Staging must send `noindex`.** Production gets index.

## 5. Testing (senior QA standard)
1. `pnpm -r lint typecheck test` and `pnpm --filter @shikshablox/www build` must be green. Add Vitest tests for the schools data, the host reserved list and the demo-form validation.
2. Write a Playwright suite `e2e/tests/www.spec.ts` covering: every page returns 200 with one H1 and a title; nav and mobile menu work on all 7 device profiles (reuse the profiles in `e2e/site-audit.mjs`); no horizontal overflow; no broken internal links or images; demo form validation plus a successful submission lands in the `shikshablox` tenant inbox on staging; the footer shows the Aurora Blossom copyright on every page; and the Moriah showcase links to the live Moriah site.
3. Run `e2e/site-audit.mjs` against the www staging URL, and run an axe-core check on every page.
4. Run PageSpeed Insights (pagespeed.web.dev, mobile and desktop) on the staging URL for `/`, `/features`, `/pricing` and `/schools`. Fix issues until the §4 targets pass, and paste the scores in your report.
5. Take full-page screenshots at 390 px and 1440 px of every page. Look at them yourself and fix anything that doesn't look polished.

## 6. Git, deploy, report
- Branch `feat/www` from `main`. Make small, clear commits with the git author `ShikshaBlox Dev <dev@shikshablox.local>`. When everything in §5 is green, merge into `main` and `git push origin main` (https://github.com/appsdevpreneur369-ai/ShikshaBlox.git).
- Add `apps/www` to `infra/gcp/cloudbuild.yaml` and `infra/gcp/deploy-staging.sh` (service `shikshablox-www`, asia-south1, min 0 / max 2 instances, allow unauthenticated). Build with `gcloud builds submit --project project-87cd233e-951b-43e4-886 --region asia-south1 --config infra/gcp/cloudbuild.yaml --substitutions=_TAG=vN .`, then `TAG=vN bash infra/gcp/deploy-staging.sh` (Git Bash). Use the next tag after the last deployed one. Seed the `shikshablox` internal tenant on staging. Don't disturb the Moriah site, Studio or API beyond the CORS and reserved-subdomain changes, and re-verify the Moriah site still works after the deploy.
- Update `docs/PROGRESS.md`, plus a "domain go-live checklist" for when I point shikshablox.com DNS at it (Cloud Run domain mapping or a load balancer, www→apex redirect, flip noindex off).
- **Final report:** the www staging URL, the list of pages, the PageSpeed scores (mobile/desktop), test results (counts), screenshots folder path, image credits file, the placeholders still waiting on me (§0.3), and anything you couldn't finish. Only say "done" when everything above is actually verified.

Work autonomously through all of this. Ask me only if you're blocked on something that's genuinely my decision (e.g. spend, or publishing prices).
