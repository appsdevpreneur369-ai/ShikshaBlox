/**
 * Module catalogue (from docs/ShikshaBlox-Schools-ERP-v2.md §5).
 * `release` is the internal build sequence (docs/PRICING-MATH.md §5). It is NOT shown on the public
 * website: the product owner decided (2026-11-26) not to publish release dates or "coming soon" labels.
 * Sales confirms module availability during the demo; billing for a module starts only when it is
 * switched on for the school.
 */
export type Release = "R1" | "R2" | "R3" | "R4" | "R5" | "R6";

export const releases: Record<Release, { name: string; live: boolean }> = {
  R1: { name: "Website & CMS", live: true },
  R2: { name: "Small-school ERP (Lite)", live: false },
  R3: { name: "Medium-school operations (Standard)", live: false },
  R4: { name: "Academic depth", live: false },
  R5: { name: "International (Global)", live: false },
  R6: { name: "Enterprise & AI", live: false },
};

export type IconName =
  | "globe" | "shield" | "funnel" | "phone" | "chat" | "users" | "home" | "check" | "calendar" | "book"
  | "pencil" | "award" | "sprout" | "file" | "world" | "ticket" | "lock" | "heart" | "compass" | "star"
  | "wallet" | "box" | "calculator" | "bag" | "id" | "briefcase" | "trend" | "bus" | "utensils" | "library"
  | "trophy" | "bed" | "map" | "clock" | "wrench" | "laptop" | "card" | "video" | "chart" | "sparkles"
  | "plug" | "building" | "graduation" | "megaphone" | "bell" | "layers" | "zap" | "smartphone" | "cloud";

export type CatalogueModule = {
  id: string;
  name: string;
  summary: string;
  icon: IconName;
  release: Release;
  page?: string;
};

export const catalogueGroups: { title: string; blurb: string; modules: CatalogueModule[] }[] = [
  {
    title: "Front door & engagement",
    blurb: "Everything parents see before and after admission.",
    modules: [
      { id: "M01", name: "School Website & CMS", summary: "Themes, 14 ready pages, notices, events, gallery, enquiries, SEO, approvals.", icon: "globe", release: "R1", page: "website-cms" },
      { id: "M02", name: "Mandatory Disclosure & Compliance", summary: "Board disclosure pages with evidence documents, review reminders, export packs.", icon: "shield", release: "R3" },
      { id: "M03", name: "Enquiry & Admissions CRM", summary: "Leads, follow-ups, applications, documents, entrance tests, offers.", icon: "funnel", release: "R3", page: "admissions-crm" },
      { id: "M12", name: "Parent & Student App", summary: "Attendance, homework, marks, fee ledger, leave and consent in one PWA.", icon: "smartphone", release: "R2", page: "parent-app-communication" },
      { id: "M13", name: "WhatsApp & Communication Hub", summary: "Templates, opt-in, two-way inbox, SMS/email fallback, acknowledgements.", icon: "chat", release: "R2", page: "parent-app-communication" },
      { id: "M26", name: "Surveys, PTM & Helpdesk", summary: "PTM slots, feedback, complaints and service requests with SLAs.", icon: "users", release: "R3" },
      { id: "M43", name: "Front Office & Reception", summary: "Walk-ins, call log, appointments, postal register, lost & found.", icon: "home", release: "R3" },
      { id: "M25", name: "Alumni & Community", summary: "Alumni directory, events, mentoring and verified communication.", icon: "graduation", release: "R6" },
    ],
  },
  {
    title: "Students & academics",
    blurb: "One record per child, from admission to alumni.",
    modules: [
      { id: "M04", name: "Student Information System", summary: "Student 360, guardians, siblings, documents, promotion, TC hand-off.", icon: "users", release: "R2", page: "student-information" },
      { id: "M07", name: "Attendance & Leave", summary: "Daily or period-wise, offline marking, leave requests, instant parent alerts.", icon: "check", release: "R2", page: "attendance" },
      { id: "M11", name: "Exams & Report Cards", summary: "Exam calendar, marks entry, CBSE/ICSE/State formats, HPC, report cards.", icon: "award", release: "R2", page: "exams-report-cards" },
      { id: "M32", name: "Certificates & Documents", summary: "TC, bonafide, character and fee certificates with QR verification.", icon: "file", release: "R2" },
      { id: "M10", name: "Homework & Assignments", summary: "Publish, collect, grade with rubrics and give feedback.", icon: "pencil", release: "R4" },
      { id: "M08", name: "Timetable & Substitutions", summary: "Constraint-based timetables, clash detection, daily substitutions.", icon: "calendar", release: "R3", page: "timetable" },
      { id: "M09", name: "Curriculum & Lesson Planning", summary: "Syllabus coverage, lesson plans, outcomes and observations.", icon: "book", release: "R4" },
      { id: "M31", name: "Early Years & Pre-Primary", summary: "Daily diary, milestones, photo journal, NEP foundational stage.", icon: "sprout", release: "R4" },
      { id: "M45", name: "External Exam Entries", summary: "Board registration data, LOC, candidate numbers, results import.", icon: "ticket", release: "R4" },
      { id: "M38", name: "International Programmes", summary: "IB, Cambridge, AP: criteria grading, CAS, EE, predicted grades, transcripts.", icon: "world", release: "R5" },
    ],
  },
  {
    title: "Welfare & safety",
    blurb: "Know every child is safe, and prove it.",
    modules: [
      { id: "M15", name: "Gate, Visitor & Pickup Safety", summary: "Visitor passes, authorised pickup, incidents, emergency muster.", icon: "lock", release: "R3" },
      { id: "M17", name: "Health Room", summary: "Visit log, parent notification, immunisation and health checks.", icon: "heart", release: "R3" },
      { id: "M33", name: "Behaviour & Pastoral Care", summary: "Merits, incidents, restorative actions and parent meetings.", icon: "star", release: "R4" },
      { id: "M34", name: "Safeguarding & Child Protection", summary: "Concern logging, DSL workflow, POCSO records, restricted access.", icon: "shield", release: "R4" },
      { id: "M24", name: "Counselling & Inclusion", summary: "Support plans, accommodations, referrals and reviews.", icon: "compass", release: "R5" },
    ],
  },
  {
    title: "Finance & administration",
    blurb: "Collect every rupee on time, with zero spreadsheet chasing.",
    modules: [
      { id: "M06", name: "Fees, Billing & Finance", summary: "Fee plans, concessions, UPI & gateway, receipts, dues, Tally export.", icon: "wallet", release: "R2", page: "fees-finance" },
      { id: "M22", name: "Payroll & Accounting", summary: "Payslips, PF/ESI/PT/TDS, Form 16 data, accounting exports.", icon: "calculator", release: "R3", page: "staff-hr-payroll" },
      { id: "M21", name: "Procurement & Inventory", summary: "Vendors, purchase orders, stock and asset register.", icon: "box", release: "R6" },
      { id: "M46", name: "School Store & POS", summary: "Uniforms, books and kits with online pre-orders.", icon: "bag", release: "R5" },
    ],
  },
  {
    title: "People",
    blurb: "Hire, run and grow your teaching team.",
    modules: [
      { id: "M05", name: "Staff & HR", summary: "Profiles, contracts, leave, staff attendance, substitutions.", icon: "id", release: "R3", page: "staff-hr-payroll" },
      { id: "M39", name: "Recruitment & Onboarding", summary: "Vacancies, applications, interviews, offers, verification.", icon: "briefcase", release: "R5" },
      { id: "M40", name: "Staff Growth & Appraisal", summary: "Goals, observations, CPD hours and appraisal cycles.", icon: "trend", release: "R5" },
    ],
  },
  {
    title: "Campus operations",
    blurb: "Buses, library, meals, hostel: all on the same platform.",
    modules: [
      { id: "M14", name: "Transport & GPS", summary: "Routes, stops, live bus tracking, boarding alerts, route fees.", icon: "bus", release: "R3", page: "transport" },
      { id: "M18", name: "Library", summary: "Catalogue, issue/return, reservations, barcodes and fines.", icon: "library", release: "R3" },
      { id: "M44", name: "Digital ID Cards", summary: "Design and print ID cards with QR/RFID; lost-card reissue.", icon: "card", release: "R3" },
      { id: "M19", name: "Activities, Sports & Houses", summary: "Clubs, house points, fixtures and achievements.", icon: "trophy", release: "R4" },
      { id: "M35", name: "Trips & Permissions", summary: "Consent slips, risk assessments, payments, headcounts.", icon: "map", release: "R5" },
      { id: "M36", name: "After-School & Day-Care", summary: "Activity booking, capacity, attendance and billing.", icon: "clock", release: "R5" },
      { id: "M16", name: "Meals & Canteen", summary: "Menus, allergy flags, cashless wallet, PM POSHAN logging.", icon: "utensils", release: "R6" },
      { id: "M20", name: "Hostel & Boarding", summary: "Rooms, roll-calls, outings, mess plans, house-parent updates.", icon: "bed", release: "R6" },
      { id: "M41", name: "Facilities & Bookings", summary: "Maintenance tickets, room and ground bookings, inspections.", icon: "wrench", release: "R6" },
      { id: "M42", name: "IT Assets & Devices", summary: "Device register, loans, BYOD and IT helpdesk.", icon: "laptop", release: "R6" },
    ],
  },
  {
    title: "Learning, intelligence & enterprise",
    blurb: "Insight for leaders, and the platform to scale to many campuses.",
    modules: [
      { id: "M27", name: "Analytics & Command Centre", summary: "Admissions funnel, attendance, fees and academics at a glance.", icon: "chart", release: "R3", page: "analytics" },
      { id: "M28", name: "AI Co-Pilots", summary: "Draft notices, summarise, search and spot risks, with human approval.", icon: "sparkles", release: "R6", page: "ai-copilots" },
      { id: "M23", name: "LMS & Virtual Classroom", summary: "Course spaces, live classes, quizzes and resources.", icon: "video", release: "R5" },
      { id: "M37", name: "University & Career Counselling", summary: "Shortlists, applications, transcripts and recommendations.", icon: "compass", release: "R5" },
      { id: "M29", name: "Integrations & APIs", summary: "APIs, webhooks, SSO, payments, biometric and accounting connectors.", icon: "plug", release: "R6" },
      { id: "M30", name: "Multi-Campus & Group Control", summary: "Central policies, local overrides, consolidated reports.", icon: "building", release: "R6" },
    ],
  },
];

export const allModules = catalogueGroups.flatMap((g) => g.modules);

export type Feature = { title: string; text: string; icon: IconName };

export type ModulePage = {
  slug: string;
  ids: string[];
  name: string;
  title: string; // <title>
  h1: string;
  description: string; // meta description
  intro: string;
  image: { src: string; alt: string };
  release: Release;
  features: Feature[];
  outcomes: string[];
  depth: { small: string; medium: string; international: string };
};

export const modulePages: ModulePage[] = [
  {
    slug: "website-cms",
    ids: ["M01", "M02"],
    name: "School Website & CMS",
    title: "School Website Builder & CMS for Schools in India",
    h1: "A beautiful school website your office can update in minutes",
    description:
      "Launch a fast, SEO-ready school website with 14 ready pages, notices, events, gallery, admissions enquiries and mandatory disclosures. No developer needed.",
    intro:
      "Every ShikshaBlox school starts here. Pick a theme, add your logo and photos, and publish a mobile-first website with all the pages parents look for. Your office team edits it in ShikshaBlox Studio, with approvals and one-click rollback.",
    image: { src: "/product/demo-desktop.webp", alt: "Homepage of Greenfield Demo School, a sample school website built on ShikshaBlox" },
    release: "R1",
    features: [
      { icon: "layers", title: "14 ready-made pages", text: "Home, About, Principal's message, Academics, Admissions, Facilities, Faculty, Events, Gallery, News, Contact, Careers, FAQs and Disclosures, created for you." },
      { icon: "pencil", title: "Block editor anyone can use", text: "24 content blocks (hero, stats, gallery, timeline, FAQs, forms) with live preview. No code, no agency." },
      { icon: "megaphone", title: "Notices, news & events", text: "Post a circular once and it appears on the homepage, the notices page and the event calendar." },
      { icon: "funnel", title: "Admissions enquiries", text: "Spam-protected enquiry form with consent; enquiries land in the Studio inbox with status and notes." },
      { icon: "shield", title: "Mandatory disclosures", text: "CBSE/State disclosure pages with documents and \"last reviewed\" reminders, so you never fall out of date." },
      { icon: "check", title: "Approvals & scheduling", text: "Draft → review → publish, scheduled go-live, private preview links and full revision history." },
      { icon: "zap", title: "Fast & SEO-ready", text: "Server-rendered pages, optimised images, sitemaps and structured data, so you rank for \"best school in your city\"." },
      { icon: "lock", title: "Photo consent built in", text: "Identifiable student photos need a consent declaration before they go public. That's DPDP-aware by design." },
      { icon: "globe", title: "Your own domain", text: "A free yourschool.shikshablox.com address, or your own domain with SSL on Web Pro, set up by our team." },
    ],
    outcomes: [
      "Live in a day with your existing content and photos",
      "Office staff update notices in under 2 minutes",
      "Every enquiry captured, none lost in WhatsApp",
    ],
    depth: {
      small: "All 14 pages, notices, gallery, enquiries, disclosures",
      medium: "Plus approvals, scheduling, preview links and a second language",
      international: "Plus programme microsites and multi-campus sites",
    },
  },
  {
    slug: "admissions-crm",
    ids: ["M03"],
    name: "Admissions CRM",
    title: "School Admissions CRM & Enquiry Management Software",
    h1: "Turn every enquiry into an admission",
    description:
      "Capture enquiries from your website, walk-ins and campaigns; follow up on time; run applications, documents, entrance tests and offers in one school admissions CRM.",
    intro:
      "Admissions season shouldn't live in a register and five WhatsApp groups. ShikshaBlox tracks every lead from first enquiry to fee payment, with reminders so no family is forgotten.",
    image: { src: "/images/principal-tablet.webp", alt: "School admissions head reviewing applications on a tablet" },
    release: "R3",
    features: [
      { icon: "funnel", title: "Lead pipeline", text: "Website, walk-in, phone and campaign leads in one board, by class and stage." },
      { icon: "bell", title: "Follow-up reminders", text: "Counsellors get daily follow-up lists; parents get WhatsApp updates." },
      { icon: "file", title: "Online applications", text: "Application forms, document upload and verification, with no paper." },
      { icon: "pencil", title: "Entrance tests & interviews", text: "Schedule slots, record scores and decide in one place." },
      { icon: "wallet", title: "Offers & admission fee", text: "Send offer letters and collect the admission fee online." },
      { icon: "chart", title: "Funnel analytics", text: "See conversion by source, class and counsellor." },
    ],
    outcomes: ["Respond to every enquiry the same day", "Know your seat fill rate in real time", "Hand over to the student record without re-typing"],
    depth: { small: "Enquiries, follow-ups, simple applications", medium: "Full pipeline, tests, offers, campaigns", international: "Multi-stage applications, waitlists, agents" },
  },
  {
    slug: "student-information",
    ids: ["M04", "M32"],
    name: "Student Information System",
    title: "Student Information System (SIS) for Schools",
    h1: "One complete record for every child",
    description:
      "ShikshaBlox SIS keeps student profiles, guardians, siblings, documents, promotions and transfer certificates in one secure, DPDP-aware record.",
    intro:
      "From admission to alumni, every module (attendance, fees, exams, transport) reads from the same student record. Update a phone number once and it's correct everywhere.",
    image: { src: "/images/students-writing.webp", alt: "Students writing in a classroom" },
    release: "R2",
    features: [
      { icon: "users", title: "Student 360", text: "Profile, guardians, siblings, health notes and documents on one screen." },
      { icon: "layers", title: "Bulk import", text: "Bring your Excel sheets or old ERP export; we validate and map columns for you." },
      { icon: "trend", title: "Promotions in one click", text: "Promote whole classes at year end, with exceptions handled cleanly." },
      { icon: "file", title: "TC & certificates", text: "Transfer, bonafide and character certificates with QR verification." },
      { icon: "lock", title: "Field-level privacy", text: "Sensitive fields masked by role; every view and change audited." },
      { icon: "graduation", title: "Alumni hand-off", text: "Leavers move to the alumni record; nothing gets deleted by mistake." },
    ],
    outcomes: ["No more duplicate registers", "Certificates in seconds, not days", "Clean data for every other module"],
    depth: { small: "Profiles, guardians, documents, certificates", medium: "Plus houses, sections, custom fields", international: "Plus programmes, nationality & visa data" },
  },
  {
    slug: "attendance",
    ids: ["M07"],
    name: "Attendance & Leave",
    title: "School Attendance App with Instant Parent Alerts",
    h1: "Attendance in 30 seconds, parents informed in 60",
    description:
      "Mark daily or period-wise attendance on any phone, even offline. Parents get instant absence alerts on WhatsApp, and leave requests flow to class teachers.",
    intro:
      "Teachers tap through the class list on their phone, even when the network drops. Absent students' parents are notified automatically, and the principal sees the whole school by 9:30.",
    image: { src: "/images/modern-classroom.webp", alt: "Students at desks in a bright classroom" },
    release: "R2",
    features: [
      { icon: "smartphone", title: "Mobile, offline-first", text: "Mark attendance with no signal; it syncs when you're back online." },
      { icon: "bell", title: "Instant absence alerts", text: "WhatsApp or SMS to parents within minutes of marking." },
      { icon: "calendar", title: "Daily or period-wise", text: "Choose per class; late and early-exit tracking included." },
      { icon: "file", title: "Leave requests", text: "Parents apply in the app; teachers approve in one tap." },
      { icon: "chart", title: "Live dashboard", text: "School-wide attendance and chronic absentee flags for leadership." },
      { icon: "card", title: "RFID & biometric ready", text: "Connect gates and devices when you're ready (Standard)." },
    ],
    outcomes: ["Attendance done before the first bell ends", "Parents know about absences the same morning", "Registers you can audit"],
    depth: { small: "Daily attendance, alerts, leave", medium: "Period-wise, corrections, RFID/biometric", international: "Plus rotating schedules and blocks" },
  },
  {
    slug: "fees-finance",
    ids: ["M06"],
    name: "Fees & Finance",
    title: "School Fee Management Software with UPI & Online Payments",
    h1: "Collect fees on time, without chasing",
    description:
      "Fee structures, instalments, concessions, UPI and online payments, instant receipts, automatic reminders and Tally export. 0% ShikshaBlox fee on online payments.",
    intro:
      "Set up your fee plan once. Parents get reminders and pay by UPI or card, receipts go out automatically, and your accountant sees dues and collections by class every day.",
    image: { src: "/images/office-accounts.webp", alt: "School accountant working on a laptop" },
    release: "R2",
    features: [
      { icon: "wallet", title: "Flexible fee plans", text: "Term, monthly or custom instalments, with transport and hostel heads." },
      { icon: "star", title: "Concessions & scholarships", text: "Sibling, staff-child and merit concessions with approvals." },
      { icon: "smartphone", title: "UPI & online payments", text: "Your own gateway; money goes straight to the school. 0% platform fee." },
      { icon: "file", title: "Instant receipts", text: "GST-ready receipts on WhatsApp and email, and cash counters too." },
      { icon: "bell", title: "Automatic reminders", text: "Polite, scheduled reminders before and after due dates." },
      { icon: "calculator", title: "Tally & reconciliation", text: "Daily collection reports and Tally-ready exports (Standard)." },
    ],
    outcomes: ["Faster collections, fewer defaulters", "End-of-day cash tally in minutes", "Parents pay in two taps"],
    depth: { small: "Cash/UPI, receipts, dues, reminders", medium: "Instalments, gateway, concessions, Tally", international: "Multi-currency, deposits, sponsor billing" },
  },
  {
    slug: "exams-report-cards",
    ids: ["M11", "M45"],
    name: "Exams & Report Cards",
    title: "Exam Management & Report Card Software (CBSE, ICSE, State)",
    h1: "Report cards your board accepts, in one evening",
    description:
      "Plan exams, enter marks on any device and print CBSE, ICSE/ISC and State Board report cards, including NEP 2020 Holistic Progress Cards.",
    intro:
      "Teachers enter marks on their phones; ShikshaBlox applies your board's grading rules and produces print-ready report cards, with no Excel formulas and no all-nighters.",
    image: { src: "/images/teacher-blackboard.webp", alt: "Teacher teaching students at a blackboard" },
    release: "R2",
    features: [
      { icon: "calendar", title: "Exam calendar", text: "Schedule terms, units and practicals for every class." },
      { icon: "pencil", title: "Marks entry anywhere", text: "Subject teachers enter marks on mobile; class teachers verify." },
      { icon: "award", title: "Board-ready formats", text: "CBSE, ICSE/ISC and State formats; HPC for NEP 2020." },
      { icon: "ticket", title: "Hall tickets & seating", text: "Generate hall tickets and seating plans (Standard)." },
      { icon: "chart", title: "Result analysis", text: "Subject, section and student trends for academic reviews." },
      { icon: "smartphone", title: "Results to parents", text: "Publish to the parent app with one click." },
    ],
    outcomes: ["Report cards printed the day after exams", "Zero formula errors", "Teachers spend time teaching, not typing"],
    depth: { small: "1 board format, marks entry, report cards", medium: "Moderation, hall tickets, seating, HPC", international: "Criteria grading, predicted grades, transcripts" },
  },
  {
    slug: "parent-app-communication",
    ids: ["M12", "M13"],
    name: "Parent App & WhatsApp",
    title: "Parent App & WhatsApp Communication for Schools",
    h1: "Reach every parent where they already are: WhatsApp",
    description:
      "A parent app (PWA) for attendance, homework, marks and fees, plus a governed WhatsApp hub with templates, opt-in, two-way inbox and delivery tracking.",
    intro:
      "Most parents won't install yet another app, but they read WhatsApp. ShikshaBlox sends approved WhatsApp messages with SMS/email fallback, and gives parents a lightweight app for everything else.",
    image: { src: "/images/parent-phone.webp", alt: "Parent reading a school message on her phone" },
    release: "R2",
    features: [
      { icon: "chat", title: "WhatsApp-first", text: "Official WhatsApp Business templates with opt-in and delivery status." },
      { icon: "bell", title: "Smart fallback", text: "SMS or email automatically if WhatsApp isn't delivered." },
      { icon: "smartphone", title: "Parent app (PWA)", text: "Attendance, homework, marks, fee ledger, leave and circulars, with no app store needed." },
      { icon: "check", title: "Circular acknowledgements", text: "Know exactly which parents have read the circular." },
      { icon: "clock", title: "Quiet hours & languages", text: "No messages at midnight; send in English, Hindi, Telugu and more." },
      { icon: "wallet", title: "Prepaid, transparent wallet", text: "Pay WhatsApp costs at provider rate + 10%, with caps and alerts." },
    ],
    outcomes: ["Parents actually read your notices", "One governed channel instead of 40 class groups", "Every message logged and auditable"],
    depth: { small: "Notices, alerts, parent PWA", medium: "Two-way inbox, PTM booking, helpdesk", international: "Multi-language families, consent forms" },
  },
  {
    slug: "timetable",
    ids: ["M08"],
    name: "Timetable & Substitutions",
    title: "School Timetable Generator & Substitution Management",
    h1: "Clash-free timetables and same-day substitutions",
    description:
      "Build constraint-based school timetables with rooms and labs, detect clashes automatically and assign substitutions in seconds when a teacher is absent.",
    intro:
      "Set teacher loads, rooms and rules once; ShikshaBlox proposes a clash-free timetable. When a teacher calls in sick, it suggests free, qualified substitutes instantly.",
    image: { src: "/images/empty-classroom.webp", alt: "Empty modern classroom with desks and a smart board" },
    release: "R3",
    features: [
      { icon: "calendar", title: "Constraint-based builder", text: "Teacher loads, consecutive periods, labs and rooms respected." },
      { icon: "zap", title: "Clash detection", text: "Instant warnings for double-booked teachers or rooms." },
      { icon: "users", title: "Smart substitutions", text: "Suggests free teachers by subject and fair workload." },
      { icon: "layers", title: "Versions", text: "Try a new timetable without disturbing the live one." },
      { icon: "smartphone", title: "Everyone's view", text: "Teachers, students and parents see their own timetable in the app." },
      { icon: "chart", title: "Workload reports", text: "Fair distribution across departments." },
    ],
    outcomes: ["Timetable done in a day, not a week", "Substitutions sorted before assembly", "No more clashes in the first week"],
    depth: { small: "Add-on: simple timetable", medium: "Constraints, substitutions, versions", international: "Rotating cycles and blocks" },
  },
  {
    slug: "staff-hr-payroll",
    ids: ["M05", "M22"],
    name: "Staff, HR & Payroll",
    title: "School HR & Payroll Software (PF, ESI, PT, TDS)",
    h1: "Staff records, leave and payroll, all in one place",
    description:
      "Manage teacher and staff profiles, contracts, leave and attendance, and run compliant payroll with PF, ESI, PT and TDS in the same school platform.",
    intro:
      "From joining documents to monthly payslips, ShikshaBlox keeps your staff data in one place and runs payroll with Indian statutory deductions built in.",
    image: { src: "/images/office-hr.webp", alt: "School administrator working at a desk" },
    release: "R3",
    features: [
      { icon: "id", title: "Staff profiles", text: "Qualifications, contracts, documents and service history." },
      { icon: "calendar", title: "Leave & attendance", text: "Leave balances, approvals and staff attendance by biometric or app." },
      { icon: "calculator", title: "Payroll", text: "Salary structures, PF/ESI/PT/TDS and payslips by email." },
      { icon: "file", title: "Form 16 data", text: "Year-end tax data ready for your accountant." },
      { icon: "users", title: "Substitution link", text: "Leave automatically feeds the substitution planner." },
      { icon: "lock", title: "Private by role", text: "Salary data visible only to authorised people." },
    ],
    outcomes: ["Payroll in an afternoon", "Leave without paper slips", "Audit-ready staff files"],
    depth: { small: "Add-on: staff records and leave", medium: "Full HR and payroll", international: "Plus recruitment, appraisal, expat staff" },
  },
  {
    slug: "transport",
    ids: ["M14"],
    name: "Transport & GPS",
    title: "School Bus Tracking & Transport Management Software",
    h1: "Every bus, every stop, every child accounted for",
    description:
      "Plan routes and stops, track school buses live on GPS, alert parents on boarding and drop, and bill transport fees from the same system.",
    intro:
      "Parents stop calling the office to ask where the bus is. Attendants mark boarding on a phone, parents get alerts, and the transport in-charge sees every vehicle live.",
    image: { src: "/images/school-bus.webp", alt: "Yellow school bus parked at a school" },
    release: "R3",
    features: [
      { icon: "map", title: "Routes & stops", text: "Plan routes, stops and pickup times; assign students in bulk." },
      { icon: "bus", title: "Live GPS tracking", text: "Use your existing GPS device or our recommended hardware." },
      { icon: "bell", title: "Boarding alerts", text: "Parents notified when their child boards and alights." },
      { icon: "shield", title: "Safety first", text: "Over-speed and route-deviation alerts; driver and attendant records." },
      { icon: "wallet", title: "Route-based fees", text: "Transport fees flow straight into the fee module." },
      { icon: "smartphone", title: "Attendant app", text: "Simple boarding checklist that works offline." },
    ],
    outcomes: ["Fewer anxious calls to the office", "Proof of every pickup and drop", "Transport fees always correct"],
    depth: { small: "Add-on: routes and allocation", medium: "GPS, attendant app, alerts", international: "Plus RFID boarding and multiple campuses" },
  },
  {
    slug: "analytics",
    ids: ["M27"],
    name: "Analytics & Command Centre",
    title: "School Analytics Dashboard for Principals & Management",
    h1: "Your whole school on one screen",
    description:
      "Admissions, attendance, fee collection and academic performance dashboards for principals, trustees and management, updated in real time.",
    intro:
      "Stop waiting for month-end reports. The command centre shows what needs attention today: low attendance, overdue fees, admissions gaps and academic trends.",
    image: { src: "/images/principal-tablet.webp", alt: "Principal checking dashboards on a tablet" },
    release: "R3",
    features: [
      { icon: "chart", title: "Live dashboards", text: "Admissions, attendance, fees and academics, all in one view." },
      { icon: "bell", title: "Exceptions, not noise", text: "Get alerted only when a number needs your attention." },
      { icon: "layers", title: "Report library", text: "40+ standard reports and a simple report builder." },
      { icon: "building", title: "Campus comparison", text: "Compare branches side by side (Enterprise)." },
      { icon: "file", title: "Board-meeting packs", text: "One-click PDF summaries for trustees." },
      { icon: "lock", title: "Role-based access", text: "Each leader sees exactly what they should." },
    ],
    outcomes: ["Decisions on today's data", "Monthly reviews in minutes", "Trustees always informed"],
    depth: { small: "Key numbers dashboard", medium: "Full command centre and reports", international: "Advanced analytics and benchmarks" },
  },
  {
    slug: "ai-copilots",
    ids: ["M28"],
    name: "AI Co-Pilots",
    title: "AI for Schools: Safe, Explainable AI Co-Pilots",
    h1: "AI that helps your staff, with a human always in charge",
    description:
      "Opt-in AI co-pilots that draft notices, summarise data and flag at-risk students, with permission-aware access, citations, human approval and full audit.",
    intro:
      "ShikshaBlox AI is useful, not gimmicky. It drafts the circular, summarises the week and points out patterns, but nothing reaches a parent without a person approving it.",
    image: { src: "/images/auditorium.webp", alt: "Staff meeting in a school auditorium" },
    release: "R6",
    features: [
      { icon: "pencil", title: "Drafting assistant", text: "Notices, circulars and messages drafted in seconds, in your tone and language." },
      { icon: "sparkles", title: "Report-card comments", text: "Suggested remarks teachers edit and approve." },
      { icon: "chart", title: "Ask your data", text: "\"Which classes had the lowest attendance this month?\" answered with sources." },
      { icon: "bell", title: "Early warnings", text: "At-risk indicators combining attendance, marks and fees." },
      { icon: "lock", title: "Permission-aware", text: "AI only sees what the user is allowed to see." },
      { icon: "check", title: "Human approval & audit", text: "Every AI action approved and logged; opt-in per school." },
    ],
    outcomes: ["Hours saved every week for office staff", "Earlier help for struggling students", "No black boxes: every answer cites its source"],
    depth: { small: "Opt-in drafting credits", medium: "Teacher co-pilot", international: "Leadership analytics co-pilot" },
  },
];

export function findModulePage(slug: string) {
  return modulePages.find((m) => m.slug === slug);
}
