import type { IconName } from "./modules";

export type Profile = {
  slug: string;
  name: string;
  short: string;
  title: string;
  h1: string;
  description: string;
  intro: string;
  image: { src: string; alt: string };
  plan: string;
  who: string;
  goLive: string;
  pains: string[];
  blocks: { title: string; text: string; icon: IconName; href?: string }[];
};

export const profiles: Profile[] = [
  {
    slug: "small-schools",
    name: "Small schools",
    short: "Up to ~600 students, one campus",
    title: "School Management Software for Small Schools",
    h1: "Everything a small school needs. Nothing it doesn't.",
    description:
      "Simple, affordable school ERP for small schools: website, student records, attendance, fees, report cards and WhatsApp updates. Live in a day.",
    intro:
      "Small schools don't need a 300-screen ERP. ShikshaBlox Lite gives your office the essentials in Simple mode: a website, student records, attendance, fees and report cards, all on one phone-friendly platform.",
    image: { src: "/images/students-group.webp", alt: "Smiling school students in uniform" },
    plan: "Lite",
    who: "Pre-primary to Class 10 schools, single campus, up to about 600 students",
    goLive: "Live in a day (self-serve setup wizard)",
    pains: [
      "Registers, receipt books and Excel sheets that never agree",
      "Fee follow-ups eating the office's afternoons",
      "Parents asking the same questions on WhatsApp all day",
      "An outdated website, or none at all",
    ],
    blocks: [
      { title: "School website", text: "14 pages ready on day one.", icon: "globe", href: "/features/website-cms" },
      { title: "Student records", text: "Profiles, guardians, TCs.", icon: "users", href: "/features/student-information" },
      { title: "Attendance", text: "Mobile, with parent alerts.", icon: "check", href: "/features/attendance" },
      { title: "Fees", text: "Cash/UPI, receipts, reminders.", icon: "wallet", href: "/features/fees-finance" },
      { title: "Report cards", text: "Your board's format.", icon: "award", href: "/features/exams-report-cards" },
      { title: "WhatsApp notices", text: "Reach every parent.", icon: "chat", href: "/features/parent-app-communication" },
    ],
  },
  {
    slug: "medium-schools",
    name: "Medium schools",
    short: "600–2,500 students, 1–3 campuses",
    title: "School ERP for Medium Schools: Admissions to Payroll",
    h1: "Run the whole school from one platform",
    description:
      "Complete school ERP for 600–2,500 students: admissions CRM, fees with instalments and gateway, timetable, HR & payroll, transport GPS and dashboards.",
    intro:
      "As schools grow, disconnected tools multiply. ShikshaBlox Standard connects admissions, academics, finance, transport and HR, so leadership sees one truth and departments stop re-typing data.",
    image: { src: "/images/hero-classroom.webp", alt: "Teacher with a full class of students" },
    plan: "Standard",
    who: "K-12 schools with 600–2,500 students, often across 1–3 campuses",
    goLive: "Live in 2–4 weeks with a named onboarding manager",
    pains: [
      "Separate software for fees, transport and payroll that don't talk",
      "Admissions leads lost between reception and counsellors",
      "Timetable and substitution chaos every morning",
      "Management reports that take a week to compile",
    ],
    blocks: [
      { title: "Admissions CRM", text: "From enquiry to admission.", icon: "funnel", href: "/features/admissions-crm" },
      { title: "Fees & finance", text: "Instalments, gateway, Tally.", icon: "wallet", href: "/features/fees-finance" },
      { title: "Timetable", text: "Clash-free, smart substitutes.", icon: "calendar", href: "/features/timetable" },
      { title: "HR & payroll", text: "PF, ESI, PT, TDS built in.", icon: "id", href: "/features/staff-hr-payroll" },
      { title: "Transport & GPS", text: "Live buses, parent alerts.", icon: "bus", href: "/features/transport" },
      { title: "Command centre", text: "The whole school, one screen.", icon: "chart", href: "/features/analytics" },
    ],
  },
  {
    slug: "international-schools",
    name: "International schools",
    short: "IB · Cambridge · AP · mixed curricula",
    title: "School Management Software for International Schools (IB, Cambridge)",
    h1: "Built for IB, Cambridge and AP from the ground up",
    description:
      "International school platform with criteria-based assessment, IB/Cambridge/AP packs, transcripts, multi-currency billing, university counselling, LMS and SSO.",
    intro:
      "International schools are usually forced to stitch together five products. ShikshaBlox Global brings programme-aware assessment, admissions, multi-currency finance and counselling onto one data model, with India-ready compliance included.",
    image: { src: "/images/senior-students.webp", alt: "Senior students in blazers walking on campus" },
    plan: "Global",
    who: "IB, Cambridge, AP and mixed-curriculum schools, day or boarding",
    goLive: "Live in 4–8 weeks with leadership workshops",
    pains: [
      "Criteria grading and transcripts bolted onto a CBSE-style ERP",
      "Fees in multiple currencies and sponsors handled in spreadsheets",
      "Separate tools for admissions, LMS, counselling and trips",
      "Foreign-national student and staff records outside the system",
    ],
    blocks: [
      { title: "Programme assessment", text: "Criteria, CAS, EE, predicted grades.", icon: "world" },
      { title: "Transcripts", text: "Official, verifiable, on demand.", icon: "file" },
      { title: "Multi-currency billing", text: "INR, USD and more; sponsors.", icon: "wallet", href: "/features/fees-finance" },
      { title: "University counselling", text: "Shortlists to outcomes.", icon: "graduation" },
      { title: "LMS & ECA", text: "Courses, clubs and trips.", icon: "video" },
      { title: "SSO & APIs", text: "Google, Microsoft, webhooks.", icon: "plug" },
    ],
  },
  {
    slug: "government-trust",
    name: "Government & Trust schools",
    short: "Aided, trust and government schools",
    title: "School Software for Government, Aided & Trust Schools",
    h1: "Transparent, compliant and affordable: the Seva plan",
    description:
      "ShikshaBlox Seva for government, aided and trust schools: website, disclosures, attendance, Mid-Day Meal logging, scholarships and UDISE+ preparation from ₹6,999 per school.",
    intro:
      "Public and trust schools need transparency and compliance on a tight budget. Seva bundles a public website, disclosures and the registers inspectors ask for, at a price districts and CSR sponsors can scale.",
    image: { src: "/images/yoga-assembly.webp", alt: "Students doing yoga at a school assembly" },
    plan: "Seva",
    who: "Government, aided, trust and NGO-run schools; district deployments",
    goLive: "Website live in a day; district roll-outs in waves",
    pains: [
      "Disclosure and UDISE+ data scattered across registers",
      "No public website for parents and inspectors",
      "Mid-Day Meal and scholarship records on paper",
      "No budget for expensive private-school ERPs",
    ],
    blocks: [
      { title: "Public website", text: "Live in a day.", icon: "globe", href: "/features/website-cms" },
      { title: "Disclosures", text: "Always current, with evidence.", icon: "shield" },
      { title: "Attendance", text: "Students and staff.", icon: "check", href: "/features/attendance" },
      { title: "Mid-Day Meal", text: "PM POSHAN daily logging.", icon: "utensils" },
      { title: "Scholarships", text: "Eligibility and tracking.", icon: "award" },
      { title: "UDISE+ preparation", text: "Data ready for submission.", icon: "file" },
    ],
  },
];

export function findProfile(slug: string) {
  return profiles.find((p) => p.slug === slug);
}
