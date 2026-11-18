import { cookies } from "next/headers";
import { NextResponse, type NextRequest } from "next/server";
import { COOKIE } from "@/lib/session";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
const SEGMENT = /^[A-Za-z0-9._-]{1,120}$/;

/**
 * Same-origin bridge from Studio client components to the admin API (`/api/admin/v1/*` only).
 * The access token stays in its httpOnly cookie. Writes must carry `X-Studio: 1`, which a
 * cross-site form or image cannot send (CSRF protection on top of SameSite=Strict cookies).
 */
async function handle(request: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  if (!path.length || path.some((s) => !SEGMENT.test(s) || s === "..")) {
    return NextResponse.json({ detail: "Not found" }, { status: 404 });
  }
  if (request.method !== "GET" && request.headers.get("x-studio") !== "1") {
    return NextResponse.json({ detail: "Forbidden" }, { status: 403 });
  }
  const token = (await cookies()).get(COOKIE.access)?.value;
  if (!token) return NextResponse.json({ detail: "Your session has expired. Please sign in again." }, { status: 401 });

  const url = `${API}/api/admin/v1/${path.join("/")}${request.nextUrl.search}`;
  const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
  const ct = request.headers.get("content-type");
  if (ct) headers["Content-Type"] = ct;
  const xff = request.headers.get("x-forwarded-for");
  if (xff) headers["X-Forwarded-For"] = xff;

  const hasBody = !["GET", "HEAD"].includes(request.method);
  const res = await fetch(url, {
    method: request.method,
    headers,
    body: hasBody ? await request.arrayBuffer() : undefined,
    cache: "no-store",
  });
  const out = new NextResponse(res.status === 204 ? null : await res.arrayBuffer(), { status: res.status });
  const rct = res.headers.get("content-type");
  if (rct) out.headers.set("Content-Type", rct);
  return out;
}

export { handle as GET, handle as POST, handle as PUT, handle as PATCH, handle as DELETE };
