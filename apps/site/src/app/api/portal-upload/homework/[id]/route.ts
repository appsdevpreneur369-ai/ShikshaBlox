import { NextResponse, type NextRequest } from "next/server";
import { normaliseHost } from "@/lib/host";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
const MAX_BYTES = 9 * 1024 * 1024;

/**
 * Hands in a child's homework from the parent portal (multipart: studentId, text, file). The
 * session token stays in the httpOnly cookie and is passed to the API as a header.
 */
export async function POST(request: NextRequest, ctx: { params: Promise<{ id: string }> }) {
  const { id } = await ctx.params;
  const host = normaliseHost(
    request.headers.get("x-forwarded-host") ?? request.headers.get("host"),
  );
  const type = request.headers.get("content-type") ?? "";
  if (!host || !/^[0-9a-f-]{36}$/.test(id) || !type.startsWith("multipart/form-data"))
    return NextResponse.json({ detail: "Bad request" }, { status: 400 });
  if (request.headers.get("x-portal") !== "1")
    return NextResponse.json({ detail: "Forbidden" }, { status: 403 });
  const body = await request.arrayBuffer();
  if (body.byteLength > MAX_BYTES)
    return NextResponse.json({ detail: "The file is too large (8 MB at most)." }, { status: 413 });
  const token = request.cookies.get("sb_parent")?.value;
  const form = new URL(request.url).searchParams;
  const studentId = form.get("studentId") ?? "";
  if (!/^[0-9a-f-]{36}$/.test(studentId))
    return NextResponse.json({ detail: "Bad request" }, { status: 400 });
  try {
    const res = await fetch(
      `${API}/api/public/v1/portal/homework/${id}/submit?host=${encodeURIComponent(host)}&studentId=${studentId}`,
      {
        method: "POST",
        headers: { "Content-Type": type, ...(token ? { "X-Parent-Session": token } : {}) },
        body,
        cache: "no-store",
      },
    );
    return new NextResponse((await res.text()) || "{}", {
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
