import { NextResponse, type NextRequest } from "next/server";
import { toEnquiry, validateDemoRequest, type DemoRequest } from "@/lib/demo-request";

export const dynamic = "force-dynamic";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
/** Host of the internal tenant whose Studio inbox receives demo requests (resolved by the backend). */
const SALES_HOST = process.env.SB_SALES_HOST ?? "sb-sales.shikshablox.localhost";

/**
 * Same-origin endpoint for the demo form: validates, then forwards to the platform's public enquiry
 * API as the internal sales tenant. The browser never talks to the API or names a tenant.
 */
export async function POST(request: NextRequest) {
  const raw = await request.text();
  if (raw.length > 8_000) return NextResponse.json({ message: "Too large" }, { status: 413 });
  let body: DemoRequest;
  try {
    body = JSON.parse(raw) as DemoRequest;
  } catch {
    return NextResponse.json({ message: "Please fill in the form." }, { status: 400 });
  }
  const errors = validateDemoRequest(body);
  if (Object.keys(errors).length > 0) {
    return NextResponse.json({ message: "Please check the highlighted fields.", errors }, { status: 400 });
  }
  const referer = request.headers.get("referer") ?? "/contact";
  const ip = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "";
  try {
    const res = await fetch(`${API}/api/public/v1/enquiries?host=${encodeURIComponent(SALES_HOST)}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Client-IP": ip },
      body: JSON.stringify(toEnquiry(body, referer)),
      cache: "no-store",
      signal: AbortSignal.timeout(10_000),
    });
    if (res.ok) return NextResponse.json({ status: "received" }, { status: 202 });
    if (res.status === 429) {
      return NextResponse.json({ message: "Too many requests. Please try again in a few minutes." }, { status: 429 });
    }
    const data = (await res.json().catch(() => ({}))) as { message?: string };
    console.error("demo-request rejected", res.status, data.message);
    return NextResponse.json(
      { message: res.status === 400 && data.message ? data.message : "We couldn't send your request. Please try again." },
      { status: res.status === 400 ? 400 : 502 },
    );
  } catch (e) {
    console.error("demo-request failed", e);
    return NextResponse.json({ message: "We couldn't reach our server. Please try again." }, { status: 502 });
  }
}
