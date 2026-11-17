/**
 * Host-name handling for the multi-tenant public site.
 *
 * The browser's Host header is only used as a lookup key: the backend resolves it to a tenant
 * (subdomain of the platform base domain, or a verified custom domain). We normalise and validate
 * it here so that a malformed Host can never become an ISR cache key or reach the backend.
 */

const HOST_PATTERN = /^(?=.{1,253}$)(?!-)[a-z0-9-]{1,63}(?<!-)(\.(?!-)[a-z0-9-]{1,63}(?<!-))*$/;

export function normaliseHost(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const host = raw.trim().toLowerCase().replace(/:\d+$/, "").replace(/\.$/, "");
  return HOST_PATTERN.test(host) ? host : null;
}

/** Returns the tenant subdomain for platform hosts (e.g. "moriah"), or null for custom domains. */
export function platformSubdomain(host: string, baseDomain: string): string | null {
  const suffix = `.${baseDomain.toLowerCase()}`;
  if (!host.endsWith(suffix)) return null;
  const sub = host.slice(0, -suffix.length);
  return sub.length > 0 && !sub.includes(".") ? sub : null;
}

/** Hosts that belong to the platform itself and never render a school site. */
export function isReservedSubdomain(sub: string): boolean {
  return ["www", "admin", "api", "media", "mail", "app", "static", "cdn"].includes(sub);
}
