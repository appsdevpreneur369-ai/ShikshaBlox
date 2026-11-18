import "server-only";

/**
 * Public website address of a school. SB_SITE_URLS overrides per school
 * ("moriah=https://moriah-site.example.run.app,greenfield=https://..."), used on staging where
 * schools don't have platform subdomains yet; otherwise <slug>.<base domain>.
 */
export function siteUrl(slug: string | undefined): string | null {
  if (!slug) return null;
  for (const pair of (process.env.SB_SITE_URLS ?? "").split(",")) {
    const [k, v] = pair.split("=").map((s) => s?.trim());
    if (k === slug && v) return v.replace(/\/+$/, "");
  }
  const base = process.env.SB_BASE_DOMAIN ?? "shikshablox.localhost";
  const proto = process.env.SB_SITE_PROTOCOL ?? (process.env.NODE_ENV === "production" ? "https" : "http");
  return `${proto}://${slug}.${base}`;
}
