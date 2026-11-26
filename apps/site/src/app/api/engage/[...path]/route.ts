import { NextResponse, type NextRequest } from "next/server";
import { normaliseHost } from "@/lib/host";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
const SEGMENT = /^[A-Za-z0-9-]{1,80}$/;
/** Only these public engagement endpoints are reachable through this route. */
const ALLOWED = [
  /^ptm$/,
  /^ptm\/[0-9a-f-]{36}\/book$/,
  /^ptm\/cancel$/,
  /^helpdesk$/,
  /^helpdesk\/(lookup|reply)$/,
  /^surveys\/[a-z0-9-]{3,60}$/,
];

/**
 * Same-origin bridge for PTM booking, the parent helpdesk and surveys: forwards to the backend with
 * the school's host (tenant) and the visitor IP (rate limits). Never cached.
 */
async function handle(request: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  const host = normaliseHost(
    request.headers.get("x-forwarded-host") ?? request.headers.get("host"),
  );
  const joined = path.join("/");
  if (!host || path.some((s) => !SEGMENT.test(s)) || !ALLOWED.some((r) => r.test(joined))) {
    return NextResponse.json({ detail: "Not found" }, { status: 404 });
  }
  const body = request.method === "POST" ? await request.text() : undefined;
  if (body && body.length > 20_000)
    return NextResponse.json({ detail: "Too large" }, { status: 413 });
  const ip = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "";
  try {
    const res = await fetch(
      `${API}/api/public/v1/engagement/${joined}?host=${encodeURIComponent(host)}`,
      {
        method: request.method,
        headers: { "Content-Type": "application/json", "X-Client-IP": ip },
        body,
        cache: "no-store",
      },
    );
    const text = await res.text();
    return new NextResponse(res.status === 204 ? null : text || "{}", {
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
