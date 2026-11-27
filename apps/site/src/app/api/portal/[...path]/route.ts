import { NextResponse, type NextRequest } from "next/server";
import { normaliseHost } from "@/lib/host";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
const COOKIE = "sb_parent";
const READS = new Set([
  "me",
  "circulars",
  "requests",
  "ptm",
  "preferences",
  "attendance",
  "leave",
  "report-cards",
  "certificates",
  "homework",
  "timetable",
]);
const HW_FILE = /^homework\/files\/[0-9a-f-]{36}$/;
const CERT = /^certificates\/[0-9a-f-]{36}$/;
const LEAVE_CANCEL = /^leave\/[0-9a-f-]{36}\/cancel$/;

/**
 * Same-origin bridge for the parent portal. Sign-in responses set the session token in an
 * httpOnly cookie (never exposed to page scripts); other calls send it to the API as a header.
 */
async function handle(request: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  const host = normaliseHost(
    request.headers.get("x-forwarded-host") ?? request.headers.get("host"),
  );
  const action = path.join("/");
  const method = request.method;
  const allowed =
    (method === "GET" && (READS.has(action) || CERT.test(action) || HW_FILE.test(action))) ||
    (method === "PUT" && action === "preferences") ||
    (method === "POST" &&
      (action === "login" ||
        action === "setup" ||
        action === "logout" ||
        action === "leave" ||
        action === "certificates" ||
        LEAVE_CANCEL.test(action)));
  if (!host || !allowed) return NextResponse.json({ detail: "Not found" }, { status: 404 });
  if (method !== "GET" && request.headers.get("x-portal") !== "1") {
    // Cross-site forms can't set custom headers (CSRF protection on top of SameSite cookies).
    return NextResponse.json({ detail: "Forbidden" }, { status: 403 });
  }
  const body = method === "GET" ? undefined : (await request.text()) || "{}";
  if (body && body.length > 2000)
    return NextResponse.json({ detail: "Too large" }, { status: 413 });
  const token = request.cookies.get(COOKIE)?.value;
  const ip = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "";
  let res: Response;
  try {
    res = await fetch(`${API}/api/public/v1/portal/${action}?host=${encodeURIComponent(host)}`, {
      method,
      headers: {
        "Content-Type": "application/json",
        "X-Client-IP": ip,
        ...(token ? { "X-Parent-Session": token } : {}),
      },
      body,
      cache: "no-store",
    });
  } catch {
    return NextResponse.json(
      { detail: "The school could not be reached. Please try again." },
      { status: 502 },
    );
  }
  const text = res.status === 204 ? "" : await res.text();
  const secure = process.env.NODE_ENV === "production";
  if ((action === "login" || action === "setup") && res.ok) {
    const s = JSON.parse(text) as { token: string; expiresAt: string };
    const out = NextResponse.json({ ok: true }, { headers: { "Cache-Control": "no-store" } });
    out.cookies.set(COOKIE, s.token, {
      httpOnly: true,
      secure,
      sameSite: "lax",
      path: "/",
      expires: new Date(s.expiresAt),
    });
    return out;
  }
  const out = new NextResponse(text || null, {
    status: res.status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
  if (action === "logout" || res.status === 401)
    out.cookies.set(COOKIE, "", { httpOnly: true, secure, sameSite: "lax", path: "/", maxAge: 0 });
  return out;
}

export { handle as GET, handle as POST, handle as PUT };
