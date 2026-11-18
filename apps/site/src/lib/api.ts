import "server-only";
import type { components } from "@shikshablox/api-client";
import type { Feed, PublicPage, PublicSite } from "./types";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

export type ResolvedSite = Required<components["schemas"]["ResolvedSite"]>;

/**
 * Every fetch for a host carries the `host:<host>` tag, so one on-demand revalidation call from
 * the backend (after publishing) refreshes the whole school site. The timer is the safety net.
 */
function opts(host: string) {
  return { next: { revalidate: 300, tags: [`host:${host}`] } };
}

async function getJson<T>(url: string, host: string): Promise<T | null> {
  const res = await fetch(url, opts(host));
  if (res.status === 404) return null;
  // Other failures throw, so ISR keeps serving the last good page instead of caching an error.
  if (!res.ok) throw new Error(`Site API ${res.status} for ${url}`);
  return (await res.json()) as T;
}

/** The school serving `host`, or null when no active school owns it. */
export async function resolveSite(host: string): Promise<ResolvedSite | null> {
  const data = await getJson<components["schemas"]["ResolvedSite"]>(
    `${API}/api/public/v1/sites/resolve?host=${encodeURIComponent(host)}`,
    host,
  );
  return data?.slug && data.name ? { slug: data.slug, name: data.name } : null;
}

export function getSite(host: string): Promise<PublicSite | null> {
  return getJson<PublicSite>(`${API}/api/public/v1/site?host=${encodeURIComponent(host)}`, host);
}

export function getPage(host: string, path: string, lang?: string): Promise<PublicPage | null> {
  const q = new URLSearchParams({ host, path });
  if (lang) q.set("lang", lang);
  return getJson<PublicPage>(`${API}/api/public/v1/site/page?${q}`, host);
}

export async function getPaths(host: string): Promise<{ path: string; updatedAt: string }[]> {
  return (
    (await getJson<{ path: string; updatedAt: string }[]>(
      `${API}/api/public/v1/site/paths?host=${encodeURIComponent(host)}`,
      host,
    )) ?? []
  );
}

/** A content feed; an empty feed when the content type has nothing published (or is unavailable). */
export async function getFeed(
  host: string,
  type: "notices" | "news" | "events" | "albums" | "staff" | "careers" | "faqs",
  params: Record<string, string | number | undefined> = {},
): Promise<Feed> {
  const q = new URLSearchParams({ host });
  for (const [k, v] of Object.entries(params)) if (v !== undefined) q.set(k, String(v));
  try {
    return (
      (await getJson<Feed>(`${API}/api/public/v1/site/feeds/${type}?${q}`, host)) ?? {
        items: [],
        media: {},
      }
    );
  } catch {
    return { items: [], media: {} };
  }
}

export async function getFeedItem(
  host: string,
  type: "news" | "events" | "albums" | "notices",
  slug: string,
): Promise<Feed | null> {
  const q = new URLSearchParams({ host });
  return getJson<Feed>(
    `${API}/api/public/v1/site/feeds/${type}/${encodeURIComponent(slug)}?${q}`,
    host,
  );
}

/** A draft page for a signed preview token (never cached). */
export async function getPreview(host: string, token: string): Promise<PublicPage | null> {
  const q = new URLSearchParams({ host, token });
  const res = await fetch(`${API}/api/public/v1/site/preview?${q}`, { cache: "no-store" });
  if (!res.ok) return null;
  return (await res.json()) as PublicPage;
}
