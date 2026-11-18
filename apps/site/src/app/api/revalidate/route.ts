import { timingSafeEqual } from "node:crypto";
import { revalidatePath, revalidateTag } from "next/cache";
import { NextResponse, type NextRequest } from "next/server";

/**
 * On-demand ISR, called by the backend after content is published:
 * `POST { tags: ["host:<host>", ...] }` with the shared `X-Revalidate-Secret` header.
 */
export async function POST(request: NextRequest) {
  const secret = process.env.SITE_REVALIDATE_SECRET ?? "";
  const given = request.headers.get("x-revalidate-secret") ?? "";
  const ok =
    secret.length >= 16 &&
    given.length === secret.length &&
    timingSafeEqual(Buffer.from(given), Buffer.from(secret));
  if (!ok) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const body = (await request.json().catch(() => null)) as { tags?: unknown } | null;
  const tags = Array.isArray(body?.tags)
    ? body.tags.filter((t): t is string => typeof t === "string" && /^host:[a-z0-9.-]{1,253}$/.test(t)).slice(0, 50)
    : [];
  for (const tag of tags) {
    revalidateTag(tag, { expire: 0 });
    // Also drop every cached route of that school (and the fetches made while rendering them),
    // so the very next visit renders fresh data instead of serving one stale render first.
    revalidatePath(`/h/${tag.slice("host:".length)}`, "layout");
  }
  return NextResponse.json({ revalidated: tags.length });
}
