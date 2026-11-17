# Claude Code Prompt — ShikshaBlox M01: School Website & CMS

> **How to use:** Put `ShikshaBlox-Schools-ERP-v2.md` into `D:\ShikshaBlox-Schools-ERP\docs\`, open Claude Code in `D:\ShikshaBlox-Schools-ERP`, and paste everything below the line.

---

You are the lead engineer for **ShikshaBlox**, a modular, multi-tenant School ERP ("LEGO-block" architecture) for Small, Medium and International schools. We are starting development with module **M01 — School Website & CMS**, the entry product (Release 1 in the roadmap).

## 0. Read first
1. Read `docs/ShikshaBlox-Schools-ERP-v2.md` in full. Pay special attention to:
   - §3 School Profiles, §4 Composable Product Model (modules, depth levels, entitlements, config hierarchy)
   - §6.1 M01 School Website & CMS (the feature table is the scope for this work)
   - §11 Cross-module platform capabilities, §16 Reference Architecture, §17 Security & Privacy, §20 NFRs
   - §22.3 Web plans (Web Free / Web Pro limits) and §24 Release R0/R1, §26 Website acceptance criteria
2. Then give me a short implementation plan with milestones, the folder structure and any questions. **Wait for my "go" before writing code.** After that, work milestone by milestone and stop for review at the end of each one.

## 1. Working directory & environment
- Project root: `D:\ShikshaBlox-Schools-ERP` (Windows). Use paths and scripts that work on Windows (PowerShell) and in Docker.
- Initialise a git repository; commit at the end of every milestone with a clear message.
- Everything must run locally with **one command** via Docker Compose (`docker compose up`), plus a `README.md` with setup steps.

## 2. Tech stack (from §16.4 of the spec)
| Layer | Choice |
|---|---|
| Public school websites | **Next.js (App Router) + TypeScript + Tailwind CSS**, SSR/ISR, multi-tenant by host name |
| Admin portal (CMS studio) | Next.js + TypeScript + Tailwind + a component library (shadcn/ui) |
| Backend | **Java 21 + Spring Boot 3**, **modular monolith** with Spring Modulith to enforce module boundaries |
| Database | **PostgreSQL 16** with Flyway migrations; tenant-aware access + Row-Level Security as defence in depth |
| Cache | Redis only where justified (e.g., rendered page cache invalidation) |
| Files/media | S3-compatible object storage (**MinIO** locally), private buckets, signed URLs, image resizing/optimisation |
| Auth | Spring Security + JWT (access + rotating refresh tokens); email/password and OTP login; TOTP MFA for privileged roles |
| API | REST with OpenAPI 3 docs (springdoc); generate a typed TS client for the frontends |
| Testing | JUnit 5 + Testcontainers (backend), Vitest/RTL (frontend), Playwright (E2E) |
| Quality | Checkstyle/Spotless, ESLint/Prettier, GitHub Actions CI workflow |

If you believe a different choice is clearly better for any layer, explain why in the plan before using it.

## 3. Suggested repository structure (adjust in the plan if needed)
```
D:\ShikshaBlox-Schools-ERP
├── docs/                      # spec, ADRs (architecture decision records), API notes
├── backend/                   # Spring Boot modular monolith
│   └── src/main/java/com/shikshablox/
│       ├── platform/          # tenancy, identity, entitlements, audit, files, notifications(stub), config
│       └── modules/website/   # M01: sites, pages, blocks, themes, menus, content types, enquiries, publishing
├── apps/
│   ├── site/                  # public school websites (multi-tenant Next.js)
│   └── admin/                 # admin portal / CMS studio
├── packages/
│   ├── ui/                    # shared design system components
│   └── api-client/            # generated TypeScript client
├── infra/                     # docker-compose, nginx/caddy, seed scripts
└── .github/workflows/
```
Use a pnpm workspace for the JS apps.

## 4. Platform foundations to build now (minimal but real)
These are shared by every future module, so build them properly, but only what M01 needs:
1. **Tenancy model:** `Organization → Campus` (single-campus by default). Every tenant-owned table carries `organization_id`. Server-side tenant resolution from **host name** (subdomain `school.shikshablox.app`-style for local dev, plus custom domains). Never trust a tenant id sent by the browser. RLS policies + automated **cross-tenant isolation tests**.
2. **Identity & roles:** users, roles, role scopes. Roles for M01: `PLATFORM_SUPER_ADMIN`, `SCHOOL_ADMIN`, `CONTENT_EDITOR`, `CONTENT_APPROVER`, `VIEWER`. MFA required for super admin.
3. **Entitlement & configuration engine (v1):** module catalogue table; per-tenant entitlements (module, features, depth `LITE|STANDARD|ADVANCED`, limits, expiry); server-side checks on every API call; config hierarchy `platform → profile → organization → campus`. Seed plans **Web Free** and **Web Pro** with the limits from §22.3 (pages, storage, custom domain, languages, branding badge). Show warnings at 80% of a limit, block gracefully at 100%.
4. **Audit log:** who/what/when/before-after for every create/update/publish/delete and permission change; immutable, queryable by admins.
5. **File/media service:** uploads with type/size validation, malware-scan hook (stub interface), image variants (thumbnail/medium/large, WebP), private storage + signed URLs; public media served via CDN-ready URLs.
6. **Consent registry (minimal):** record guardian consent for publishing identifiable student photos/videos; the gallery must enforce it (see §6.1). For M01, an admin can mark media as "contains identifiable students" and link a consent record or tick a school-level consent declaration — design the data model so the full Consent Ledger from the SIS can plug in later.
7. **Notifications (stub):** email sending interface (local: MailHog/Mailpit) for enquiry alerts and approval requests.

## 5. M01 scope — School Website & CMS (from §6.1)
Build these features for the **Web Free / Web Pro** plans:

**Site & theming**
- Onboarding wizard: school name, logo, colours, board, address, contact, social links → generates a ready site from a template.
- 3 responsive, accessible themes to start (e.g., *Classic*, *Modern*, *Playful/Primary*), with brand colours and fonts as design tokens.
- Navigation/menu builder (nested, drag-and-drop ordering).

**Page builder**
- Block-based pages (JSON block schema, versioned): hero, rich text, image, gallery strip, cards grid, CTA, stats, testimonials (school-provided only), FAQ accordion, embedded map, video embed, downloads list, notices feed, events feed, staff grid, enquiry form, custom HTML (sanitised, admin-only).
- Reusable blocks/sections across pages.
- Standard page templates pre-created: Home, About, Principal's Message, Vision & Mission, Academics, Admissions, Facilities, Faculty, Events, Gallery, News/Circulars, Contact, Careers, FAQs.

**Content types (auto-published to the site)**
- Notices/Circulars (with attachments, categories, pin, expiry date).
- Events (date/time, venue, category, calendar view, past/upcoming).
- Gallery albums (photos/videos, public/private visibility, consent check before publish).
- Staff directory (name, designation, qualifications, photo, subject, display order, show/hide fields).
- News/announcements, FAQs, Careers (job openings with apply form).

**Mandatory Public Disclosure**
- Dedicated disclosure section with configurable groups/fields per board (start with a **CBSE disclosure template** structure; fields must be editable, not hard-coded legal text), document uploads, "last reviewed" date and review reminders. Label clearly: content is the school's responsibility.

**Enquiry form → Admissions hand-off**
- Enquiry form with spam protection (honeypot + rate limit + optional hCaptcha/Turnstile), consent notice checkbox, configurable fields.
- **Use the future Admissions data contract** (student name, grade sought, academic year, guardian name/phone/email, source, UTM params, consent) so activating M03 later needs no redesign. Store enquiries in an `admissions_enquiry` table owned by an `admissions` package stub, emitted via a domain event `EnquiryReceived`.
- Enquiry inbox in admin: list, filter, status (new/contacted/closed), notes, CSV export, email alert to school.

**Publishing workflow**
- Draft → In review → Approved → Scheduled/Published → Archived. Preview links (signed, expiring). Scheduled publish/unpublish. Full version history with diff and **rollback**. Web Free: single-approver (self-publish); Web Pro: optional approval step (`CONTENT_APPROVER`).

**SEO & performance**
- Per-page meta title/description, Open Graph image, canonical URL, `noindex` toggle, auto `sitemap.xml`, `robots.txt`, 301 redirects manager, schema.org structured data (`School`/`EducationalOrganization`, `Event`).
- Target: Lighthouse mobile ≥ 90 for performance, accessibility, best practices, SEO on the default theme; LCP < 2.5 s. ISR with on-demand revalidation when content is published.
- Analytics: optional GA4/Plausible ID per site with a cookie-consent banner (privacy-first defaults).

**Multilingual**
- Content in multiple languages with per-language publishing status (Web Free: 1 language; Web Pro: 2 languages — enforced by entitlements). UI language switcher on the public site. Start with English + Hindi; the model must support any language (incl. RTL later).

**Accessibility**
- WCAG 2.1 AA: semantic headings, keyboard navigation, focus states, contrast checks in theme editor, required alt text on images, captions field for videos, accessible forms.

**Custom domain & SSL**
- Web Pro: add custom domain, show DNS instructions (CNAME/A), verification via DNS TXT token, status tracking. Locally, simulate with hosts-file entries; design production for on-demand TLS (e.g., Caddy on-demand TLS or Cloudflare for SaaS) and document it in an ADR.

**Data & ops**
- Site export (JSON + media zip) and import; daily backup strategy documented.
- Seed script that creates **3 demo schools** (small CBSE school, medium state-board school, international school) with realistic content, on separate subdomains.

**Out of scope now (leave clean seams only):** full Admissions CRM, SIS, fees, WhatsApp, AI writing assistant, multi-campus sites, programme microsites, billing/payments. Add TODO markers and interfaces, not implementations.

## 6. Non-functional & security requirements
- OWASP Top 10 protections; sanitise all rich text/HTML (allow-list); CSP headers on public sites; rate limits on public endpoints.
- Tenant isolation enforced in service layer **and** DB (RLS); tests that try cross-tenant reads/writes must fail.
- P95 API < 400 ms for common reads; public pages served from cache/ISR.
- Structured logging with tenant id and request id; health checks; `/actuator` secured.
- No real personal data in seeds. Store secrets in `.env` (with `.env.example`), never committed.

## 7. Milestones (propose refinements in your plan)
1. **M0 Scaffold:** monorepo, Docker Compose (Postgres, Redis, MinIO, Mailpit, backend, site, admin), CI, linting, README, ADRs for key decisions.
2. **M1 Foundations:** tenancy + host resolution, auth + roles + MFA, entitlements & plans, audit log, file service, RLS + isolation tests.
3. **M2 CMS core:** sites, themes/design tokens, menus, block-based pages, media library, page templates, onboarding wizard.
4. **M3 Content types:** notices, events, gallery (+consent), staff directory, news, FAQs, careers.
5. **M4 Disclosures & enquiries:** disclosure section with review reminders; enquiry form, inbox, email alert, `EnquiryReceived` event.
6. **M5 Publishing:** workflow, approvals, preview, scheduling, versions, rollback, ISR revalidation.
7. **M6 SEO, i18n, accessibility, performance:** meta, sitemap, redirects, structured data, multilingual, a11y fixes, Lighthouse targets.
8. **M7 Custom domains, export/import, seeds, hardening:** domain verification flow, export/import, demo schools, E2E tests, security review.

## 8. Definition of done (per milestone and for M01 overall)
- Code compiles, all tests pass in CI; meaningful unit + integration tests for every module; Playwright E2E for: onboarding wizard → publish home page → submit enquiry → see it in inbox.
- Tenant-isolation tests pass; permissions tested per role.
- Every write action audited.
- OpenAPI docs up to date; README and ADRs updated.
- Website acceptance criteria from §26 met: a school admin can create and publish a page without developer help; mobile performance, SEO, accessibility, domain and SSL checks pass; disclosure section is easy to find and has a review date; gallery publishing enforces consent.

## 9. Working style
- Keep modules decoupled: M01 code must not reach into other modules' tables; communicate through interfaces/events.
- Prefer configuration over hard-coding (board templates, themes, limits).
- Small, reviewable commits. At the end of each milestone, give me: what was built, how to run/test it, screenshots or URLs to check, and any open decisions.
- If something in the spec is ambiguous or conflicts, ask rather than guess.

Start now with step 0: read the spec and send me your plan and questions.
