import "server-only";
import type { components } from "@shikshablox/api-client";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

export type ResolvedSite = Required<components["schemas"]["ResolvedSite"]>;

/** The school serving `host`, or null when no active school owns it (cached for ISR). */
export async function resolveSite(host: string): Promise<ResolvedSite | null> {
  const res = await fetch(`${API}/api/public/v1/sites/resolve?host=${encodeURIComponent(host)}`, {
    next: { revalidate: 60, tags: [`site:${host}`] },
  });
  if (res.status === 404) return null;
  // Other failures throw, so ISR keeps serving the last good page instead of caching an error.
  if (!res.ok) throw new Error(`Site lookup failed: ${res.status}`);
  const data = (await res.json()) as components["schemas"]["ResolvedSite"];
  return data.slug && data.name ? { slug: data.slug, name: data.name } : null;
}
