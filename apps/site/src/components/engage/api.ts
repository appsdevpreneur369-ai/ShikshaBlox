/** Client calls to the same-origin engagement bridge (/api/engage/*). */
export type Result<T> = { ok: true; data: T } | { ok: false; status: number; message: string };

export async function engage<T>(path: string, body?: unknown): Promise<Result<T>> {
  try {
    const res = await fetch(`/api/engage/${path}`, {
      method: body === undefined ? "GET" : "POST",
      headers: body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
    });
    if (res.ok)
      return {
        ok: true,
        data: (res.status === 204 ? undefined : await res.json().catch(() => undefined)) as T,
      };
    const p = (await res.json().catch(() => ({}))) as { detail?: string; message?: string };
    const message =
      res.status === 429
        ? "Too many attempts. Please wait a few minutes and try again."
        : res.status === 404 && !p.detail
          ? "Not found."
          : (p.detail ?? p.message ?? "Something went wrong. Please try again.");
    return { ok: false, status: res.status, message };
  } catch {
    return {
      ok: false,
      status: 0,
      message: "We could not reach the school. Please check your connection.",
    };
  }
}

const ist = (o: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", ...o });
export const fmtDate = (d: string) =>
  ist({ weekday: "long", day: "numeric", month: "long", year: "numeric" }).format(
    new Date(`${d}T00:00:00+05:30`),
  );
export const fmtTime = (t: string) => {
  const [h, m] = t.split(":").map(Number);
  const d = new Date(2000, 0, 1, h, m);
  return new Intl.DateTimeFormat("en-IN", { hour: "numeric", minute: "2-digit" }).format(d);
};
export const fmtWhen = (iso: string) =>
  ist({ day: "numeric", month: "short", hour: "numeric", minute: "2-digit" }).format(new Date(iso));
