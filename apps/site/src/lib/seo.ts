import "server-only";
import type { Block } from "@shikshablox/blocks";
import type { MediaMap, PublicSite } from "./types";

/** Public origin of a school site, e.g. https://moriah.example.org (scheme from env in dev). */
export function siteOrigin(host: string): string {
  const proto =
    process.env.SB_SITE_PROTOCOL ??
    (process.env.NODE_ENV === "production" && !host.endsWith(".localhost") ? "https" : "http");
  return `${proto}://${host}`;
}

export function absoluteUrl(host: string, path: string): string {
  const p = path.replace(/^\/+/, "");
  return p ? `${siteOrigin(host)}/${p}` : `${siteOrigin(host)}/`;
}

const TEXT_KEYS = ["subheading", "intro", "text", "body", "html", "description", "summary", "content"];

function strip(s: string): string {
  return s
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/\s+/g, " ")
    .trim();
}

export function clip(s: string, max = 158): string {
  if (s.length <= max) return s;
  const cut = s.slice(0, max);
  return cut.slice(0, cut.lastIndexOf(" ") > 80 ? cut.lastIndexOf(" ") : max).replace(/[,;:.\s]+$/, "") + "…";
}

/** A meta description from the first meaningful text in a page's blocks. */
export function describe(blocks: Block[], fallback: string): string {
  for (const b of blocks) {
    const props = (b.props ?? {}) as Record<string, unknown>;
    for (const k of TEXT_KEYS) {
      const v = props[k];
      if (typeof v === "string") {
        const s = strip(v);
        if (s.length >= 50) return clip(s);
      }
    }
  }
  return clip(fallback);
}

/** First image used by a page (hero, then any block), for social sharing cards. */
export function firstImage(blocks: Block[], media: MediaMap): string | undefined {
  for (const b of blocks) {
    const props = (b.props ?? {}) as Record<string, unknown>;
    for (const k of ["imageId", "backgroundImageId", "image"]) {
      const v = props[k];
      if (typeof v === "string" && media[v]?.src) return media[v]!.src;
    }
  }
  return undefined;
}

/** Pads a short description with the school's name and town so it reads well in search results. */
export function enrich(desc: string, site: PublicSite): string {
  if (desc.length >= 90) return clip(desc);
  const city = site.profile.address?.city;
  const tail = `${site.name}${city ? `, ${city}` : ""}`;
  const base = desc.replace(/[.\s]+$/, "");
  const extra = site.profile.tagline && !base.includes(site.profile.tagline) ? ` ${site.profile.tagline}.` : "";
  return clip(`${base} — ${tail}.${extra}`);
}

export function siteDescription(site: PublicSite): string {
  const city = site.profile.address?.city;
  return (
    site.profile.tagline ||
    `${site.name}${city ? `, ${city}` : ""} — admissions, academics, facilities, news and events.`
  );
}

/** schema.org School markup for the site (rendered once per page in the layout). */
export function schoolJsonLd(site: PublicSite, host: string) {
  const a = site.profile.address;
  const logo = site.logoId ? site.media[site.logoId]?.src : undefined;
  const sameAs = Object.values(site.profile.social ?? {}).filter(Boolean);
  return {
    "@context": "https://schema.org",
    "@type": "School",
    name: site.name,
    url: absoluteUrl(host, ""),
    description: siteDescription(site),
    ...(logo ? { logo, image: logo } : {}),
    ...(site.profile.phone ? { telephone: site.profile.phone } : {}),
    ...(site.profile.email ? { email: site.profile.email } : {}),
    ...(a
      ? {
          address: {
            "@type": "PostalAddress",
            streetAddress: [a.line1, a.line2].filter(Boolean).join(", ") || undefined,
            addressLocality: a.city,
            addressRegion: a.state,
            postalCode: a.pincode,
            addressCountry: a.country || "IN",
          },
        }
      : {}),
    ...(sameAs.length ? { sameAs } : {}),
  };
}
