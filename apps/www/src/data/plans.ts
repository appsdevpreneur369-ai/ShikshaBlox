/** Published prices; see docs/PRICING-MATH.md for the cost model behind every number. */
export type Plan = {
  id: string;
  name: string;
  for: string;
  price: string;
  period: string;
  priceNote?: string;
  founding?: string;
  highlight?: boolean;
  cta: { label: string; href: string };
  features: string[];
};

export const FOUNDING_DISCOUNT = 25;
export const FOUNDING_SEATS = 25;

export const plans: Plan[] = [
  {
    id: "web-free",
    name: "Web Free",
    for: "Any school getting online",
    price: "₹0",
    period: "forever",
    cta: { label: "Start free", href: "/contact?plan=web-free" },
    features: [
      "All 14 standard pages",
      "Notices, news, events & gallery",
      "Admissions enquiry form & inbox",
      "Mandatory disclosure pages",
      "yourschool.shikshablox.com address",
      "5 extra custom pages · 2 GB media",
      "Email support",
    ],
  },
  {
    id: "web-pro",
    name: "Web Pro",
    for: "Schools that want their own brand",
    price: "₹7,999",
    period: "per year",
    priceNote: "or ₹799/month",
    founding: "₹5,999 in year 1 for founding schools",
    highlight: true,
    cta: { label: "Book a demo", href: "/contact?plan=web-pro" },
    features: [
      "Everything in Web Free",
      "Your own domain + SSL, set up by our team",
      "Unlimited custom pages · 10 GB media",
      "Approval workflow, scheduling & preview links",
      "Priority support",
    ],
  },
  {
    id: "lite",
    name: "Lite",
    for: "Small schools, up to ~600 students",
    price: "₹19,999",
    period: "per year",
    priceNote: "includes 200 students · ₹59 per extra student",
    founding: "₹14,999 in year 1 for founding schools",
    cta: { label: "Book a demo", href: "/contact?plan=lite" },
    features: [
      "Web Pro included",
      "Student records, guardians & certificates",
      "Attendance with instant parent alerts",
      "Fees: cash/UPI, receipts, dues, reminders",
      "Report cards (1 board format)",
      "Parent app + WhatsApp notices",
      "Self-serve onboarding · live in a day",
    ],
  },
  {
    id: "standard",
    name: "Standard",
    for: "Medium schools, 600–2,500 students",
    price: "₹59,999",
    period: "per year",
    priceNote: "includes 600 students · ₹99 per extra student",
    founding: "25% off year 1 for founding schools",
    cta: { label: "Book a demo", href: "/contact?plan=standard" },
    features: [
      "Everything in Lite, at full depth",
      "Admissions CRM",
      "Fees: instalments, concessions, gateway, Tally",
      "Timetable & substitutions",
      "HR & payroll (PF/ESI/PT/TDS)",
      "Transport + GPS, library, visitor & gate",
      "Command-centre dashboards",
      "Named onboarding manager (₹19,999, waived on 2-year plans)",
    ],
  },
  {
    id: "global",
    name: "Global",
    for: "International schools (IB · Cambridge · AP)",
    price: "₹2,49,999",
    period: "per year",
    priceNote: "includes 500 students · ₹399 per extra student",
    founding: "25% off year 1 for founding schools",
    cta: { label: "Talk to us", href: "/contact?plan=global" },
    features: [
      "Everything in Standard, at advanced depth",
      "IB / Cambridge / AP assessment & transcripts",
      "Multi-currency billing",
      "University counselling, LMS, ECA, trips",
      "SSO (Google / Microsoft) & APIs",
      "Customer success manager",
    ],
  },
];

export const extraPlans = [
  {
    name: "Seva: Government & Trust schools",
    price: "₹9,999 per school / year",
    text: "Up to 1,000 students. Website, disclosures, attendance, Mid-Day Meal logging, scholarships and UDISE+ preparation. District deployments of 100+ schools: ₹6,999 per school.",
    status: "For government-aided and trust-run schools",
  },
  {
    name: "Enterprise & school groups",
    price: "Custom",
    text: "Multi-campus control, central finance, group brand with campus websites, dedicated hosting options and an SLA.",
    status: "Talk to us",
  },
];

export const pricingPromises = [
  { title: "Pay only for what's switched on", text: "Billing for each module starts on the day it goes live for your school, after setup, data import and training." },
  { title: "0% fee on online fee payments", text: "Parents pay the school through your own gateway. We never take a cut of your fees." },
  { title: "Messaging at cost + 10%", text: "WhatsApp and SMS from a prepaid wallet with caps and low-balance alerts. No surprise bills." },
  { title: "3-year price lock", text: `For the first ${FOUNDING_SEATS} founding schools, along with ${FOUNDING_DISCOUNT}% off the first year and free data migration.` },
];

export const pricingFaqs = [
  { q: "Are prices inclusive of GST?", a: "No. All prices are in Indian Rupees and exclude GST, which is added to the invoice at 18%." },
  { q: "When does billing start?", a: "Each module is billed from the day it is switched on for your school, after setup, data import and staff training. Your website can go live within a day." },
  { q: "How is the student count measured?", a: "Active enrolled students on the billing date. Alumni, withdrawn students and applicants are free. Net additions above 5% are trued up quarterly." },
  { q: "Can we start with the website and add modules later?", a: "Yes. That's how ShikshaBlox is designed. Your website, enquiries and data carry over; new modules are switched on, never re-installed or migrated." },
  { q: "Is there a free trial?", a: "Web Free is free forever. For ERP modules, we run a guided demo on a sample school with your own data scenarios." },
];
