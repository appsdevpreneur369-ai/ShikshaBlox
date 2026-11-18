import { NextResponse, type NextRequest } from "next/server";
import { normaliseHost } from "@/lib/host";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

/**
 * Same-origin endpoint for the enquiry form: forwards to the backend with the school's host (the
 * backend resolves the tenant from it) and the visitor IP for rate limiting.
 */
export async function POST(request: NextRequest) {
  const host = normaliseHost(request.headers.get("x-forwarded-host") ?? request.headers.get("host"));
  if (!host) return NextResponse.json({ message: "Bad request" }, { status: 400 });
  const body = await request.text();
  if (body.length > 10_000) return NextResponse.json({ message: "Too large" }, { status: 413 });
  const ip = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "";
  try {
    const res = await fetch(`${API}/api/public/v1/enquiries?host=${encodeURIComponent(host)}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Client-IP": ip },
      body,
      cache: "no-store",
    });
    const text = await res.text();
    return new NextResponse(text || "{}", {
      status: res.status,
      headers: { "Content-Type": "application/json" },
    });
  } catch {
    return NextResponse.json({ message: "The school could not be reached. Please try again." }, { status: 502 });
  }
}
