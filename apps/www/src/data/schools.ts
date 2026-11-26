/**
 * Schools running on ShikshaBlox. Add one entry per school (with the school's permission to be
 * listed); the /schools page and the homepage showcase render from this list.
 */
export type School = {
  slug: string;
  name: string;
  location: string;
  logo: { src: string; w: number; h: number };
  since: string;
  liveModules: string[];
  plan: string;
  siteUrl: string;
  summary: string;
  facts: { label: string; value: string }[];
  screenshots: { desktop: string; mobile: string };
};

export const schools: School[] = [
  {
    slug: "moriah",
    name: "Moriah School",
    location: "Guntur, Andhra Pradesh",
    logo: { src: "/schools/moriah-logo.webp", w: 400, h: 127 },
    since: "November 2026",
    liveModules: ["School Website & CMS", "Enquiry inbox", "Notices, events & gallery", "Mandatory disclosures"],
    plan: "Web Pro",
    siteUrl: process.env.SB_WWW_MORIAH_URL || "https://moriah-914241661718.asia-south1.run.app",
    summary:
      "An English-medium school for Pre-KG to Class 10, with Moriah Home, serving Guntur since 1999. Moriah moved from an old donation-focused website to a modern, mobile-first school website that its office team updates in ShikshaBlox Studio.",
    facts: [
      { label: "Pages live", value: "14+" },
      { label: "Curated photos", value: "30+" },
      { label: "Time to launch", value: "2 days" },
    ],
    screenshots: { desktop: "/product/moriah-desktop.webp", mobile: "/product/moriah-mobile.webp" },
  },
];

/** The school featured on the homepage. */
export const featuredSchool: School = schools[0] as School;
