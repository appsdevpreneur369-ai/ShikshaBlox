/**
 * Site-wide settings. Values that differ between staging and production come from build-time env
 * (the pages are statically generated), with production defaults.
 */
export const site = {
  name: "ShikshaBlox",
  tagline: "The School ERP that grows with your school",
  description:
    "ShikshaBlox is a modern, modular School ERP and school website platform for Indian and international schools: website & CMS, admissions, student records, attendance, fees, exams, parent app, WhatsApp communication and more.",
  url: (process.env.SB_WWW_SITE_URL || "https://shikshablox.com").replace(/\/$/, ""),
  noindex: process.env.SB_WWW_NOINDEX === "true",
  company: {
    name: "Aurora Blossom",
    url: "https://aurora-blossom.com",
  },
  /** Sales contacts (Aurora Blossom). Build-time env can override them. */
  contact: {
    whatsapp: process.env.SB_WWW_WHATSAPP || "918121606633", // digits only, with country code
    phone: process.env.SB_WWW_PHONE || "+91 81216 06633",
    email: process.env.SB_WWW_EMAIL || "info@aurora-blossom.com",
  },
  studioUrl: process.env.SB_WWW_STUDIO_URL || "https://studio.shikshablox.com",
} as const;

export type NavItem = { label: string; href: string; children?: { label: string; href: string; note?: string }[] };

export const nav: NavItem[] = [
  {
    label: "Product",
    href: "/features",
    children: [
      { label: "All modules", href: "/features", note: "40+ blocks, one platform" },
      { label: "School Website & CMS", href: "/features/website-cms", note: "Online in a day" },
      { label: "Admissions CRM", href: "/features/admissions-crm" },
      { label: "Fees & Finance", href: "/features/fees-finance" },
      { label: "Parent App & WhatsApp", href: "/features/parent-app-communication" },
      { label: "Attendance", href: "/features/attendance" },
    ],
  },
  {
    label: "Solutions",
    href: "/solutions/small-schools",
    children: [
      { label: "Small schools", href: "/solutions/small-schools", note: "Up to ~600 students" },
      { label: "Medium schools", href: "/solutions/medium-schools", note: "600–2,500 students" },
      { label: "International schools", href: "/solutions/international-schools", note: "IB · Cambridge · AP" },
      { label: "Government & Trust", href: "/solutions/government-trust", note: "Seva plan" },
    ],
  },
  { label: "Pricing", href: "/pricing" },
  { label: "Demo school", href: "/schools" },
  { label: "Why us", href: "/why-shikshablox" },
];

export const footerNav: { title: string; links: { label: string; href: string }[] }[] = [
  {
    title: "Product",
    links: [
      { label: "All modules", href: "/features" },
      { label: "School Website & CMS", href: "/features/website-cms" },
      { label: "Pricing", href: "/pricing" },
      { label: "Demo school website", href: "/schools" },
      { label: "Security & privacy", href: "/security" },
    ],
  },
  {
    title: "Solutions",
    links: [
      { label: "Small schools", href: "/solutions/small-schools" },
      { label: "Medium schools", href: "/solutions/medium-schools" },
      { label: "International schools", href: "/solutions/international-schools" },
      { label: "Government & Trust", href: "/solutions/government-trust" },
    ],
  },
  {
    title: "Company",
    links: [
      { label: "About", href: "/about" },
      { label: "Why ShikshaBlox", href: "/why-shikshablox" },
      { label: "Book a demo", href: "/contact" },
    ],
  },
  {
    title: "Legal",
    links: [
      { label: "Privacy policy", href: "/privacy" },
      { label: "Terms of service", href: "/terms" },
    ],
  },
];
