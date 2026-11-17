import { NextResponse, type NextRequest } from "next/server";
import { isReservedSubdomain, normaliseHost, platformSubdomain } from "@/lib/host";

const BASE_DOMAIN = process.env.SB_BASE_DOMAIN ?? "shikshablox.localhost";

/**
 * Multi-tenant routing: every public request is rewritten to `/h/<host>/<path>` so that ISR
 * caches each school's pages separately. The host is validated here and resolved to a tenant by
 * the backend; no tenant id ever comes from the browser.
 */
export function proxy(request: NextRequest) {
  const host = normaliseHost(
    request.headers.get("x-forwarded-host") ?? request.headers.get("host"),
  );
  if (!host) {
    return new NextResponse("Bad Request", { status: 400 });
  }
  const sub = platformSubdomain(host, BASE_DOMAIN);
  if (sub && isReservedSubdomain(sub)) {
    return new NextResponse("Not Found", { status: 404 });
  }

  const url = request.nextUrl.clone();
  url.pathname = `/h/${host}${request.nextUrl.pathname === "/" ? "" : request.nextUrl.pathname}`;
  return NextResponse.rewrite(url);
}

export const config = {
  // Skip Next internals, static files and the health endpoint.
  matcher: ["/((?!_next/|api/health|favicon.ico|robots.txt|sitemap.xml|.*\\.[a-z0-9]+$).*)"],
};
