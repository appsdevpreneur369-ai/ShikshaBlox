# ShikshaBlox: pricing math & roadmap estimates (published on shikshablox.com)

**Date:** 2026-11-26. **Status:** recommendation, to be reviewed after the first 10 paying schools.
All amounts are in INR, excluding GST (18% is added to invoices).

## 1. Goal
The founder pays infrastructure costs out of pocket. Prices must (a) cover the **fixed platform cost** quickly,
(b) leave a **positive margin on every single school from day one**, including founding discounts, and
(c) never bundle a cash cost we can't control, such as messaging.

## 2. Cost model

### 2.1 Fixed platform cost (production, lean, GCP asia-south1)
| Item | ₹ / month |
|---|---|
| API (Cloud Run, 1 vCPU / 1 GiB, always on) | 3,800 |
| PostgreSQL (Cloud SQL db-g1-small + 20 GB SSD + backups) | 2,600 |
| School sites, Studio, www (Cloud Run, scale to zero) | 1,200 |
| Object storage + egress (GCS) | 500 |
| Email (transactional, free tier → paid), domain, monitoring | 600 |
| **Total** | **≈ 8,700 / month ≈ ₹1.05 lakh / year** |

Rule: staging stays scale-to-zero, and the staging DB is deleted when no demo is planned.

### 2.2 Variable cost per school per year
| Cost | Web Free | Web Pro | Lite (200 students) | Standard (1,000) | Global (500) |
|---|---|---|---|---|---|
| Hosting & storage (₹15/student/yr, spec §22.9 upper bound; ₹300 per website) | 300 | 500 | 3,000 | 15,000 | 7,500 |
| Support (₹500/hr) | 0 (self-serve, email) | 1,000 (2 h) | 2,500 (5 h) | 10,000 (20 h) | 25,000 (50 h) |
| Payment collection on our invoice (≈2%) | 0 | 160 | 400 | 2,000 | 9,000 |
| Included messaging credit (real cash) | 0 | 0 | 0 * | 0 * | 0 * |
| **Total variable** | **300** | **1,660** | **5,900** | **27,000** | **41,500** |

\* **Change vs spec §22.3:** included WhatsApp credits are removed from the base plans. All messaging is a
**prepaid wallet at provider cost + 10%**, so messaging can never lose money. A one-time ₹500 starter credit is given only on annual prepaid plans.

Onboarding labour (Standard ≈ 30 h, Global ≈ 80 h) is paid separately by the onboarding fee (₹19,999 / from ₹1,00,000),
so it isn't included above.

## 3. Prices published on the website
| Plan | List price | Year-1 founding price (−25%) | Variable cost | Year-1 margin at founding price |
|---|---|---|---|---|
| Web Free | ₹0 (subdomain, 5 extra pages, 2 GB, email support) | — | 300 | −300 (acquisition cost, capped) |
| Web Pro | ₹7,999/yr or ₹799/mo | ₹5,999 | 1,660 | **+4,339 (72%)** |
| Lite | ₹19,999/yr incl. 200 students + ₹59/extra student | ₹14,999 | 5,900 | **+9,099 (61%)** |
| Standard (1,000 students) | ₹59,999 incl. 600 + ₹99/extra = ₹99,599 | ₹74,699 | 27,000 | **+47,699 (64%)** |
| Global (500 students) | ₹2,49,999 incl. 500 + ₹399/extra | ₹1,87,499 | 41,500 | **+1,45,999 (78%)** |
| Seva (Government / Trust) | ₹9,999/school/yr (up to 1,000 students) | — (already discounted) | ~2,500 for website-only use today | **+7,499** |
| Enterprise | Custom (≥ Standard per-student rate) | — | — | — |

**Changes vs spec §22.7:**
- **Founding offer:** −25% in year 1 for the first 25 schools (the spec said −40% for the first 50). At −40%, a 1,000-student
  Standard school's first year earns ₹59,759 against ~₹27,000 variable plus onboarding overrun risk. That's too thin while one person supports everyone.
  Every plan stays well above 50% margin at −25%. The 3-year price lock and free migration stay.
- **No money for modules that aren't live.** Lite, Standard and Global are **pre-booked**: the school locks the founding price,
  and billing starts only when its modules go live. Web plans are billed now.
- **Web Free cap:** self-serve, email support only, "Powered by ShikshaBlox" badge, and review once there are 100 free schools.
  100 free schools cost ≈ ₹30,000/yr in hosting.
- Monthly Web Pro (₹799 × 12 = ₹9,588) is 20% dearer than annual, so annual is the default.

## 4. Break-even on the fixed cost (₹1.05 lakh/yr)
Contribution per school per year, at the founding price:
- Web Pro ₹4,339 → **25 Web Pro schools**, or
- Lite ₹9,099 → **12 Lite schools**, or
- Standard (1,000 students) ₹47,699 → **3 Standard schools**,
- or any mix, e.g. **10 Web Pro + 4 Lite + 1 Standard ≈ ₹1.3 lakh**.

So, until Lite goes live, website sales alone need **about 25 Web Pro schools** to cover hosting. The staging stack must stay
scale-to-zero, and the production DB should be shared by all tenants (it already is: RLS multi-tenancy).

## 5. Roadmap estimates (published as *target* quarters)
Assumptions: a founder-led team (1–2 engineers, AI-assisted), each release ships to **2–3 pilot schools for 4–6 weeks**
before general availability, and no release ships without passing the spec's §24 release gate.

| Release | Scope | Build estimate | Target availability |
|---|---|---|---|
| R1 Website & CMS | Website, CMS, enquiries, notices/events/gallery, disclosures, publishing workflow | done | **Live (Nov 2026)** |
| R1.1 Website extras | Custom domains + SSL, SEO/i18n/a11y hardening, menu builder, export | 4–6 weeks | **Jan 2027** |
| R2 Lite | Setup wizard, SIS & guardians, attendance (offline), fees Lite (cash/UPI, receipts), WhatsApp, parent app (PWA), report cards Lite, certificates/TC, import wizard | 12–14 weeks + 6-week pilot | **Q2 2027** |
| R3 Standard operations | Admissions CRM, fees Standard (instalments, gateway, Tally), timetable, HR & payroll, transport + GPS, visitor & gate, library, health room, PTM/helpdesk, dashboards | 18–20 weeks + pilot | **Q4 2027** |
| R4 Academic depth | Curriculum packs (CBSE HPC, ICSE, first state board), exams Standard, lesson plans, homework submissions, behaviour, safeguarding | 10–12 weeks + pilot | **Q1 2028** |
| R5 International (Global) | IB/Cambridge/AP engine, transcripts, multi-currency, counselling, LMS, SSO, ECA, trips, store | 20–24 weeks + pilot | **Q3 2028** |
| R6 Enterprise & AI | Multi-campus control, APIs/webhooks, advanced analytics, AI co-pilots, hostel, facilities, IT assets | 16–20 weeks | **Q4 2028** |

**Whole catalogue: about 24 months.** One AI feature, notice and message drafting, ships early with R2. If a pilot
school sponsors a module, it can move up one release.

The website shows these as "Target Q2 2027" etc. with the note "Targets, refined with our pilot schools".
