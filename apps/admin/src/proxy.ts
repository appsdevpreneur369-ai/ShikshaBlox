import { NextResponse, type NextRequest } from "next/server";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
const ACCESS = "sb_at";
const REFRESH = "sb_rt";

const secure = () =>
  process.env.NODE_ENV === "production" && process.env.SB_SECURE_COOKIES !== "false";

/**
 * Keeps studio sessions alive: when the short-lived access cookie has expired but a refresh
 * cookie exists, rotate the tokens before the page renders. Unauthenticated visitors to studio
 * pages are sent to the sign-in screen.
 */
export async function proxy(request: NextRequest) {
  const access = request.cookies.get(ACCESS)?.value;
  const refresh = request.cookies.get(REFRESH)?.value;
  if (access) return NextResponse.next();
  if (!refresh) return NextResponse.redirect(new URL("/login", request.url));

  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const xff = request.headers.get("x-forwarded-for");
  if (xff) headers["X-Forwarded-For"] = xff;
  const res = await fetch(`${API}/api/auth/v1/refresh`, {
    method: "POST",
    headers,
    body: JSON.stringify({ refreshToken: refresh }),
    cache: "no-store",
  }).catch(() => null);

  if (!res?.ok) {
    const out = NextResponse.redirect(new URL("/login", request.url));
    out.cookies.delete(ACCESS);
    out.cookies.delete(REFRESH);
    return out;
  }
  const t = (await res.json()) as {
    accessToken: string;
    accessTokenExpiresAt: string;
    refreshToken: string;
    refreshTokenExpiresAt: string;
  };

  // Make the new access token visible to this request's server components…
  request.cookies.set(ACCESS, t.accessToken);
  const out = NextResponse.next({ request: { headers: request.headers } });
  // …and to the browser for later requests.
  const base = { httpOnly: true, sameSite: "strict" as const, secure: secure(), path: "/" };
  out.cookies.set(ACCESS, t.accessToken, { ...base, expires: new Date(t.accessTokenExpiresAt) });
  out.cookies.set(REFRESH, t.refreshToken, { ...base, expires: new Date(t.refreshTokenExpiresAt) });
  return out;
}

export const config = {
  // Studio pages only; sign-in pages, health and static assets are public.
  matcher: ["/dashboard/:path*"],
};
