import { modulePages } from "./modules";
import { profiles } from "./profiles";

export const staticPaths = [
  "/",
  "/features",
  "/pricing",
  "/schools",
  "/roadmap",
  "/why-shikshablox",
  "/security",
  "/about",
  "/contact",
  "/privacy",
  "/terms",
];

/** Every public page (used by the sitemap and the e2e suite). */
export const allPaths = [
  ...staticPaths,
  ...modulePages.map((m) => `/features/${m.slug}`),
  ...profiles.map((p) => `/solutions/${p.slug}`),
];
