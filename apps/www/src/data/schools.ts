/**
 * The demo school shown on shikshablox.com. Greenfield Demo School is FICTIONAL: a real ShikshaBlox
 * tenant (slug "greenfield", seeded from backend/src/main/resources/seed/greenfield) with stock
 * photos, so prospects can click through a complete school website without us showing any real
 * school. Add real customers here only with the school's written permission.
 */
export type DemoSchool = {
  slug: string;
  name: string;
  tagline: string;
  logo: { src: string; w: number; h: number };
  siteUrl: string;
  summary: string;
  highlights: string[];
  tour: { title: string; text: string; path: string; shot: string }[];
};

export const demoSchool: DemoSchool = {
  slug: "greenfield",
  name: "Greenfield Demo School",
  tagline: "A sample CBSE school, Nursery to Class 12",
  logo: { src: "/schools/greenfield-logo.webp", w: 629, h: 200 },
  siteUrl: process.env.SB_WWW_DEMO_URL || "https://greenfield-914241661718.asia-south1.run.app",
  summary:
    "A complete school website running on ShikshaBlox: 14 pages, notice board, events calendar, news, photo albums, staff, FAQs and an admissions enquiry form that lands in the school's Studio inbox.",
  highlights: ["14 ready pages", "Notices & events", "Photo albums", "Admissions enquiries", "Mobile-first"],
  tour: [
    { title: "Homepage", text: "Hero, key facts, why families choose the school, notices, gallery, events and news, all managed in Studio.", path: "/", shot: "/product/demo-desktop.webp" },
    { title: "Facilities", text: "Every facility with photos, laid out with ready-made blocks; no designer needed.", path: "/facilities", shot: "/product/demo-facilities-desktop.webp" },
    { title: "Gallery", text: "Albums and a masonry gallery. Student photos need a consent declaration before they go public.", path: "/gallery", shot: "/product/demo-gallery-desktop.webp" },
  ],
};
