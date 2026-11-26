import { NextResponse, type NextRequest } from "next/server";
import { normaliseHost } from "@/lib/host";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
const TOKEN = /^[A-Za-z0-9_-]{16,64}$/;

/**
 * Same-origin bridge for personal circular links: GET marks the circular read (called from the
 * browser, so WhatsApp link previews don't count), POST ack / stop. Never cached.
 */
async function handle(request: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  const host = normaliseHost(
    request.headers.get("x-forwarded-host") ?? request.headers.get("host"),
  );
  const [token, action] = path;
  const ok =
    !!host &&
    !!token &&
    TOKEN.test(token) &&
    path.length <= 2 &&
    (request.method === "GET" ? action === undefined : action === "ack" || action === "stop");
  if (!ok) return NextResponse.json({ detail: "Not found" }, { status: 404 });
  const body = request.method === "POST" ? (await request.text()) || "{}" : undefined;
  if (body && body.length > 2000)
    return NextResponse.json({ detail: "Too large" }, { status: 413 });
  try {
    const res = await fetch(
      `${API}/api/public/v1/comms/circulars/${token}${action ? `/${action}` : ""}?host=${encodeURIComponent(host)}`,
      {
        method: request.method,
        headers: { "Content-Type": "application/json" },
        body,
        cache: "no-store",
      },
    );
    return new NextResponse(await res.text(), {
      status: res.status,
      headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
    });
  } catch {
    return NextResponse.json(
      { detail: "The school could not be reached. Please try again." },
      { status: 502 },
    );
  }
}

export { handle as GET, handle as POST };
