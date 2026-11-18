import { normaliseHost } from "@/lib/host";
import { getSite } from "@/lib/api";
import { siteOrigin } from "@/lib/seo";

/** Per-school robots.txt (the proxy does not rewrite this path, so the host comes from headers). */
export async function GET(request: Request) {
  const host = normaliseHost(request.headers.get("x-forwarded-host") ?? request.headers.get("host"));
  const site = host ? await getSite(host).catch(() => null) : null;
  const body =
    host && site
      ? `User-agent: *\nAllow: /\nDisallow: /preview/\nDisallow: /api/\n\nSitemap: ${siteOrigin(host)}/sitemap.xml\n`
      : "User-agent: *\nDisallow: /\n";
  return new Response(body, {
    headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "public, max-age=3600" },
  });
}
