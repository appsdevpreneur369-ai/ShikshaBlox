import { normaliseHost } from "@/lib/host";
import { getFeed, getPaths, getSite } from "@/lib/api";
import { absoluteUrl } from "@/lib/seo";

const esc = (s: string) =>
  s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

/** Per-school sitemap: published pages plus news, event and album detail pages. */
export async function GET(request: Request) {
  const host = normaliseHost(request.headers.get("x-forwarded-host") ?? request.headers.get("host"));
  const site = host ? await getSite(host).catch(() => null) : null;
  if (!host || !site) return new Response("Not Found", { status: 404 });

  const entries: { loc: string; lastmod?: string; priority: string }[] = [];
  for (const p of await getPaths(host)) {
    const path = p.path.replace(/^\/+/, "");
    entries.push({ loc: absoluteUrl(host, path), lastmod: p.updatedAt, priority: path ? "0.8" : "1.0" });
  }
  const sections = { news: "news", events: "events", albums: "gallery" } as const;
  for (const type of ["news", "events", "albums"] as const) {
    const feed = await getFeed(host, type, { limit: 200 }).catch(() => null);
    for (const it of feed?.items ?? []) {
      if (it.slug) entries.push({ loc: absoluteUrl(host, `${sections[type]}/${it.slug}`), priority: "0.6" });
    }
  }

  const xml =
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n` +
    entries
      .map(
        (e) =>
          `  <url><loc>${esc(e.loc)}</loc>${e.lastmod ? `<lastmod>${esc(e.lastmod.slice(0, 10))}</lastmod>` : ""}<priority>${e.priority}</priority></url>`,
      )
      .join("\n") +
    `\n</urlset>\n`;
  return new Response(xml, {
    headers: { "Content-Type": "application/xml; charset=utf-8", "Cache-Control": "public, max-age=3600" },
  });
}
