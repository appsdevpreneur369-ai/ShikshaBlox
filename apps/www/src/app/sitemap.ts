import type { MetadataRoute } from "next";
import { allPaths } from "@/data/routes";
import { site } from "@/data/site";

export default function sitemap(): MetadataRoute.Sitemap {
  const lastModified = new Date("2026-11-26");
  return allPaths.map((p) => ({
    url: `${site.url}${p === "/" ? "" : p}`,
    lastModified,
    changeFrequency: p === "/" || p === "/roadmap" ? "weekly" : "monthly",
    priority: p === "/" ? 1 : p.startsWith("/features") || p === "/pricing" ? 0.8 : 0.6,
  }));
}
