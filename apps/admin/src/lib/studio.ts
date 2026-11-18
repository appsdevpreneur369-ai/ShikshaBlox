/** Client-side calls to the admin API through the same-origin bridge (/api/backend/*). */

export class StudioError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

export async function sb<T = unknown>(
  path: string,
  init: { method?: string; body?: unknown; form?: FormData } = {},
): Promise<T> {
  const headers: Record<string, string> = { "X-Studio": "1" };
  let body: BodyInit | undefined;
  if (init.form) body = init.form;
  else if (init.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(init.body);
  }
  const res = await fetch(`/api/backend/${path.replace(/^\//, "")}`, {
    method: init.method ?? (body ? "POST" : "GET"),
    headers,
    body,
    cache: "no-store",
  });
  if (res.status === 401) {
    window.location.href = "/login";
    throw new StudioError("Your session has expired.", 401);
  }
  if (!res.ok) {
    const p = (await res.json().catch(() => ({}))) as { detail?: string };
    throw new StudioError(
      res.status === 403 && !p.detail ? "You do not have permission to do that." : (p.detail ?? "Something went wrong. Please try again."),
      res.status,
    );
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export type Media = {
  id: string;
  kind: "IMAGE" | "DOCUMENT" | "VIDEO";
  filename: string;
  url?: string;
  variants?: Record<string, { url: string; width: number; height: number }>;
  altText?: string;
  caption?: string;
  folder?: string;
  containsIdentifiableStudents: boolean;
  publishable: boolean;
  needsAltText: boolean;
  sizeBytes: number;
  createdAt: string;
};

export function thumb(m: Media | undefined): string | undefined {
  if (!m) return undefined;
  return m.variants?.thumb?.url ?? m.variants?.small?.url ?? m.variants?.medium?.url ?? m.url;
}

export const fmtDate = (iso?: string | null) =>
  iso
    ? new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit" }).format(new Date(iso))
    : "—";
