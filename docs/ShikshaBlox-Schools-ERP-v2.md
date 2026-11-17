# ShikshaBlox — Composable School Operating Platform
## Product Requirements, Module Blueprint & System Architecture (v2.0)

> **One platform, three school profiles.** ShikshaBlox is a modular, "LEGO-block" School ERP that is configured — not customised — to fit a **Small School**, a **Medium School**, or an **International School**, and grows with the school without re-platforming.

---

## 0. Document Control

| Item | Detail |
|---|---|
| Document | ShikshaBlox Schools ERP — Requirement Analysis & Product Blueprint |
| Version | 2.0 (supersedes "ShikshaBlox-Schools-ERP-Updated") |
| Date | 16 November 2026 |
| Status | Draft for discovery & pilot validation |
| Audience | Founders, product, architecture, engineering, implementation, sales |

### 0.1 What changed in v2.0

| # | Change | Why |
|---|---|---|
| 1 | Re-organised the whole product around **three school profiles — Small, Medium, International** — plus a **Government / Trust overlay** and a **Multi-Campus Group overlay**. | v1 had two conflicting tier models (Gov/Private/International in §3 and seven packages in §17). v2 has one model. |
| 2 | Added a **Composable Product Model**: Module → Feature → **Depth level (Lite / Standard / Advanced)** → Limits, driven by an **Entitlement & Configuration Engine**. | Makes "modular" concrete and buildable; the same module can serve a 200-student school and a 3,000-student IB school. |
| 3 | Added a **Module × Profile availability matrix** and **feature-depth tables per profile**. | Sales, product and engineering can see exactly what each school type gets. |
| 4 | Added **16 new modules (M31–M46)**: Early Years, Certificates, Behaviour, Safeguarding, Trips, ECA/Day-care, University Counselling, International Programmes, Recruitment, Staff Growth, Facilities, IT Assets, Front Office, ID Cards, External Exam Entries, School Store. | Gaps found against real school operations, especially for pre-primary and international schools. |
| 5 | Added an **International School Pack** (IB / Cambridge / AP / US, multi-currency, foreign-national compliance, accreditation, transcripts, university applications). | v1 deferred international curricula without defining what they need. |
| 6 | Added **Small School Quick-Start** and **Medium School** operating packs. | Small schools fail with complex ERPs; they need a 1-day setup path. |
| 7 | Added **Role catalogue, Reports catalogue, Integrations & Hardware matrix, NFRs, Onboarding & Migration playbook, Upgrade/Downgrade rules, Glossary**. | Required for a complete requirements document. |
| 8 | Merged the original microservices diagram (§6 v1) with the architecture review (§13 v1) into one **evolution path**: modular monolith first, extract services later. | Removes contradiction between v1 §4/§6 and §8.1. |
| 9 | Replaced the original 30-week roadmap with a **profile-driven release plan** (Small-school MVP first). | Gets to revenue sooner with lower execution risk. |
| 10 | Added **competitor-benchmarked Pricing Plans** (§22): market comparison with Fedena, Entab CampusCare, Vidyalaya, Schoollog, Edisapp and others; Web / Lite / Standard / Global / Enterprise / Seva plans; add-ons; usage pass-through; discounts; billing rules; TCO comparison. | v1 had packaging but no prices. |

All substantive content from v1 has been retained, consolidated, or explicitly superseded.

---

## 1. Executive Summary & Product Vision

**ShikshaBlox** is a cloud-native, API-driven, AI-assisted **School Operating Platform** for schools from **under 200 to 5,000+ students**, single-campus or multi-campus, day or boarding, on national boards (CBSE, ICSE/ISC, State Boards) or international curricula (IB, Cambridge, AP/US).

It is built on a **"LEGO-Block" Composable Architecture**:

1. **Start small** — a school can begin with only a **Dynamic School Website** and enquiry form, live in a day.
2. **Snap on blocks** — Admissions, SIS, Attendance, Fees, Exams, Transport, HR, LMS and 40+ other blocks are activated through entitlements, not new installations.
3. **Fit the school, not the other way round** — each block has **depth levels** so a small school sees a simple screen and an international school sees criterion-based grading, multi-currency billing and university counselling — on the **same codebase and data model**.
4. **Pay only for active blocks** — transparent pricing by students, campuses and modules, with usage-based messaging and AI.

Competitors to benchmark (validate through demos and customer interviews, do not assume gaps): Vidyalaya, Schoollog, Entab CampusCare, Fedena, Edisapp; for international schools also ManageBac, Toddle, iSAMS, Veracross, PowerSchool, OpenApply, Engage.

**Positioning:** *"The school platform that grows from your first website to your fifth campus — without ever migrating."*

---

## 2. Competitive Gap Analysis

| Dimension | Typical legacy ERPs | ShikshaBlox |
|---|---|---|
| **Architecture** | Varies by vendor and edition; validate packaging and extensibility in demos. | **Composable modules** with explicit dependencies, entitlements, depth levels and a modular-monolith-first deployment. |
| **Fit across school sizes** | Usually built for one segment; small schools find them heavy, international schools find them shallow. | **Profile-driven configuration** (Small / Medium / International) on one platform; UI complexity scales with the profile. |
| **Web presence** | Separate or dated static templates. | **Headless CMS + Dynamic Website** auto-synced with admissions, events, gallery, notices, disclosures. |
| **Communication** | One-way SMS blasts; low parent-app adoption. | **WhatsApp-first, governed communication hub** with two-way inbox, consent, templates and SMS/email/push fallback. |
| **Board & curriculum** | Hard-coded report cards; custom scripting per board. | **Versioned Curriculum Packs** (CBSE incl. NEP 2020 HPC, ICSE/ISC, State Boards, IB, Cambridge, AP) configured by a declarative rule engine. |
| **International school needs** | Multi-currency, IB criteria, CAS, transcripts, university applications usually missing or bolted on. | **International School Pack** as a first-class module set. |
| **Intelligence** | Rule-based chatbots marketed as AI. | **Opt-in AI co-pilots** with permission-aware retrieval, citations, human approval and audit. |
| **User experience** | Cluttered tables, many clicks, separate portals per role. | **Role-adaptive unified workspace**, mobile-first PWA, scoped offline mode, regional languages. |
| **Onboarding** | Weeks of vendor-led setup; messy migration. | **Setup wizard + AI-assisted import**; Small school live in 1 day, Medium in 2–4 weeks, International in 4–8 weeks (targets to validate). |
| **Trust** | Privacy treated as an enterprise add-on. | **DPDP-aware by design**: consent ledger, audit, field masking, data portability — in every profile. |

---

## 3. School Profiles — Small, Medium, International

### 3.1 Profiles are presets, not separate products

A **School Profile** is a named preset of:
- enabled modules (entitlements),
- the **depth level** of each module (Lite / Standard / Advanced),
- default configuration (roles, workflows, grading packs, fee rules, notification templates, dashboards),
- limits (students, campuses, storage, messages, AI credits),
- service level (support hours, SLA, onboarding package).

A school is placed in a profile by the **Setup Wizard** using four axes. Any setting can be changed afterwards; the profile is only the starting point.

| Axis | Values | Effect |
|---|---|---|
| **Size** | < 600 / 600–2,500 / 2,500+ students (indicative) | Default modules, approval workflows, number of roles, analytics depth |
| **Curriculum** | National board(s) / International / Mixed (e.g., CBSE + Cambridge) | Curriculum packs, grading engine, report cards, transcripts |
| **Structure** | Single campus / Multi-campus / Group or franchise; Day / Boarding | Campus hierarchy, Command Centre, Hostel, central finance |
| **Ownership** | Private / Trust / Government / Aided | Fee engine vs. scholarship/grant mode, statutory registers, procurement rules |

### 3.2 Profile definitions

| | **Small School** | **Medium School** | **International School** |
|---|---|---|---|
| **Typical size** | Up to ~600 students (incl. pre-primary-only schools, play schools, new schools) | ~600–2,500 students | Typically 300–3,000 students; size is secondary to curriculum |
| **Campuses** | 1 | 1–3 | 1 or more; often part of a group |
| **Curriculum** | One board (CBSE / ICSE / State) | One or two boards; senior secondary streams | IB (PYP/MYP/DP/CP), Cambridge (Primary/Lower Secondary/IGCSE/AS & A Level), AP/US; often alongside a national board |
| **Admin team** | Principal + 1–3 office staff; no IT staff | Dedicated admissions, accounts, HR, transport, exam cell; part-time IT | Specialised teams: admissions/marketing, finance, HR, IT, counselling, learning support, safeguarding, boarding |
| **Fees** | Simple fee heads, cash + UPI, few instalments | Instalments, concessions, transport fees, payment gateway, accounting sync | Multi-currency, deposits, sponsor/corporate billing, financial aid, refunds, international payments |
| **Parents** | Mostly WhatsApp; regional language; low app adoption | WhatsApp + parent app/portal | Portal/app first + email; multilingual; expat & globally mobile families |
| **Biggest pains** | Manual registers, fee tracking, parent communication, compliance paperwork | Fee recovery, timetable/substitution, exams & report cards, transport, HR/payroll | Criterion-based assessment, programme requirements (CAS/EE/IA), transcripts, university applications, accreditation evidence, safeguarding, multi-currency |
| **Device reality** | Low-end Android, intermittent internet | Mixed; biometric devices common | Laptops/tablets, SSO with Google Workspace / Microsoft 365, 1:1 devices |
| **Setup target** | Self-serve, live in **1 day** | Assisted, **2–4 weeks** | Implementation project, **4–8 weeks** |
| **UI mode** | **Simple mode**: fewer menus, guided tasks, mobile-first admin | **Standard mode** | **Advanced mode**: full configuration, programme views |

### 3.3 Overlays (can be added to any profile)

| Overlay | Adds | Applies to |
|---|---|---|
| **Government / Trust / Aided** | Scholarship & grant mode instead of fee collection, Mid-Day Meal (PM POSHAN) logging, statutory registers, UDISE+ preparation, public disclosure, procurement with approvals, lottery-based admission (e.g., RTE seats) | Government, aided, trust and RTE-quota schools |
| **Multi-Campus Group** | Campus hierarchy, central policies with local overrides, consolidated finance & analytics, inter-campus transfers, shared staff | Medium and International groups, franchises |
| **Boarding** | Hostel, mess, outings/leave passes, house parents, boarding fees, night roll-call, weekend activities | Any boarding or semi-boarding school |
| **Early Years** | Daily diary, developmental milestones, observations, nap/meal/potty logs, pickup authorisation, photo journal | Pre-schools and K-2 sections |

### 3.4 Default activation by profile (summary)

Legend: **●** included in profile · **◐** included at Lite depth · **○** optional add-on · **—** not typical

| Capability area | Small | Medium | International |
|---|:-:|:-:|:-:|
| Website, enquiries, notices, gallery | ● | ● | ● |
| Admissions CRM | ◐ | ● | ● (Advanced, multi-stage, global applicants) |
| SIS & guardian 360 | ● | ● | ● |
| Attendance & leave | ● | ● | ● |
| Fees & receipts | ◐ | ● | ● (multi-currency) |
| Parent communication (WhatsApp/app) | ● | ● | ● |
| Homework & assignments | ◐ | ● | ● |
| Exams & report cards | ◐ | ● | ● (criteria / programme-based) |
| Timetable & substitutions | ○ | ● | ● |
| HR, staff attendance, payroll | ○ | ● | ● |
| Transport & GPS | ○ | ● | ● |
| Library, inventory, store | ○ | ◐ | ● |
| LMS / virtual classroom | ○ | ○ | ● |
| International programmes (CAS/EE/IA, transcripts) | — | ○ | ● |
| University & career counselling | — | ○ | ● |
| Safeguarding & pastoral | ◐ | ● | ● (Advanced) |
| Analytics & Command Centre | ◐ | ● | ● |
| AI co-pilots | ○ | ○ | ○ |
| SSO, APIs, integrations | — | ○ | ● |

The detailed module-level matrix is in **§5**.

---

## 4. Composable Product Model — How the "LEGO" Actually Works

### 4.1 Building blocks

```text
Platform
 └─ Module (e.g., M06 Fees)                      ← licensable unit, has dependencies
     └─ Feature (e.g., Instalments, Concessions)   ← can be toggled
         └─ Depth level: Lite | Standard | Advanced ← controls UI & rules complexity
             └─ Limits (e.g., 3 fee plans, 1 currency) ← enforced by entitlement engine
                 └─ Configuration (school's own rules) ← data, never code
```

| Depth | Meaning | Example (Fees) | Example (Exams) |
|---|---|---|---|
| **Lite** | Essential workflow, minimal setup, sensible defaults, simple UI | Fee heads, due dates, cash/UPI receipt, dues list | Marks entry, auto-total, printable report card from board template |
| **Standard** | Full operational workflow with approvals and automation | Instalments, late fees, concessions with approval, gateway, reconciliation, Tally/Zoho export | Exam calendar, components & weightage, moderation, HPC, hall tickets, seating |
| **Advanced** | Configurable rules, multi-entity, deep analytics, integrations | Multi-currency, deposits, sponsor billing, financial aid, multi-entity ledgers, refunds workflow | Criterion-based grading (IB MYP 1–8 per criterion), predicted grades, transcripts, board entry files |

### 4.2 Entitlement & Configuration Engine (core platform service)

**Responsibilities**
- Store, per tenant/campus: active modules, features, depth level, limits, trial/expiry dates.
- Evaluate entitlements on **every API call and UI render** (server-side; the UI only hides, the server enforces).
- Resolve configuration through a **hierarchy**: `Platform defaults → Profile preset → Organisation → Campus → Academic year → (Grade/Section where relevant)`.
- Version configuration per academic year so last year's report cards and fee rules remain reproducible.
- Expose a **Module Catalogue API** used by the Billing Console, Setup Wizard, and UI navigation.
- Emit events (`ModuleActivated`, `ModuleDeactivated`, `DepthChanged`, `LimitReached`) for billing, onboarding, and notifications.

**Rules**
- A module cannot be activated unless its **hard dependencies** are active (see §22.10); **soft dependencies** add features when present (e.g., Transport + Fees → automatic route billing).
- Changing depth **never deletes data**; higher-depth fields are hidden, preserved and restored on upgrade.
- Limits produce warnings at 80% and a graceful block at 100% (read access always remains).

### 4.3 Setup Wizard (self-serve onboarding)

1. School basics: name, logo, board(s), address, academic year, campuses.
2. Answer the four profile axes (§3.1) → **recommended profile** with explanation.
3. Pick modules (pre-ticked by profile; price shown live).
4. Load **starter configuration**: grades/sections, subjects from curriculum pack, fee heads template, roles, notification templates, holiday calendar for the state.
5. Import data (students, guardians, staff, fee balances) using the migration wizard.
6. Invite staff and guardians; verify WhatsApp number; connect payment gateway (optional).
7. Go-live checklist with completion score.

### 4.4 Upgrade, downgrade and module exit rules

| Event | Behaviour |
|---|---|
| **Upgrade profile / depth** | Immediate; new features use defaults; guided "what's new" tour for admins. |
| **Downgrade depth** | Data preserved read-only for advanced fields; workflows in progress must complete or be cancelled first. |
| **Deactivate module** | Module becomes read-only for a grace period (e.g., 90 days), then archived; full export available; dependants warned before deactivation. |
| **Academic year roll-over** | Configuration cloned to new year; promotions, fee plans and timetables carried forward with review. |
| **Customer exit** | Complete export (CSV/JSON + documents + PDFs) within a defined SLA; deletion certificate after the agreed retention period. |

### 4.5 UI complexity modes

- **Simple mode** (Small default): task-first home ("Take attendance", "Collect fee", "Send notice"), max ~8 menu items, mobile-first admin, wizards over forms.
- **Standard mode** (Medium default): full menus per role, bulk actions, approvals inbox.
- **Advanced mode** (International default): programme views, configuration studio, custom fields, report builder.
- Users can switch mode if permitted; the mode changes presentation only, never permissions.

---

## 5. Master Module Catalogue & Profile Matrix

Each module is independently licensable, has declared dependencies, and supports depth levels. Module IDs M01–M30 are retained from v1; M31–M46 are new in v2.

Legend: **●** included · **◐** included at Lite depth · **○** optional add-on · **—** not typical for the profile · **A** Advanced depth

### 5.1 Front door & engagement

| ID | Module | Key capabilities | Small | Medium | Intl |
|---|---|---|:-:|:-:|:-:|
| M01 | School Website & CMS | Themes, custom domain, SEO, pages, notices, events, gallery, staff directory, forms, multilingual, accessibility | ● | ● | ● A |
| M02 | Mandatory Disclosure & Compliance | Board/state disclosure pages, compliance calendar, evidence vault, UDISE+ preparation, export packs, accreditation evidence (Intl) | ◐ | ● | ● A |
| M03 | Enquiry & Admissions CRM | Leads, campaigns, follow-ups, applications, documents, entrance tests, waitlists, offers, conversion | ◐ | ● | ● A |
| M12 | Parent & Student Portal / App | Announcements, attendance, timetable, homework, marks, fee ledger, leave, consent, documents, requests | ● | ● | ● |
| M13 | WhatsApp & Communication Hub | Templates, opt-in, delivery status, two-way inbox, SMS/email/push fallback, quiet hours, languages, circular acknowledgements | ● | ● | ● |
| M25 | Alumni & Community | Alumni directory, events, mentoring, donations, verified communications | — | ○ | ● |
| M26 | Surveys, PTM & Helpdesk | PTM slots, feedback, surveys, complaints, service requests, SLA tracking | ◐ | ● | ● |
| M43 | Front Office & Reception *(new)* | Walk-in register, call log, appointments, postal dispatch/receipt, lost & found, visitor hand-off | ○ | ● | ● |

### 5.2 Student & academic core

| ID | Module | Key capabilities | Small | Medium | Intl |
|---|---|---|:-:|:-:|:-:|
| M04 | Student Information System | Student 360, guardians, siblings, previous school, documents, lifecycle, promotion, withdrawal, alumni hand-off | ● | ● | ● A |
| M07 | Attendance & Leave | Daily / period-wise, late, early exit, leave applications, corrections, QR/RFID/biometric options | ● | ● | ● |
| M08 | Timetable & Resource Planner | Constraints, rooms/labs, substitutions, versions, clash detection, rotating/blocks schedules (Intl) | ○ | ● | ● A |
| M09 | Curriculum & Lesson Planning | Curriculum maps, syllabus coverage, lesson plans, outcomes, resources, observations, unit planners (Intl) | ○ | ● | ● A |
| M10 | Homework & Assignments | Publishing, attachments, due dates, submissions, rubrics, feedback, late policy | ◐ | ● | ● |
| M11 | Exams & Assessment | Exam calendar, question bank, blueprint, hall tickets, seating, marks, moderation, gradebook, report cards | ◐ | ● | ● A |
| M31 | Early Years & Pre-Primary *(new)* | Daily diary, milestones, observations, meal/nap/potty logs, photo journal, NEP Foundational Stage / EYFS-style frameworks | ○ | ○ | ○ |
| M32 | Certificates & Document Generator *(new)* | TC, bonafide, character, migration, fee & study certificates, custom templates, QR verification, e-sign | ◐ | ● | ● |
| M38 | International Programmes *(new)* | IB PYP/MYP/DP/CP, Cambridge, AP: criteria grading, CAS, EE, IA, ToK, Personal Project, Exhibition, predicted grades, transcripts | — | ○ | ● |
| M45 | External Exam Entries *(new)* | Board registration data prep (e.g., CBSE Class 9/11 registration & LOC), Cambridge/IB/AP entries, candidate numbers, exam fees, results import | ○ | ● | ● |

### 5.3 Student welfare & safety

| ID | Module | Key capabilities | Small | Medium | Intl |
|---|---|---|:-:|:-:|:-:|
| M15 | Gate, Visitor & Student Safety | Visitor pre-registration, QR/OTP passes, pickup authorisation, custody notes, incidents, emergency muster | ◐ | ● | ● |
| M17 | Health Room / Infirmary | Nurse records, visit log, parent notification, medication workflow where lawful, immunisation records, health checks | ○ | ● | ● |
| M24 | Counselling, Inclusion & Student Support | Support plans (IEP), accommodations, EAL, referrals, interventions, restricted notes, reviews | ○ | ○ | ● |
| M33 | Behaviour & Pastoral Care *(new)* | Merits/demerits, house points link, incidents, detentions, restorative actions, pastoral notes, parent meetings | ◐ | ● | ● |
| M34 | Safeguarding & Child Protection *(new)* | Concern logging, Designated Safeguarding Lead workflow, POCSO committee records, restricted access, referrals, chronology | ◐ | ● | ● A |

### 5.4 Finance & administration

| ID | Module | Key capabilities | Small | Medium | Intl |
|---|---|---|:-:|:-:|:-:|
| M06 | Fees, Billing & Finance | Fee structures, instalments, concessions, scholarships, invoices, receipts, refunds, dues, reconciliation, exports | ◐ | ● | ● A |
| M21 | Procurement & Inventory | Vendors, requisitions, POs, GRN, stock, asset register, maintenance schedules | ○ | ◐ | ● |
| M22 | Accounting & Payroll Integration | Payroll inputs, payslips, PF/ESI/PT/TDS configuration, Form 16 data, accounting exports | ○ | ● | ● |
| M46 | School Store & POS *(new)* | Uniforms, books, stationery, kits, sizes/variants, online pre-orders, POS, stock, parent wallet link | ○ | ○ | ● |

### 5.5 People (staff)

| ID | Module | Key capabilities | Small | Medium | Intl |
|---|---|---|:-:|:-:|:-:|
| M05 | Staff & HR | Profiles, qualifications, contracts, onboarding, leave, attendance, substitutions | ◐ | ● | ● |
| M39 | Recruitment & Onboarding *(new)* | Vacancies, careers page (M01), applications, interviews, offers, background/police verification checklist, document collection, expat staff visas (Intl) | — | ○ | ● |
| M40 | Staff Growth & Appraisal *(new)* | Goals, classroom observations, CPD hours, training catalogue, appraisal cycles, certifications expiry | — | ○ | ● |

### 5.6 Campus operations

| ID | Module | Key capabilities | Small | Medium | Intl |
|---|---|---|:-:|:-:|:-:|
| M14 | Transport & Fleet | Routes, stops, vehicles, drivers, attendants, GPS, allocation, boarding events, safety alerts, route billing | ○ | ● | ● |
| M16 | Meals, Canteen & Nutrition | Menu, served/consumed, allergy flags, cashless wallet/POS, vendor/stock; PM POSHAN logging (Gov overlay) | ○ | ○ | ● |
| M18 | Library & Learning Resources | Catalogue, issue/return, reservations, fines, barcode/RFID, e-library links | ○ | ● | ● |
| M19 | Activities, Sports & Houses | Clubs, house points, fixtures, registrations, achievements, certificates | ◐ | ● | ● |
| M20 | Hostel & Boarding | Rooms, beds, roll-calls, visitor logs, outings/leave, mess plans, house-parent communication | ○ | ○ | ○ |
| M35 | Trips, Excursions & Permissions *(new)* | Trip planning, consent slips, risk assessments, payments, medical info pack, headcounts, emergency contacts offline | ○ | ● | ● |
| M36 | After-School, ECA & Day-Care *(new)* | Activity catalogue, capacity, booking windows, waitlists, attendance, billing, external providers, day-care hours | ○ | ○ | ● |
| M41 | Facilities, Maintenance & Bookings *(new)* | Maintenance tickets, preventive schedules, room/hall/ground booking, safety inspections (fire, lifts, water) | ○ | ○ | ● |
| M42 | IT Assets & Devices *(new)* | Device register, student/staff device loans, BYOD registration, MDM integration, IT helpdesk | — | ○ | ● |
| M44 | Digital ID Cards *(new)* | ID card design & print batches, QR/RFID/NFC cards, lost-card reissue, staff/visitor/student cards | ○ | ● | ● |

### 5.7 Learning, intelligence & enterprise

| ID | Module | Key capabilities | Small | Medium | Intl |
|---|---|---|:-:|:-:|:-:|
| M23 | LMS & Virtual Classroom | Course spaces, live-class links, recordings, quizzes, resources, progress, LTI/OneRoster interoperability | ○ | ○ | ● |
| M27 | Analytics & Command Centre | Admissions funnel, attendance, fees, academics, operations, campus comparisons, report builder | ◐ | ● | ● A |
| M28 | AI Co-Pilots | Drafting, summaries, search, anomaly detection, lesson support, report insights, NL analytics | ○ | ○ | ○ |
| M29 | Integrations & Developer Platform | APIs, webhooks, SSO, payment, biometric, accounting, video, messaging, LMS connectors | — | ○ | ● |
| M30 | Multi-Campus & Group Control | Campus hierarchy, central policies, local overrides, consolidated reporting, transfers | — | ○ | ○ |
| M37 | University & Career Counselling *(new)* | Career interest profiles, university shortlists, application tracker, deadlines, transcripts, recommendation letters, test scores, outcomes | — | ○ | ● |

---

## 6. Module Specifications with Feature Depth by Profile

This section specifies the most important modules. For each, **S / M / I** shows the depth delivered to Small / Medium / International profiles by default.

### 6.1 M01 — School Website & CMS (entry product)

| Feature | S | M | I |
|---|:-:|:-:|:-:|
| Themes, custom domain, SSL, page builder, navigation, reusable blocks | ● | ● | ● |
| Standard pages: Home, About, Principal's Message, Vision/Mission, Academics, Admissions, Facilities, Faculty, Events, Gallery, News/Circulars, Contact, Careers, FAQs | ● | ● | ● |
| Auto-publish notices, events, results highlights from ERP | ● | ● | ● |
| Board-specific Mandatory Public Disclosure section with evidence documents | ● | ● | ● |
| Enquiry forms with spam protection, consent notice, CRM hand-off | ● | ● | ● |
| SEO (meta, sitemap, canonical, redirects, structured data), analytics consent | ● | ● | ● |
| Draft / preview / schedule / publish, approval, rollback, history | ◐ | ● | ● |
| Multilingual content with per-language publishing status | ◐ (2 languages) | ● | ● |
| Programme microsites (IB / Cambridge pages), virtual tour, admissions landing pages per campaign | — | ◐ | ● |
| Multi-campus sites under one group brand | — | ○ | ● |
| Consent check before publishing identifiable student photos/videos | ● | ● | ● |
| CDN image optimisation and video hosting (no large originals from app server) | ● | ● | ● |

**Upgrade path:** the enquiry form already uses the Admissions data contract, so activating M03 later needs no redesign.

### 6.2 M03 — Enquiry & Admissions CRM

| Feature | S | M | I |
|---|:-:|:-:|:-:|
| Lead capture: website, walk-in, phone, WhatsApp, social campaigns | ● | ● | ● |
| Enquiry pipeline with stages and follow-up reminders | ● | ● | ● |
| Online application form with document upload and application fee | ◐ | ● | ● |
| Document checklist and OCR-assisted verification (human-confirmed) | — | ● | ● |
| Entrance test / interaction scheduling, scoring, panel notes | — | ● | ● |
| Merit list / lottery generation (incl. RTE-quota lottery for Gov/Trust overlay) | ○ | ● | ○ |
| Offers, waitlists, seat matrix by grade/section, acceptance deposits | — | ● | ● |
| Counsellor assignment, campaign source ROI, conversion analytics | — | ● | ● |
| Multi-stage international admissions: previous-school reports, teacher recommendations, language assessment, student visit/virtual interview, sibling & staff-child priority | — | — | ● |
| International applicants: passport, visa, nationality, time zones, currency of deposit | — | — | ● |
| Rolling admissions across the year with mid-year joining fee pro-rating | ◐ | ● | ● |

### 6.3 M04 — Student Information System

| Feature | S | M | I |
|---|:-:|:-:|:-:|
| Student 360: demographics, guardians, siblings, emergency contacts, medical alerts, documents | ● | ● | ● |
| Lifecycle states: applicant → enrolled → promoted → withdrawn → alumni | ● | ● | ● |
| Guardian 360: multiple guardians, custody/pickup restrictions, communication preferences | ● | ● | ● |
| Bulk promotion and section reshuffle at year-end | ● | ● | ● |
| APAAR ID tracking with consent/exception workflow; UDISE+ data preparation | ● | ● | ● |
| Custom fields per school | ◐ (10) | ● | ● |
| Houses, streams, electives, subject choices with rules | ◐ | ● | ● |
| Nationality, passport, visa/residence permit expiry, foreign-national reporting data (see §7.6) | — | ○ | ● |
| Multiple languages spoken at home, EAL status, previous curricula and grade-equivalence mapping | — | ○ | ● |
| Inter-campus transfer with history | — | ○ | ● |

### 6.4 M06 — Fees, Billing & Finance

| Feature | S | M | I |
|---|:-:|:-:|:-:|
| Fee heads (tuition, admission, transport, lab, activity), fee plans per grade | ● | ● | ● |
| Cash / cheque / UPI QR collection with instant e-receipt (WhatsApp/PDF) | ● | ● | ● |
| Dues list, defaulter list, WhatsApp reminders with UPI payment link | ● | ● | ● |
| Instalments (monthly/quarterly/term), late-fee rules, grace periods | ◐ | ● | ● |
| Concessions, sibling/staff discounts, scholarships with approval workflow | ◐ | ● | ● |
| Payment gateway (Razorpay/PayU/Cashfree etc.), auto-reconciliation with settlements | ○ | ● | ● |
| Split settlement to multiple accounts (e.g., tuition vs. transport entity) | — | ● | ● |
| Tally / Zoho Books export, day book, collection reports, cashier closing | ◐ | ● | ● |
| Refunds and credit notes with approval | — | ● | ● |
| Fee defaulter workflow: reminders → call task → hold on report card/TC (configurable, policy-compliant) | — | ● | ● |
| GST handling for taxable items (e.g., store, some services) — configuration validated by the school's CA | — | ● | ● |
| **Multi-currency** invoices and payments, exchange-rate snapshot, international card/wire payments | — | — | ● |
| Deposits (security / acceptance), debentures/capital fees where applicable, forfeiture rules | — | ○ | ● |
| Sponsor / corporate billing (employer pays all or part), split-payer invoices | — | — | ● |
| Financial aid applications with means assessment and committee approval | — | ○ | ● |
| Multi-entity ledgers (trust vs. company), budget vs. actual | — | ○ | ● |
| Grant / scholarship disbursement mode (Gov/Trust overlay) | ○ | ○ | — |

### 6.5 M07 — Attendance & Leave

| Feature | S | M | I |
|---|:-:|:-:|:-:|
| Daily class attendance on mobile in < 60 seconds per class, offline capable | ● | ● | ● |
| Absent/late WhatsApp alert to guardians, per school policy | ● | ● | ● |
| Leave application by parent, approval by class teacher | ● | ● | ● |
| Period-wise / subject attendance | ○ | ● | ● |
| QR / RFID / biometric capture (gate, bus, class) | — | ● | ● |
| Attendance correction with approval and audit | ◐ | ● | ● |
| Monthly registers in board/state format, attendance % for eligibility | ● | ● | ● |
| Boarding roll-calls (morning, night, weekend) | — | ○ | ○ |
| Absence reason codes, chronic-absence alerts to pastoral team | — | ● | ● |

### 6.6 M08 — Timetable & Resource Planner

| Feature | S | M | I |
|---|:-:|:-:|:-:|
| Manual timetable grid with clash detection | ○ | ● | ● |
| Auto-generator with constraints (teacher load, labs, consecutive periods, preferences) | — | ● | ● |
| Daily substitution suggestions when a teacher is on leave (e.g., published by 7:30 AM) | — | ● | ● |
| Rotating / cycle-day schedules (e.g., 6-day or 10-day cycles), block scheduling, option blocks | — | — | ● |
| Electives & DP subject option blocks (HL/SL) | — | ○ | ● |
| Room & resource booking integration (M41) | — | ○ | ● |

### 6.7 M09–M11 — Curriculum, Homework, Exams & Report Cards

| Feature | S | M | I |
|---|:-:|:-:|:-:|
| Curriculum Pack for school's board (subjects, grade scales, report card layout) | ● | ● | ● |
| Marks entry with validation, auto-totals, grade conversion | ● | ● | ● |
| Report cards from board templates (CBSE incl. NEP 2020 HPC, ICSE/ISC, State) | ◐ | ● | ● |
| Homework publishing with attachments and parent alerts | ● | ● | ● |
| Submissions, rubrics, feedback, late policy | — | ● | ● |
| Exam calendar, components, weightages, pass/promotion rules | ◐ | ● | ● |
| Hall tickets, seating plans, invigilation duties | — | ● | ● |
| Question bank, paper blueprints, OMR/online mock tests | — | ● | ● |
| Moderation and marks-publication approval | — | ● | ● |
| Lesson plans, syllabus completion meter, classroom observations | — | ● | ● |
| Co-scholastic, competency and skill observations | ◐ | ● | ● |
| Unit planners (IB/Cambridge style), ATL skills, learner profile | — | — | ● |
| Criterion-based / standards-based grading, best-fit judgement, grade boundaries | — | — | ● |
| Predicted grades, transcripts (cumulative GPA where needed), course credits | — | ○ | ● |
| Student portfolios with evidence (files, media, reflections) | — | ○ | ● |

### 6.8 M12 / M13 — Parent Portal & Communication Hub

| Feature | S | M | I |
|---|:-:|:-:|:-:|
| WhatsApp broadcast with approved templates, opt-in register, delivery status | ● | ● | ● |
| WhatsApp self-service bot: "Fees", "Report Card", "Bus Location", "Apply Leave" | ◐ | ● | ● |
| Two-way shared inbox with assignment and escalation | — | ● | ● |
| Parent PWA/portal: one view for all children | ● | ● | ● |
| Native/white-label parent app | ○ | ○ | ● |
| Circulars with read receipts and acknowledgement/consent capture | ● | ● | ● |
| Email channel with branded templates (primary for many international parents) | ◐ | ● | ● |
| Multilingual templates (regional languages; for Intl — e.g., English plus families' home languages) | ● | ● | ● |
| Quiet hours, budget caps, duplicate suppression, fallback SMS/email/push | ● | ● | ● |
| Digital notice boards / signage feed | — | ○ | ○ |

### 6.9 M05 / M22 — Staff, HR & Payroll

| Feature | S | M | I |
|---|:-:|:-:|:-:|
| Staff profiles, documents, qualifications | ● | ● | ● |
| Staff attendance (mobile + geofence), leave with balances | ◐ | ● | ● |
| Biometric device integration | — | ● | ● |
| Payroll inputs, salary structures, payslips | ○ | ● | ● |
| Statutory: PF, ESI, Professional Tax, TDS, Form 16 data — jurisdiction-validated | — | ● | ● |
| Substitution scheduler (link to M08) | — | ● | ● |
| Expat staff: visas/permits expiry, relocation, housing, allowances, multi-currency pay components | — | — | ● |
| Recruitment (M39), appraisal & CPD (M40) | — | ○ | ● |
| Staff safeguarding training & police verification tracking | ◐ | ● | ● |

### 6.10 M14 — Transport & Fleet

| Feature | S | M | I |
|---|:-:|:-:|:-:|
| Routes, stops, vehicles, drivers, attendants, student allocation | ○ | ● | ● |
| Attendant app: boarding/deboarding (offline), parent alerts | ○ | ● | ● |
| Live GPS link for parents during trip windows | — | ● | ● |
| RFID boarding with exceptions (wrong bus, not boarded) | — | ○ | ● |
| Over-speed / route deviation / delay alerts | — | ● | ● |
| Route optimisation, capacity planning | — | ○ | ● |
| Vehicle compliance: fitness, permit, insurance, PUC, driver licence & police verification expiries | ○ | ● | ● |
| Route billing integrated with M06 | ○ | ● | ● |

### 6.11 M27 — Analytics & Command Centre

| Feature | S | M | I |
|---|:-:|:-:|:-:|
| Principal home: today's attendance, fee collection, pending approvals, notices | ● | ● | ● |
| Standard report library (see §21) | ◐ | ● | ● |
| Drill-down from metric to authorised records, with definitions and freshness | ● | ● | ● |
| Custom report builder, scheduled reports by email | — | ● | ● |
| Academic analytics: subject/teacher/cohort trends, value-added | — | ● | ● |
| Campus comparison & group KPIs (with M30) | — | ○ | ● |
| Programme analytics: IB/Cambridge grade distributions vs. predicted, university outcomes | — | — | ● |
| Data warehouse export / BI connector | — | ○ | ● |

### 6.12 New modules — brief specifications

**M31 Early Years & Pre-Primary** — daily diary per child (arrival, meals, nap, toileting, mood, activities), developmental milestones aligned to the school's chosen framework (e.g., NEP 2020 Foundational Stage / NCF-FS, or EYFS-style frameworks for international schools), observation notes with photos (consent-aware), learning-story style reports, pickup authorisation with photo of authorised adult, daily parent feed.

**M32 Certificates & Document Generator** — template designer with merge fields, numbering series, issue register, approval, QR verification page, e-signature (Aadhaar e-Sign or DSC — integration to validate), re-issue with reason; TC generation enforces fee/library/no-dues clearances as configured.

**M33 Behaviour & Pastoral Care** — positive and negative behaviour logging, configurable categories and points, escalation ladders, detentions, restorative conversations, tutor/pastoral notes, parent meeting records; positive-first design; no automated punitive decisions.

**M34 Safeguarding & Child Protection** — confidential concern logging by any staff, routed to the Designated Safeguarding Lead / POCSO committee; chronology per child, actions, referrals to external agencies, restricted visibility (named users only), break-glass access with audit; staff training and acknowledgement tracking; never visible to parents or general staff.

**M35 Trips, Excursions & Permissions** — trip proposal → approval → risk assessment → consent slip (digital signature) → payment → medical/emergency pack (available offline) → headcount check-ins → incident log; overseas trips capture passport/visa details (Intl).

**M36 After-School, ECA & Day-Care** — activity catalogue with terms, capacity, eligibility, booking windows with lottery/first-come, waitlists, external provider portal, attendance, billing via M06, day-care hourly/monthly billing.

**M37 University & Career Counselling** — career interest surveys, counsellor meetings, university shortlist (safety/match/reach), application tracker by deadline, document requests (transcript, counsellor and teacher recommendations), test scores (SAT/ACT/IELTS/TOEFL etc.), decisions and outcomes reporting; export formats for common application platforms where supported (integration to validate).

**M38 International Programmes** — see §7.

**M39 Recruitment & Onboarding** — vacancy approval, careers page, applicant tracking, interview scheduling and scorecards, offer letters, pre-joining checklist (police verification, references, medical, safeguarding declaration), onboarding tasks; for Intl, overseas recruitment and work-permit tracking.

**M40 Staff Growth & Appraisal** — goal setting, observation forms, peer reviews, CPD hours and certificates, training catalogue, appraisal cycles with sign-off, certification expiry alerts (first aid, safeguarding, IB workshop categories).

**M41 Facilities, Maintenance & Bookings** — ticketing with SLA, preventive maintenance calendar, statutory inspections (fire NOC, lifts, water testing, electrical), room/hall/ground booking with approvals, vendor work orders (link M21).

**M42 IT Assets & Devices** — asset register, assignment to staff/students, loaner devices, BYOD registration and acceptable-use acknowledgement, MDM integration (e.g., Google Admin, Intune, Jamf — to validate), IT helpdesk.

**M43 Front Office & Reception** — walk-in and call registers, appointment booking with staff, courier/postal inward/outward, lost & found with photos, quick hand-off to Visitor (M15) and Admissions (M03).

**M44 Digital ID Cards** — card templates per role, batch printing files for vendors or in-house printers, QR/RFID/NFC encoding, lost-card blocking and reissue fee, digital ID in the app.

**M45 External Exam Entries** — candidate data preparation and validation for board registration (e.g., CBSE registration / List of Candidates — via official formats, not portal scraping), Cambridge/IB/AP entries with subject options and fees, access arrangements, candidate numbers, timetable, results import and analysis.

**M46 School Store & POS** — catalogue (uniform sizes, books by grade, kits), pre-orders with online payment, POS counter, stock/reorder levels, vendor purchase link (M21), returns/exchanges.

---

## 7. International School Pack (deep dive)

International schools buy on curriculum fit first. Treat each programme as a **versioned Curriculum Pack** validated with real IB/Cambridge coordinators before claiming support. Nothing here implies endorsement or certification by the IB, Cambridge or College Board unless a formal partnership exists.

### 7.1 Programme support matrix

| Programme | Must-have capabilities |
|---|---|
| **IB PYP** | Programme of Inquiry, units of inquiry, transdisciplinary themes, learner profile, ATL skills, portfolios, Exhibition tracking, narrative reports |
| **IB MYP** | Unit planners, criteria A–D per subject (0–8 scale), best-fit final grades (1–7 via boundaries), Personal Project / Community Project, service as action, global contexts |
| **IB DP** | Subject groups with HL/SL, CAS (experiences, reflections, learning outcomes, supervisor sign-off), Extended Essay (supervisor meetings, RPPF-style reflections, deadlines), Theory of Knowledge, Internal Assessment deadlines and sample tracking, predicted grades, core points, diploma eligibility checks |
| **IB CP** | Career-related study, reflective project, language development, service learning |
| **Cambridge Primary / Lower Secondary** | Stage-based frameworks, checkpoint-style progress tracking |
| **Cambridge IGCSE / O Level / AS & A Level** | Syllabus codes, components, tiers (core/extended), coursework tracking, predicted grades, entries and results analysis |
| **AP / US-style high school** | Courses with credits, weighted/unweighted GPA, transcripts, AP course and exam tracking, graduation requirements |
| **Mixed schools** | CBSE/ICSE in some grades and IB/Cambridge in others; grade-equivalence mapping for transfers |

### 7.2 Assessment engine requirements (international)
- Multiple **grading models** coexisting: percentages, letter grades, criteria with level descriptors, standards-based (1–4), IB 1–7, Cambridge A*–G / 9–1.
- **Best-fit** and **most-recent/most-consistent** judgement support, not just averaging.
- **Grade boundaries** configurable per subject/session.
- **Predicted grades** workflow with history, rationale and approval.
- **Narrative comments** with comment banks, length limits and AI-assisted drafting (teacher approval).
- **Transcripts** (cumulative, credit-based) and **school profile** documents for university applications.

### 7.3 International finance
- Multi-currency fee schedules and receipts; invoicing in INR with indicative foreign-currency values or in foreign currency where legally permissible (validate with the school's CA and banking partner).
- International cards and wire transfers, payment plans, sponsor/corporate billing, refundable deposits.
- Financial aid and scholarship committees.

### 7.4 Families and communication
- Families from many nationalities: multilingual templates, translation workflow with human review, time-zone-aware messaging for parents abroad.
- Email-first, app second, WhatsApp optional (consent-based).
- Parent-teacher-student conferences (three-way) with booking.

### 7.5 Accreditation & quality evidence
- Evidence library mapped to accreditation frameworks used by the school (e.g., IB programme evaluation, CIS, NEASC, Cambridge school quality) — framework structures entered by the school, not shipped as proprietary content.
- Self-study workflows: standards → evidence → owner → status → review date.

### 7.6 Foreign-national students and staff (India)
- Capture passport, visa/residence permit type and expiry, arrival date, and local address with role-restricted access.
- Expiry alerts and a **reporting workflow for foreign-national students and staff** as required by current Indian immigration law and the FRRO/e-FRRO system (for example, the institution-reporting form for foreign students). Requirements must be verified with counsel before release; the ERP prepares data, the school submits.
- Equivalent data handling for international schools outside India via configurable fields.

### 7.7 International integrations (to validate)
- SSO with Google Workspace / Microsoft 365; rostering via OneRoster; LMS interoperability via LTI 1.3.
- Optional sync with ManageBac / Toddle where schools keep a separate curriculum platform during transition.
- Exam boards: import/export using official file formats only.
- University application platforms: transcript and document export where APIs/formats exist.

---

## 8. Small School Pack — "Live in a Day"

**Design goal:** a principal with one office assistant can run the school on a phone.

| Requirement | Detail |
|---|---|
| Self-serve signup | Free trial, Setup Wizard, sample data, guided tasks |
| Starter configuration | State holiday list, board subjects, standard fee heads, roles (Principal, Office, Teacher, Accountant, Parent) |
| Mobile-first admin | Take attendance, collect fee (cash/UPI), print/share receipt, send notice — all from phone |
| WhatsApp-native | Notices, absence alerts, fee reminders with UPI link, report card PDF on WhatsApp |
| Printable outputs | Attendance registers, fee receipts, report cards, TC, ID cards — A4 and thermal printer formats |
| Excel in, Excel out | Import from Excel templates; export any list |
| Language | English + one regional language UI and templates |
| Support | WhatsApp & phone support, video tutorials in regional languages |
| Pricing | Low base fee, per-student pricing with minimum, no per-user fees |
| Growth path | Turn on Exams Standard, Transport, HR or Medium profile with one click; no migration |

**Explicitly hidden in Simple mode:** approval chains (single approver), custom fields beyond 10, multi-campus settings, API keys, advanced analytics.

---

## 9. Medium School Pack — "Run the whole school"

| Requirement | Detail |
|---|---|
| Departmental roles | Admissions, accounts, exam cell, transport, HR, front office, librarians |
| Approvals | Concessions, refunds, leave, marks publication, purchase requests, record corrections |
| Fee recovery | Instalments, gateway, auto reminders, defaulter workflow, reconciliation, Tally/Zoho |
| Academics | Timetable + substitutions, lesson plans, exams with moderation, HPC, hall tickets |
| Operations | Transport with GPS, visitor management, infirmary, library, inventory |
| Staff | Biometric attendance, payroll with statutory deductions |
| Analytics | Principal and management dashboards, scheduled reports |
| Parent app | PWA or white-label app add-on, two-way WhatsApp inbox |
| Onboarding | Assisted implementation in 2–4 weeks with data migration and training |

---

## 10. Government / Trust / Aided Overlay

| Requirement | Detail |
|---|---|
| Digital identity | Website with mandatory disclosures and public notices |
| Admissions | Online forms, lottery for reserved seats (e.g., RTE 25% quota where applicable), transparent merit lists |
| Mid-Day Meal / PM POSHAN | Daily meal count, menu, stock received/used, photo evidence, report exports in required formats (verify state formats) |
| Attendance | Simple mobile attendance, offline-first |
| Scholarships | Eligibility, applications, disbursement tracking (no fee collection required) |
| Registers | Admission/withdrawal register, attendance register, stock register, cash book — in state formats |
| Reporting | UDISE+ data preparation, state-department exports |
| Procurement | Multi-level approvals, quotations comparison, audit trail |
| Commercial model | Per-school annual fee, tender/district deployments, or CSR-sponsored deployments |

---

## 11. Cross-Module Platform Capabilities (non-optional foundations)

- **Identity, access & consent:** authentication (OTP, password, SSO), MFA for privileged users, role/attribute-based authorisation, guardian relationships, consent receipts, delegated access, account recovery.
- **Entitlement & configuration engine:** see §4.2.
- **Document & records vault:** versioned files, categories, expiry alerts, access logs, retention and legal hold.
- **Workflow engine:** configurable approvals (admissions, concessions, refunds, leave, purchases, marks publication, pickup, corrections) with SLA and escalation.
- **Notification orchestration:** channel selection, templates, retries, deduplication, delivery receipts, quiet hours, escalation, cost controls.
- **Audit & activity timeline:** who viewed/changed what, when, from which workflow, with approvals.
- **Import/export & migration:** mapping, validation, duplicate detection, preview, error report, rollback, reconciliation.
- **Search & global navigation:** role-aware search across authorised records.
- **Localisation:** English + configurable Indian and international languages, date/number formats, time zone, currency, translations.
- **Academic calendar:** terms, holidays, events, working-day calculation used by attendance, fees and timetable.
- **Custom fields & forms:** per entity, with validation, visibility rules and reporting support.
- **Print & template service:** report cards, receipts, certificates, ID cards, registers — HTML/PDF templates with versioning.
- **Payments service:** gateway abstraction, webhooks, reconciliation, refunds.
- **Billing & subscription service:** plans, add-ons, usage metering (messages, AI, storage), invoices for schools (see §22).

---

## 12. Role Catalogue by Profile

| Role | Small | Medium | Intl | Notes |
|---|:-:|:-:|:-:|---|
| Platform Super Admin (ShikshaBlox) | ● | ● | ● | Internal; break-glass access with audit |
| Group Admin / Trustee | — | ○ | ● | With Multi-Campus overlay |
| Principal / Head of School | ● | ● | ● | |
| Vice Principal / Section Head / Programme Coordinator (PYP/MYP/DP) | — | ● | ● | |
| Office Admin / Front Office | ● | ● | ● | |
| Admissions Officer / Counsellor | ◐ | ● | ● | |
| Accountant / Bursar / Cashier | ● | ● | ● | |
| HR Manager / Payroll | — | ● | ● | |
| Class Teacher / Homeroom Teacher | ● | ● | ● | |
| Subject Teacher | ● | ● | ● | |
| Exam Cell / Exams Officer | — | ● | ● | |
| Transport Manager / Bus Attendant / Driver | ○ | ● | ● | |
| Security / Gatekeeper | ○ | ● | ● | |
| Nurse / Health Officer | ○ | ● | ● | Restricted health access |
| Counsellor / Learning Support / EAL | — | ○ | ● | Restricted notes |
| Designated Safeguarding Lead | ◐ | ● | ● | Restricted safeguarding access |
| Librarian / Store Keeper / Hostel Warden | ○ | ● | ● | |
| University Counsellor | — | ○ | ● | |
| IT Admin | — | ○ | ● | |
| Parent / Guardian | ● | ● | ● | |
| Student | ◐ | ● | ● | Age-appropriate access |
| External provider (ECA coach, vendor) | — | ○ | ● | Scoped, time-bound |
| Auditor / Inspector (read-only) | ○ | ○ | ● | Time-bound, audited |

Permissions combine **role** + **scope** (campus, grade, section, subject, relationship) + **sensitivity** (normal, restricted, highly restricted).

---

## 13. High-Value Workflows

### 13.1 Student lifecycle
Enquiry → application → document verification → entrance/interaction → offer/waitlist → admission confirmation → class/section allocation → fee plan → guardian onboarding → daily operations → promotion → TC/withdrawal → alumni. Every transition is auditable, permission-controlled, and reversible only through an approved correction workflow.

### 13.2 Parent experience
One unified view of all linked children and campuses, with clear separation per child. A parent can view today's attendance, timetable, homework, circulars and calendar; pay fees and download receipts; apply for leave; book PTMs; raise helpdesk requests; view approved results and report cards; see bus status (if enabled); receive meal updates (if recorded); sign consent slips; manage notification preferences and consent.

### 13.3 Teacher daily workflow
Today's timetable and substitutions → attendance with reasons (offline capable) → lesson plan and syllabus progress → homework and review queue → marks entry with validation → behaviour/pastoral notes → parent communication requests.

### 13.4 Principal / management workflow
Leadership dashboard answers: unexplained attendance drops, overdue instalments and trends, subjects needing intervention, best admissions sources, routes with repeated exceptions, compliance items due, parent issues beyond SLA. Every metric drills down to authorised records, with definition, date range and freshness.

### 13.5 Safety & incident workflow
Incident reporting for injury, illness, bullying, safeguarding concerns, transport events, unauthorised pickup attempts and emergencies. Restricted access, minimum necessary detail, designated escalation roles, evidence handling and immutable audit. Sensitive notes never appear in routine parent broadcasts.

### 13.6 Additional workflows (new)
- **Early years day:** arrival check-in → diary updates (meal/nap/activity) → photo moments (consent-aware) → pickup with authorised adult verification → daily summary to parent.
- **Fee defaulter:** due date → reminder sequence (WhatsApp/SMS/email) → call task to accounts → meeting → payment plan or concession request → policy-based holds (configurable, legally reviewed).
- **Trip:** proposal → approval → risk assessment → consent & payment → medical pack → departure headcount → return headcount → incident closure.
- **External exam entry:** subject choices → eligibility check → entry file → fee collection → candidate numbers → timetable → results import → analysis.
- **Staff onboarding:** offer → document collection → police verification & safeguarding declaration → account creation → training assignments → probation review.
- **Year-end roll-over:** close marks → publish results → promotion/detention decisions (human) → section reshuffle → new fee plans → timetable clone → archive previous year.

---

## 14. WhatsApp-First Communication — a Governed Channel

WhatsApp is a differentiator but never the system of record or the only route to essential information.

### 14.1 Notification catalogue

| Event | Example |
|---|---|
| Attendance | Present / absent / late alert, per school policy |
| Fees | Due reminder, payment link, receipt, failed-payment follow-up |
| Homework | Subject, title, due date, secure portal link |
| Exams | Schedule, hall-ticket availability, approved result publication |
| Monthly progress | Approved summary with attendance, assessment trends, comments |
| Holidays | Holiday / unexpected closure / reopening |
| Events | Details, permission request, RSVP, schedule change |
| Transport | Bus approaching, boarding/deboarding, delay, route exception |
| Meals | Menu and meal served/consumed where recorded |
| Health room | Notification per approved policy |
| PTM | Booking confirmation, reminder, reschedule |
| Documents | TC / certificate / report card ready for secure download |
| Trips & ECA | Consent request, payment due, departure/return confirmation |
| Emergency | Urgent closure, evacuation, transport incident instructions |

### 14.2 Messaging controls
- Verifiable opt-in/preference records (number, purpose, timestamp, source, language, withdrawal).
- Approved templates for business-initiated messages; design for current Meta policy and pricing.
- Template versions, approval state, language, category, fallback text.
- Never send marks, health details, identity documents or sensitive child information in message bodies — short alert + authenticated link.
- SMS (DLT-registered templates in India), email, push and in-app fallback for essential notices.
- Per-school budgets, throttling, retries, quiet hours, delivery reports, duplicate suppression.
- Shared inbox with assignment, escalation, audit; obvious bot-to-human hand-off.
- Never assume every parent has WhatsApp, a smartphone or data access.

---

## 15. AI Strategy — Useful, Explainable, Safe

AI is an opt-in layer (M28) on top of reliable data, priced by credits (see §22).

| AI capability | User value | Required control | Profiles |
|---|---|---|---|
| Website content assistant | Draft notices, events, admissions FAQs | Staff approval before publishing | S M I |
| Admissions assistant | Classify enquiries, suggest follow-ups, answer approved FAQs | No autonomous admission decisions | M I |
| Teacher assistant | Lesson plans, worksheets, question variants, rubrics, unit planners | Teacher review; curriculum-pack grounding | S M I |
| Report comment assistant | Draft narrative comments from evidence | Teacher edits and approves | M I |
| Assessment assistant | Paper blueprints, class trend analysis | Human moderation; no unsupervised marks | M I |
| Parent communication assistant | Multilingual drafts, conversation summaries | Approval for sensitive messages | S M I |
| Principal analytics assistant | Natural-language questions over authorised aggregates | Permission-aware, cited data, audit | M I |
| At-risk indicators | Flag attendance/performance changes for review | Explainable signals; human review; no labels or punitive action | M I |
| Document extraction | Read admission documents, suggest field values | Confidence score, human verification, no silent overwrite | M I |
| Support assistant | Product/school policy FAQs | Show source, escalate uncertainty | S M I |

**Guardrails**
- No training of shared models on student data by default.
- Minimise and redact data sent to model providers; prefer providers/regions meeting the school's data-residency needs.
- Enforce tenant and role permissions before retrieval and before response.
- AI never decides admission, promotion, discipline, scholarships or eligibility.
- No opaque ranking, emotion inference, facial recognition, or stigmatising predictive labels.
- Log model, prompt version, sources, user, output and approvals.
- Disable controls per school and module; non-AI path for every critical task.

---

## 16. Reference Architecture — Practical Evolution Path

### 16.1 Principle: modular monolith first, services later

v1 described every block as an independent microservice. That stays the **long-term target**, not the starting point.

- Start with a **modular monolith** (or a few coarse-grained services) with strict domain boundaries, explicit contracts, separate test suites.
- Modules remain independently **enableable** via the Entitlement Engine even when they share a deployment.
- Extract a module into its own service only when scale, team ownership, security isolation, release cadence or availability justify it. Likely first candidates: Notification/WhatsApp, GPS/telemetry ingestion, Website rendering, AI services.
- Asynchronous events for cross-module workflows (transactional outbox); no network call for every internal function.
- One PostgreSQL cluster with schema-per-domain ownership in early releases; no database-per-service.

### 16.2 Logical layers

```text
PUBLIC EXPERIENCE
  School websites | Admissions microsites | Public notices | Disclosure pages | SEO
                         |
USER EXPERIENCE (role-adaptive: Simple / Standard / Advanced modes)
  Admin web | Teacher web/PWA | Parent & student PWA / white-label app
  Bus attendant app | Gate console | Nurse console | Kiosk
                         |
EDGE & ACCESS
  CDN/WAF | API gateway | Tenant/domain resolver | SSO/MFA
  RBAC + ABAC policies | Rate limits | Consent enforcement | Entitlement check
                         |
APPLICATION DOMAINS (modular boundaries)
  Website/CMS | Admissions | SIS | Academics & Assessment | Intl Programmes
  Attendance | Timetable | Fees/Finance | HR/Payroll | Parent Hub
  Transport | Campus Safety | Welfare & Safeguarding | LMS | Library
  Inventory & Store | Facilities | Compliance | Analytics | AI
                         |
PLATFORM SERVICES
  Entitlement & configuration | Workflow | Notification | Document & print
  Search | Audit | Import/export | Payments | Subscription billing & metering
  Integration adapters | Calendar | Custom fields
                         |
EVENT & DATA
  PostgreSQL | Redis | Object storage | Search index
  Event broker (when justified) | Time-series store for GPS | Analytics warehouse
                         |
OPERATIONS & TRUST
  Observability | Backups | DR | Security monitoring
  Retention | Data-subject requests | Audit evidence | Status page
```

### 16.3 Target-state view (from v1, retained as long-term direction)

```text
+------------------------------------------------------------------------------+
| CLIENTS: Next.js websites | React admin | Flutter/PWA apps | WhatsApp bot     |
+------------------------------------------------------------------------------+
| API GATEWAY: rate limiting | tenant resolver | RBAC/JWT | entitlements        |
+------------------------------------------------------------------------------+
| DOMAIN SERVICES ("LEGO BLOCKS"): CMS | Admissions | SIS | Exams | Fees | HR  |
| Transport & Gate | LMS & Library | Intl Programmes | Welfare | Analytics       |
+------------------------------------------------------------------------------+
| EVENT BUS (Kafka/RabbitMQ when justified) -> notifications, AI, analytics    |
+------------------------------------------------------------------------------+
| DATA: PostgreSQL (+RLS) | Redis | S3-compatible storage | TimescaleDB (GPS)  |
+------------------------------------------------------------------------------+
```

### 16.4 Suggested implementation stack

| Layer | Choice | Notes |
|---|---|---|
| Public websites & admin portal | Next.js + TypeScript + React + Tailwind | SSR/ISR for SEO and speed |
| Backend | Java 21 + Spring Boot modular monolith; Spring Modulith for boundaries | Match team skills; Node/NestJS is a valid alternative |
| Database | PostgreSQL with tenant-aware access; RLS as defence in depth | Managed service, PITR |
| Cache / short-lived state | Redis only where needed | |
| Files | S3-compatible private buckets, signed URLs, malware scanning, lifecycle | CDN for public media |
| Background jobs | Transactional outbox + managed queue; Kafka later | |
| Search | PostgreSQL full-text first | Dedicated engine when needed |
| GPS / telemetry | TimescaleDB or managed time-series when transport scales | |
| AI | Separate Python service only where useful; LLM via provider with regional options | Business rules stay in core |
| Mobile | PWA first; Flutter for attendant/teacher apps needing offline & devices; white-label parent app | |
| Deployment | Managed containers, managed Postgres, secrets manager, CDN/WAF, IaC | India region primary; other regions for international customers if required |

Choose the smallest stack the team can operate well.

### 16.5 Multi-tenancy and data boundaries
- Model **platform → organisation/group → campus → academic year → grade/section → student** explicitly.
- Users may access one campus, selected campuses, or the whole group.
- Every tenant-owned record carries the tenant boundary; enforced in service authorisation and database access; RLS as additional guardrail with automated cross-tenant tests.
- Public website content separate from private student/staff data.
- Dedicated database/deployment for large enterprise or data-residency customers only when justified (priced as add-on).
- Never trust a tenant ID supplied by the browser.

### 16.6 Core domain model (starter, expanded)

`Organization`, `Campus`, `AcademicYear`, `Term`, `Grade`, `Section`, `Subject`, `CurriculumPack`, `Programme`, `Course`, `Student`, `Guardian`, `GuardianStudentRelationship`, `Staff`, `Enrollment`, `AttendanceRecord`, `LeaveRequest`, `TimetableSlot`, `Substitution`, `Assessment`, `AssessmentComponent`, `Criterion`, `GradeScale`, `MarkEntry`, `PredictedGrade`, `ReportCard`, `Transcript`, `CasActivity`, `ExtendedEssay`, `ExternalExamEntry`, `FeePlan`, `FeeInvoice`, `Payment`, `Receipt`, `Refund`, `Concession`, `Deposit`, `Sponsor`, `Currency`, `TransportRoute`, `RouteStop`, `Vehicle`, `Trip`, `BoardingEvent`, `Visitor`, `PickupAuthorization`, `Incident`, `SafeguardingConcern`, `BehaviourEvent`, `HealthVisit`, `SupportPlan`, `Notice`, `Assignment`, `Submission`, `Activity`, `Booking`, `SchoolTrip`, `Certificate`, `Document`, `ConsentRecord`, `WorkflowInstance`, `Notification`, `AuditEvent`, `ModuleEntitlement`, `Subscription`, `UsageMeter`.

Use stable internal IDs; never use phone, Aadhaar, APAAR ID, passport or admission number as primary keys.

---

## 17. Security, Privacy, Reliability & Governance

Launch requirements in **every** profile — not an enterprise add-on.

### 17.1 Privacy and child data
- Map each field to purpose, lawful basis, owner, retention, permitted roles.
- Verifiable parental consent and guardian verification designed for the DPDP Act and Rules (with legal review of school vs. platform roles — school is typically the Data Fiduciary, ShikshaBlox the Data Processor).
- Consent ledger: notice version, purpose, guardian identity/verification method, timestamp, channel, status, withdrawal, downstream propagation.
- Access/correction/erasure workflows subject to legal retention.
- Minimise collection; Aadhaar or other IDs only with a documented lawful need.
- Health, safeguarding and counselling records separated from ordinary profiles.
- International schools: support GDPR-style requests for EU families and data-residency options where contracted.

### 17.2 Security baseline
- MFA for super-admins and privileged roles; secure sessions; refresh-token rotation.
- Least-privilege RBAC + ABAC (campus, class, relationship, sensitivity).
- TLS in transit, encryption at rest, managed keys and secrets.
- Secure hashing, brute-force protection, rate limits, OWASP protections, dependency and container scanning.
- Audit sensitive reads and all high-impact changes (exports, permission changes, fee adjustments).
- Malware scanning, private storage, expiring links.
- Annual VAPT, incident response drills, patch SLAs; target ISO 27001 / SOC 2 readiness for International & Enterprise sales.

### 17.3 Reliability targets by plan (to validate)

| Target | Lite (Small) | Standard (Medium) | Global (International) / Enterprise |
|---|---|---|---|
| Monthly availability (core portals) | 99.5% | 99.5% | 99.9% once the platform can support it |
| RPO | 24 h | 12 h | ≤ 1 h (PITR) |
| RTO | 8 h | 8 h | ≤ 4 h |
| Support hours | Mon–Sat business hours | Mon–Sat extended | Priority, extended + emergency line |
| Status page & incident comms | ● | ● | ● with named contact |

Graceful degradation: if WhatsApp, payment gateway, GPS or AI provider is down, attendance/SIS continue.

---

## 18. Offline-First and Low-Connectivity Design

Offline is scoped by workflow — the whole ERP does not work offline.

**Offline candidates:** teacher roster and attendance; bus attendant boarding; event/meal counts; trip headcounts and emergency packs; draft lesson notes; gate visitor log (queued).

**Sync requirements:** encrypted local storage; last-sync time and pending count; idempotent APIs with client operation IDs; per-entity conflict rules (attendance corrections need review); server validation with visible, recoverable rejections; remote logout/device revocation; no highly sensitive records cached on shared devices.

---

## 19. Integrations & Hardware Matrix

| Category | Examples (to validate) | Small | Medium | Intl |
|---|---|:-:|:-:|:-:|
| Payment gateways | Razorpay, PayU, Cashfree, Easebuzz; international cards/wires | ○ | ● | ● |
| UPI QR | Static/dynamic QR on receipts and notices | ● | ● | ● |
| WhatsApp BSP | Meta Cloud API direct, or Gupshup / Interakt / others | ● | ● | ● |
| SMS (DLT) & email | DLT-registered SMS providers; SES/SendGrid-type email | ● | ● | ● |
| Accounting | Tally (export/connector), Zoho Books | ◐ | ● | ● |
| Biometric / face devices (staff) | eSSL, ZKTeco, Realtime and similar | — | ● | ● |
| RFID / QR | Gate readers, bus readers, library tags | — | ○ | ● |
| GPS | Vehicle trackers via vendor APIs; phone-based GPS for attendants | — | ● | ● |
| Video classes | Google Meet, Zoom, Microsoft Teams | — | ○ | ● |
| SSO & productivity | Google Workspace, Microsoft 365 | — | ○ | ● |
| LMS / rostering standards | LTI 1.3, OneRoster, xAPI | — | ○ | ● |
| Curriculum platforms | ManageBac, Toddle (sync during transition) | — | — | ○ |
| E-sign | Aadhaar e-Sign / DSC providers | — | ○ | ○ |
| Printers | A4, thermal receipt printers, ID card printers | ● | ● | ● |
| MDM | Google Admin, Microsoft Intune, Jamf | — | — | ○ |
| Webhooks & public API | Event subscriptions, REST/GraphQL | — | ○ | ● |

---

## 20. Non-Functional Requirements

| Area | Requirement |
|---|---|
| Performance | P95 API < 400 ms for common reads; attendance save < 1 s on 4G; website LCP < 2.5 s on mobile |
| Scale (design target) | 5,000+ tenants, 2M+ students platform-wide; single tenant up to 50,000 students across campuses |
| Peak loads | Result publication, fee due dates and morning attendance (7:30–9:30 AM) load-tested at 5× normal |
| Devices | Android 9+ low-end phones (2 GB RAM), latest 2 versions of Chrome, Safari, Edge, Firefox |
| Accessibility | WCAG 2.1 AA for portals and websites |
| Localisation | UI and templates in English + Hindi at launch; Telugu, Tamil, Kannada, Marathi, Bengali, Gujarati etc. by demand; RTL-ready for Gulf international schools |
| Data portability | Full export in CSV/JSON + documents within 7 days of request |
| Observability | Per-tenant metrics, tracing, error budgets, audit-log retention ≥ 7 years for financial records (configurable) |
| Maintainability | Module test coverage gates, contract tests between modules, feature flags for every release |

---

## 21. Reports & Analytics Catalogue (standard library)

| Area | Reports |
|---|---|
| Admissions | Enquiry funnel, source ROI, counsellor performance, seat matrix, waitlist, conversion by grade |
| Students | Strength by grade/section/gender/category, new joins/withdrawals, sibling map, birthday list, document pending |
| Attendance | Daily summary, chronic absentees, late arrivals, class-wise %, monthly register, eligibility (% for exams) |
| Fees | Collection by day/head/mode, outstanding ageing, defaulters, concessions granted, refunds, reconciliation exceptions, forecast |
| Academics | Mark sheets, subject toppers, grade distribution, teacher-wise results, syllabus coverage, homework completion |
| International | Criteria achievement, predicted vs. actual grades, CAS completion, EE status, IA deadlines, university destinations |
| Staff | Attendance, leave balances, substitutions taken, payroll register, statutory reports, training compliance |
| Transport | Route utilisation, boarding exceptions, delays, over-speed events, vehicle compliance expiries |
| Safety & welfare | Visitor log, incidents by type, health-room visits, behaviour trends (aggregate, role-restricted) |
| Communication | Messages sent/delivered/failed, opt-outs, cost per channel, circular acknowledgement rates |
| Compliance | UDISE+ readiness, disclosure freshness, expiring documents, registers |
| Group | Campus comparison across all of the above |

---

## 22. Commercial Model & Pricing Plans

### 22.1 Market benchmark — legacy and current School ERPs

Most established Indian School ERPs **do not publish prices**. The figures below come from public listings and third-party estimates as of November 2026. They are **indicative only** and must be confirmed through vendor quotes or mystery-shopper demos before pricing goes live.

| Vendor | Public pricing signal | Pricing model | What it tells us |
|---|---|---|---|
| **Fedena** | Standard listed at **US$999/year** (21 core + 17 standard modules, unlimited users); **Pro Plus US$1,299/year**; Enterprise on quote. Third-party estimate ₹18K–₹1.2L/year; free if self-hosted (open-source edition) | Per institution, per feature tier | Flat institution pricing with unlimited users is the market's low-friction benchmark |
| **Entab CampusCare** | **No public pricing** (contact vendor, annual subscription). Third-party estimate ₹25K–₹4L/year: Lite ₹25K–₹90K (200–800 students); Enterprise ₹3L–₹12L (2,500+ students) | Quote-based, annual | Premium, sales-led; mobile apps and integrations often priced separately (per competitor claims — verify) |
| **Vidyalaya School Software** | **Sales-led**, no self-serve tiers published. Third-party estimate ₹30K–₹3L/year; Standard ₹75K–₹3L (800–2,500 students); multi-branch ₹3L–₹12L | Quote-based | Strong in mid/large and Gulf schools; multi-branch carries a premium |
| **VidyalayaERP** *(different product)* | Listed **from ₹7,500/year** (Basic) on a marketplace; three plans | Flat tiers | Shows the low end of the market |
| **Schoollog** | **Sales-led**; pricing page routes to a contact form. A marketplace lists a plan at ₹2,331 (unit unclear) | Quote-based / flat | No transparency — an opening for ShikshaBlox |
| **Edisapp** | Directory listings show **from US$1/user/month**; not published on G2 | Per user | Per-user pricing gets expensive when parents count as users |
| Budget newcomers (for reference) | e.g., TACHY from ₹49/student/year; JPRAXC ₹14,999/year + ₹39–59/student/year; Teachmint free basic tier + paid plans | Per student + base | Price floor is very low; we must win on value, not price alone |
| International platforms | **ManageBac**: quote-based annual subscription by enrolment (Essentials / Pro / Max). **Toddle**: per-user licence covering students and staff, modules on/off | Per student / per user | International schools are used to paying in USD for curriculum platforms *on top of* an SIS |

**Market price bands (third-party estimate, per student per year):** budget ₹50–₹120 · standard ₹120–₹300 · premium (apps, biometrics, GPS) ₹300–₹600.

**Hidden costs schools commonly face (third-party estimate):** SMS/WhatsApp ₹0.15–₹0.50 per message; branded parent app ₹20K–₹80K; data migration ₹40K–₹2L; staff training ₹25K–₹1L; custom report-card format ₹15K–₹40K each; add-on modules such as transport (~₹15K), library (~₹10K), hostel (~₹25K) and HR (~₹30K) per year.

**What this means for ShikshaBlox**
1. **Transparency wins.** Three of the five named legacy vendors hide prices. ShikshaBlox publishes prices and has a live calculator.
2. **No per-user fees.** Unlimited staff, parents and students. That beats per-user models and matches Fedena's unlimited-user promise.
3. **Bundle what others charge extra for:** parent PWA, onboarding, one report-card format, data import and website are included.
4. **Price in the band for each profile while delivering more depth:** Small in the budget band, Medium at the low end of the standard band, International in the premium band (well below global SIS + LMS stacks).
5. **Make usage costs visible.** Pass messaging through at cost plus a small fee, and charge 0% platform fee on online payments.

### 22.2 Pricing principles
- **Base fee + per-student rate**, with students included in the base. This covers support cost for small schools and scales fairly.
- **Unlimited users** in every plan.
- **Pay only for active modules.** Dormant modules are free.
- **Annual billing by default.** Monthly is available, and annual works out to 2 months free.
- **All prices in INR, excluding 18% GST.** USD pricing for overseas international schools.
- **Price lock** for founding customers; renewal increases capped (e.g., max 7%/year).
- **No exit fees.** Full data export is always free.

### 22.3 Core plans

| | **Web** | **Lite** — *Small schools* | **Standard** ⭐ — *Medium schools* | **Global** — *International schools* | **Enterprise / Group** |
|---|---|---|---|---|---|
| **Best for** | Any school starting online; government schools | Up to ~600 students, single campus | ~600–2,500 students, 1–3 campuses | IB / Cambridge / AP or mixed curricula | Multi-campus groups, franchises, 2,500+ |
| **Annual base fee** | **Free** (subdomain) / **₹7,999** Web Pro (custom domain) | **₹19,999** incl. 200 students | **₹59,999** incl. 600 students | **₹2,49,999** incl. 500 students | Custom |
| **Per extra student / year** | — | **₹59** (up to 600) | **₹99** | **₹399** | Volume-tiered |
| **Monthly option** | ₹799/mo (Pro) | ₹1,999/mo + ₹6/student | ₹5,999/mo + ₹10/student | Annual only | Annual only |
| **Website** | 5 pages (Free) / unlimited (Pro) | Web Pro included | Web Pro included | Multi-site + programme microsites | Group brand + campus sites |
| **Modules (default)** | Website, enquiry form, notices, gallery, disclosures | SIS, attendance, fees (Lite), exams & report cards (Lite), homework, notices, parent PWA, WhatsApp, certificates, admissions (Lite) | Everything in Lite at Standard depth + admissions CRM, timetable & substitutions, HR & payroll, transport (incl. GPS software), visitor & gate, library, health room, behaviour, safeguarding, PTM & helpdesk, analytics | Everything in Standard at Advanced depth + International Programmes, multi-currency billing, LMS, university counselling, ECA, trips, store, recruitment, appraisal, facilities, IT assets, SSO & API | Global or Standard per campus + Multi-Campus Control, central finance, dedicated hosting options |
| **UI mode** | Simple | Simple | Standard | Advanced | Advanced |
| **Messaging wallet included** | — | ₹2,000/yr credit | ₹10/student/yr credit | ₹20/student/yr credit | Custom |
| **Storage** | 2 GB / 10 GB | 25 GB | 100 GB | 500 GB | Custom |
| **Onboarding** | Self-serve | Self-serve free; assisted ₹4,999 | ₹19,999 (waived on 2-yr plan) | From ₹1,00,000 | Custom |
| **Report-card formats included** | — | 1 board template | 1 board, custom layout | All programme templates configured | Custom |
| **Support** | Email | WhatsApp + phone, business hours | Extended hours + named onboarding manager | Priority + Customer Success Manager | Dedicated team, SLA |
| **SLA target** | — | 99.5% | 99.5% | 99.9% (when supported) | Contractual |

**Government / Trust plan — "Seva":** ₹9,999 per school per year (up to 1,000 students). Includes Web Pro, disclosures, attendance, Mid-Day Meal logging, scholarships, statutory registers and UDISE+ preparation. District or tender deployments of 100+ schools: ₹6,999 per school per year. CSR-sponsored deployments are supported.

**International schools outside India:** Global at **US$2,999/year base + US$4.99/student**, billed in USD.

### 22.4 Effective cost per student (illustrative, excl. GST)

| Students | Lite | Standard | Global | Market band (third-party est.) |
|---|---|---|---|---|
| 200 | ₹19,999 → **₹100/student** | — | — | ₹60–₹150 |
| 500 | ₹37,699 → **₹75/student** | — | ₹2,49,999 → ₹500/student | ₹50–₹150 |
| 1,000 | — (move to Standard) | ₹99,599 → **₹100/student** | ₹4,49,499 → **₹449/student** | ₹120–₹300 (standard) / ₹300–₹600 (premium) |
| 2,000 | — | ₹1,98,599 → **₹99/student** | ₹8,48,499 → **₹424/student** | ₹100–₹300 |
| 2,500 | — | ₹2,48,099 → **₹99/student** | ₹10,47,999 → ₹419/student | Enterprise ₹3L–₹12L total |

**Illustrative Year-1 total cost of ownership for a 1,000-student school**

| Cost item | Typical legacy ERP (third-party estimate) | ShikshaBlox Standard |
|---|---|---|
| Software subscription | ₹1.2L–₹3L | ₹99,599 |
| Data migration | ₹40K–₹2L | Included in onboarding |
| Staff training | ₹25K–₹1L | Included in onboarding |
| Branded parent app | ₹20K–₹80K | PWA included (white-label optional) |
| Report-card format | ₹15K–₹40K | 1 included |
| Onboarding fee | Varies | ₹19,999 (waived on 2-yr plan) |
| **Total (before messaging & GST)** | **≈ ₹2.2L–₹7.2L** | **≈ ₹1.2L** |

These are planning estimates. Validate them with real competitor quotes during discovery.

### 22.5 Add-on modules (for plans that don't include them)

| Add-on | Price (annual) | Typically bought by |
|---|---|---|
| Exams Standard (HPC, hall tickets, seating, moderation) | ₹15/student | Lite |
| Admissions CRM (full) | ₹9,999/campus | Lite |
| Timetable & Substitutions | ₹6,999/campus | Lite |
| HR & Payroll | ₹600/staff | Lite |
| Transport (routes, attendant app) | ₹1,200/vehicle | Lite |
| GPS live tracking (software) | ₹2,400/vehicle (hardware & SIM pass-through, or bring your own device) | Lite, Standard |
| RFID boarding / gate | ₹3,600/reader (software; hardware at cost) | Standard |
| Visitor & Gate | ₹4,999/campus | Lite |
| Library | ₹4,999/campus | Lite |
| Health Room | ₹3,999/campus | Lite |
| Early Years pack | ₹29/child | All |
| Hostel & Boarding | ₹99/boarder | All |
| Meals, Canteen & Cashless Wallet | ₹19/student | Standard |
| LMS & Virtual Classroom | ₹79/student | Lite, Standard |
| International Programmes (mixed schools) | ₹199/student in programme | Standard |
| University & Career Counselling | ₹499/student (Grades 11–12) | Standard |
| ECA / After-school / Day-care | ₹9,999/campus | Standard |
| Trips & Permissions | ₹4,999/campus | Lite |
| School Store & POS | ₹9,999/campus | Standard |
| White-label parent app (Android + iOS) | ₹24,999 one-time + ₹14,999/yr | Lite, Standard |
| Multi-Campus Control | ₹25,000/additional campus | Standard, Global |
| API & Webhooks | ₹24,999/yr | Standard |
| SSO (Google / Microsoft) | ₹9,999/yr | Standard |
| Dedicated database / data-residency hosting | Custom | Enterprise |
| Premium support (extended hours, CSM) | ₹24,999/yr | Lite, Standard |
| Extra storage | ₹2,999 per 100 GB | All |
| Additional custom report-card format | ₹7,500 each | All |

**AI Co-Pilot (M28), opt-in:**
- **AI Starter:** 5,000 credits for ₹4,999 (drafting notices, lesson plans, comments).
- **AI Teacher:** ₹1,499 per teacher per year, fair use.
- **AI Leadership:** ₹19,999 per campus per year (natural-language analytics, at-risk indicators).
- Credits are metered per action and shown in the billing console.

### 22.6 Usage-based charges (transparent pass-through)

| Item | Charge |
|---|---|
| WhatsApp messages | Meta/BSP cost + 10% platform fee, deducted from the messaging wallet; included credits by plan (above) |
| SMS (DLT) | Provider cost + 10% |
| Email | Free up to 10× student count per month; then ₹0.05/email |
| Online fee payments | **0% ShikshaBlox platform fee**. Gateway MDR is settled directly between the school and its gateway; an optional parent convenience fee is configurable |
| GPS SIM/data | At cost |

Every school gets budget caps, low-balance alerts and a monthly usage statement.

### 22.7 Discounts & offers

| Offer | Terms |
|---|---|
| Annual vs. monthly | Annual = 2 months free |
| Multi-year | 2-year prepaid −10%, 3-year −15% + price lock |
| Founding schools (first 50) | −40% in year 1, 3-year price lock, free onboarding, in return for feedback and a case study |
| Low-fee / Trust / NGO schools | −20% where monthly tuition is below an agreed threshold |
| Group volume | 5+ campuses −10%, 10+ −15%, 25+ −25% |
| Switch from a legacy ERP | Free migration from Fedena/Entab/Vidyalaya/Schoollog/Edisapp exports + up to 3 months free to cover overlap |
| Referral | 1 month free per referred school that converts |
| Free trial | 30 days, full Standard features, sandbox with demo data |

### 22.8 Billing rules
- **Student count** = active enrolled students on the billing date. Quarterly true-up for net additions above 5%. Decreases are adjusted at renewal.
- Alumni, withdrawn and applicant records are free (read-only).
- Plan upgrades are pro-rated immediately. Downgrades take effect at renewal (see §4.4 for data behaviour).
- Dormant (deactivated) modules are not billed. Data stays read-only for 90 days, then is archived.
- Invoices carry GST. School-side GST registration details are captured.
- Non-payment: reminders → 15-day grace → read-only mode (parents keep receipt access) → suspension. Data is never deleted during the agreed retention period.

### 22.9 Unit-economics guardrails (to validate)
- Target gross margin ≥ 75% after hosting, messaging fees retained, payment and support costs.
- Hosting + storage cost per student: assume ₹8–₹15/year for the first model; measure from pilots.
- The minimum base fee must cover onboarding and support cost for a 200-student school within 12 months.
- CAC payback: < 12 months for Lite/Standard, < 18 months for Global.
- Track net revenue retention, module attach rate, and messaging margin per school.

### 22.10 Module dependencies (activation rules)
- Admissions CRM → Organisation, Campus, Notification service; Website optional.
- SIS → Organisation, Campus, Academic Year, Identity, Document service.
- Attendance → SIS, Academic Calendar, Identity.
- Fees → SIS/Enrollment, Finance ledger; Payment integration optional (cash/manual supported).
- Exams → SIS, Curriculum Pack, Academic Year, Assessment configuration.
- International Programmes → Exams (Advanced), Curriculum Packs (IB/Cambridge/AP).
- University Counselling → SIS, Exams/Transcripts.
- Parent Hub → Guardian relationships, Identity, Notification service.
- Transport → SIS, Guardian relationships, Vehicle/Route data; GPS optional.
- Hostel → SIS, Attendance; Fees optional.
- WhatsApp → Communication preferences, approved templates, provider account, messaging wallet.
- AI → explicit school enablement, data permissions, provider configuration, audit and human approval.
- Multi-Campus Control → two or more campuses on Standard or Global.

### 22.11 Pricing validation plan
1. Run 15–20 willingness-to-pay interviews per profile (Van Westendorp + feature-value ranking).
2. Collect 5+ real competitor quotes per profile, including hidden costs.
3. Pilot with founding schools at the listed price minus the founding discount; measure conversion and objections.
4. Review prices after 2 sales cycles. Keep the "published, transparent pricing" promise even if levels change.

---

## 23. Onboarding, Migration & Customer Success

| Stage | Small (Lite) | Medium (Standard) | International (Global) |
|---|---|---|---|
| Kick-off | Self-serve wizard | Video call kick-off, project plan | On-site/virtual workshop with leadership & coordinators |
| Configuration | Starter templates | Fee rules, approvals, report card, timetable constraints | Programme packs, grading models, transcripts, SSO, multi-currency |
| Data migration | Excel templates, self-import | Assisted import from Excel/legacy exports, reconciliation of fee balances | Migration from SIS/curriculum platforms, historical grades, documents |
| Training | Video library (regional languages), 1 live session | Role-based sessions (office, accounts, teachers, exam cell) | Train-the-trainer, coordinator sessions, parent orientation |
| Parallel run | Optional | 2 weeks for fees and attendance | One reporting cycle recommended |
| Go-live | Day 1 | Week 2–4 | Week 4–8 |
| Success | Monthly health check (automated) | Quarterly business review | Quarterly business review + annual roadmap session |

**Migration wizard:** AI-assisted column mapping from Excel/CSV, Tally and common ERP exports; duplicate detection; validation preview; error report; rollback; fee-balance reconciliation report signed off by the school.

---

## 24. Product Roadmap — Profile-Driven Release Plan

The v1 30-week plan is replaced. Dates should be estimated after a technical spike and pilot discovery.

| Release | Scope | Sellable to |
|---|---|---|
| **R0 — Foundation** | Pilot discovery (website-only, small, medium, international school); design system; tenant/identity/entitlement/audit models; billing skeleton; CI/CD; demo data | — |
| **R1 — Website** | Website CMS, custom domain, SSL, themes, SEO, notices, events, gallery, forms, disclosures, enquiry inbox; Web plans live | Web, Seva (partial) |
| **R2 — Small-School MVP (Lite)** | Setup wizard, SIS, guardians, attendance (offline), fees Lite (cash/UPI, receipts, dues), notices, WhatsApp templates, parent PWA, report cards Lite, certificates/TC, import wizard, Simple mode | **Lite**, Seva |
| **R3 — Medium operations (Standard)** | Admissions CRM, fees Standard (instalments, concessions, gateway, reconciliation, Tally), timetable & substitutions, HR & payroll, transport + GPS, visitor/gate, library, health room, PTM/helpdesk, dashboards, approvals | **Standard** |
| **R4 — Academic depth** | Versioned curriculum packs (CBSE incl. HPC, ICSE/ISC, first state board), exams Standard (moderation, hall tickets, seating), lesson plans, homework submissions, behaviour, safeguarding | Standard (complete) |
| **R5 — International (Global)** | Advanced assessment engine, IB/Cambridge/AP packs, CAS/EE/IA, predicted grades, transcripts, multi-currency billing, university counselling, LMS, SSO, ECA, trips, store | **Global** |
| **R6 — Enterprise & AI** | Multi-campus control, APIs/webhooks, advanced analytics/warehouse, AI co-pilots, hostel, facilities, IT assets, dedicated hosting | **Enterprise**, AI add-ons |

**Release gate:** no module is production-ready without role/permission tests, tenant-isolation tests, audit coverage, migration/export, backup/restore validation, monitoring, user documentation, and pilot sign-off.

---

## 25. Differentiators Worth Building — and Features to Defer

### Build as true differentiators
1. **One platform for Small, Medium and International** via profiles and depth levels; no replatforming.
2. **Website-to-ERP journey** starting free.
3. **Transparent published pricing** with unlimited users and 0% payment platform fee.
4. **Compliance evidence centre** (disclosures, UDISE+, accreditation).
5. **Guardian 360** across children, guardians and campuses.
6. **WhatsApp-first, governed communication** with cost transparency.
7. **Workflow builder** without custom code.
8. **International programme depth** at a fraction of global stacks.
9. **Data portability**: quality import, export and exit.
10. **Regional-language UX** and scoped offline mode.
11. **Trust centre**: privacy notices, consent history, access logs, security posture.

### Defer until demand is proven
- Facial recognition for student attendance; emotion detection / classroom surveillance.
- Automated student ranking or disciplinary recommendations.
- Custom-built video conferencing (integrate instead).
- Blockchain credentials without a verified partner use case.
- Complex AI prediction before sufficient quality data exists.
- Full microservices decomposition before scale justifies it.
- On-premise deployments (consider only for large government tenders).

---

## 26. Pilot Acceptance Criteria & KPIs

### Website
- Admin creates and publishes a page without developer help; mobile performance, SEO, accessibility, domain and SSL checks pass; disclosure section easy to find with a review date; gallery publishing enforces consent.

### Small school (Lite)
- Setup to first attendance and first fee receipt within **1 working day**.
- Attendance for a class of 40 in under 60 seconds on a low-end Android phone, including offline recovery.
- Principal can run daily tasks entirely from a phone.

### Medium school (Standard)
- Fee balances reconcile with receipts and gateway settlements.
- Substitutions published before first period on test days.
- Every mark publication, fee adjustment, refund and record correction has an approval/audit trail.

### International school (Global)
- Criterion-based report cards and predicted grades match coordinator-approved samples.
- CAS/EE tracking used by DP coordinator for a full term.
- Multi-currency invoices and receipts accepted by the school's finance team.

### All profiles
- Parents see only authorised children and records; cross-tenant automated tests pass.
- Opt-in/opt-out recorded; template approval and delivery status visible; duplicates suppressed; fallback channels configured; no sensitive data in previews.

### Business KPIs
Time to onboard (by profile) · weekly active schools · parent activation and monthly active parents · attendance completion time · fee collection and reconciliation exception rate · enquiry→application→enrolment conversion · message delivery/failure/opt-out and cost per student · support tickets per 100 students · renewal, net revenue retention, module attach rate, upgrade rate (Lite→Standard→Global).

---

## 27. Open Decisions to Resolve During Discovery

1. Launch geography and first state board (e.g., Andhra Pradesh, Telangana or another state).
2. Government schools: direct paid, tender/procurement, or CSR-sponsored.
3. Launch-critical curricula: CBSE, ICSE/ISC, named state boards; which international programme first (IB or Cambridge).
4. First release: website-only or website + Lite.
5. Parent apps: PWA + WhatsApp first, or white-label native apps at launch.
6. Payments: each school's own merchant account vs. aggregator/marketplace model.
7. WhatsApp model: school-owned WhatsApp Business Account vs. managed onboarding with portability.
8. Hosting region, data residency, backup region, enterprise deployment options; overseas international school hosting.
9. Support hours, onboarding services, migration responsibility, SLAs.
10. Group schools: centralised finance and policies vs. consolidated reporting only.
11. **Pricing:** confirm price points via §22.11; decide USD pricing for overseas markets (e.g., GCC, Africa, SE Asia).
12. **Partnerships:** exam boards, BSPs, payment gateways, GPS hardware vendors, implementation partners/resellers (and reseller margins).
13. **Brand:** "ShikshaBlox" naming for plans (Web / Lite / Standard / Global / Enterprise / Seva) — confirm trademark availability.

---

## 28. Research Notes and Reference Sources

Requirements and prices change. Verify the latest circulars, rules, portal specifications, vendor prices and legal advice before implementation or making claims.

**Regulatory & communication**
- CBSE Affiliation Bye-Laws: https://www.cbse.gov.in/cbsenew/aff-bye-laws.html
- CBSE 2026 circular on prescribed teacher details on school websites: https://saras.cbse.gov.in/SARAS/Circulars/Circular01_2026639038229508601060.pdf
- CBSE Mandatory Public Disclosure circular: https://www.cbse.gov.in/cbsenew/documents/Circular_Merged_01_08012025.pdf
- UDISE+ portal: https://www.udiseplus.gov.in/
- Digital Personal Data Protection Act, 2023: https://www.meity.gov.in/static/uploads/2024/06/2bf1f0e9f04e6fb4f8fef35e82c42aa5.pdf
- DPDP Rules, 2025 (Gazette): https://www.meity.gov.in/static/uploads/2025/11/53450e6e5dc0bfa85ebd78686cadad39.pdf
- PIB explainer on DPDP Rules, 2025: https://www.pib.gov.in/PressNoteDetails.aspx?ModuleId=3&NoteId=156054&id=156054&lang=2&reg=3
- WhatsApp Business Platform policy: https://whatsappbusiness.com/policy/

**Competitor pricing signals (accessed 16 Nov 2026; indicative)**
- Fedena pricing listing (Standard US$999/yr): https://us.fitgap.com/products/022107/fedena
- Fedena tiers (Pro Plus US$1,299/yr): https://www.capterra.ie/reviews/126199/fedena-pro
- Entab CampusCare — contact vendor, annual subscription: https://www.goodfirms.co/software/entab-campuscare
- Vidyalaya — sales-led pricing: https://www.xpay.sh/saas-pricing/vidyalaya/
- VidyalayaERP — from ₹7,500/yr: https://www.techjockey.com/detail/vidyalayaerp
- Schoollog — sales-led pricing: https://www.xpay.sh/saas-pricing/schoollog/
- Edisapp — from US$1/user/month: https://www.capterra.com/p/200394/Edisapp-School-ERP/
- Indian school ERP cost bands & hidden costs (third-party vendor estimate): https://codingclave.com/guides/school-management-software-cost-india-2026
- JPRAXC School ERP pricing: https://www.softwareadvice.com.au/software/774848/JPRAXC-School-ERP
- TACHY School ERP pricing: https://tachy.in/school-erp-for-bengaluru-schools-pricing-features-setup/
- Toddle licensing model: https://www.toddleapp.com/?p=562
- ManageBac vs Toddle pricing structure: https://sites.google.com/moreland.edu/lmsm40a3/recommendations-proposals

**Research-derived implications**
- CBSE emphasises a functional website and prescribed public information → prominent, configurable disclosure area with controlled updates.
- UDISE+ is the official school data system → validated data preparation/export; no claim of official integration without approval.
- DPDP Act and Rules make child-data consent, safeguards, purpose limitation and accountability central → consent and privacy workflows with legal review.
- WhatsApp business messaging is governed by templates and service-window rules → design around opt-in, approval, pricing, delivery status and fallbacks.
- Legacy competitors largely hide prices → transparent, published pricing is a real differentiator.

---

## 29. Final Product Recommendation

Position ShikshaBlox as a **Composable School Operating Platform**, not another School ERP.

1. **Win with the website** (free/low-cost) and enquiries.
2. **Convert Small schools to Lite** with a 1-day setup, WhatsApp-native operations and transparent pricing.
3. **Grow Medium schools on Standard** with fee recovery, academics, transport and HR, at roughly ₹100 per student with unlimited users.
4. **Enter International schools with Global** once the advanced assessment engine and programme packs are validated with real coordinators.
5. **Expand into groups, integrations and AI** where it saves measurable staff time and stays safe, explainable and reviewable.

The defensible advantage is not the number of modules. It is the shared foundation: trustworthy student and guardian data, configurable school rules, depth levels that fit every school size, reliable communication, strong privacy, simple onboarding, honest pricing, and a website-to-enterprise upgrade path.

---

## Appendix A — Glossary

| Term | Meaning |
|---|---|
| **Profile** | Preset of modules, depth levels, configuration and limits for Small, Medium or International schools |
| **Overlay** | Add-on preset for Government/Trust, Multi-Campus, Boarding or Early Years |
| **Depth level** | Lite / Standard / Advanced variant of a module |
| **Entitlement** | Server-enforced permission for a tenant to use a module/feature within limits |
| **Curriculum Pack** | Versioned configuration of a board/programme's subjects, grading, rules and report layouts |
| **HPC** | Holistic Progress Card (NEP 2020 / CBSE) |
| **APAAR** | Automated Permanent Academic Account Registry ID |
| **UDISE+** | Unified District Information System for Education Plus |
| **DPDP** | Digital Personal Data Protection Act, 2023 and Rules, 2025 |
| **PYP / MYP / DP / CP** | IB Primary Years, Middle Years, Diploma and Career-related Programmes |
| **CAS / EE / IA / ToK** | Creativity-Activity-Service, Extended Essay, Internal Assessment, Theory of Knowledge |
| **IGCSE** | Cambridge International General Certificate of Secondary Education |
| **DSL** | Designated Safeguarding Lead |
| **POCSO** | Protection of Children from Sexual Offences Act |
| **BSP** | WhatsApp Business Solution Provider |
| **DLT** | Distributed Ledger Technology registration required for commercial SMS in India |
| **MDR** | Merchant Discount Rate charged by payment gateways |
| **RLS** | PostgreSQL Row-Level Security |
| **PWA** | Progressive Web App |

---

*End of document — ShikshaBlox Requirement Analysis & Product Blueprint v2.0*
