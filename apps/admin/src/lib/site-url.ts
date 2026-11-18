import "server-only";

/** Public website address of a school (platform subdomain). */
export function siteUrl(slug: string | undefined): string | null {
  if (!slug) return null;
  const base = process.env.SB_BASE_DOMAIN ?? "shikshablox.localhost";
  const proto = process.env.SB_SITE_PROTOCOL ?? (process.env.NODE_ENV === "production" ? "https" : "http");
  return `${proto}://${slug}.${base}`;
}
