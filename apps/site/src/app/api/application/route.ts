import { NextResponse, type NextRequest } from "next/server";
import { normaliseHost } from "@/lib/host";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
/** Cloud Run caps requests at 32 MB; the form keeps uploads well below this. */
const MAX_BYTES = 30 * 1024 * 1024;

/**
 * Same-origin endpoint for the online application form (multipart with documents): forwards to
 * the backend with the school's host and the visitor IP for rate limiting.
 */
export async function POST(request: NextRequest) {
  const host = normaliseHost(
    request.headers.get("x-forwarded-host") ?? request.headers.get("host"),
  );
  const type = request.headers.get("content-type") ?? "";
  if (!host || !type.startsWith("multipart/form-data")) {
    return NextResponse.json({ message: "Bad request" }, { status: 400 });
  }
  if (Number(request.headers.get("content-length") ?? 0) > MAX_BYTES) {
    return NextResponse.json(
      { detail: "The files are too large. Please upload smaller copies." },
      { status: 413 },
    );
  }
  const body = await request.arrayBuffer();
  if (body.byteLength > MAX_BYTES) {
    return NextResponse.json(
      { detail: "The files are too large. Please upload smaller copies." },
      { status: 413 },
    );
  }
  const ip = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "";
  try {
    const res = await fetch(`${API}/api/public/v1/applications?host=${encodeURIComponent(host)}`, {
      method: "POST",
      headers: { "Content-Type": type, "X-Client-IP": ip },
      body,
      cache: "no-store",
    });
    const text = await res.text();
    return new NextResponse(text || "{}", {
      status: res.status,
      headers: { "Content-Type": "application/json" },
    });
  } catch {
    return NextResponse.json(
      { detail: "The school could not be reached. Please try again." },
      { status: 502 },
    );
  }
}
