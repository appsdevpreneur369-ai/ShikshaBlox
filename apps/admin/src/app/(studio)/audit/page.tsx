import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { api, currentUser } from "@/lib/session";

export const metadata: Metadata = { title: "Audit log" };

const dateFmt = new Intl.DateTimeFormat("en-IN", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Asia/Kolkata",
});

export default async function AuditPage({
  searchParams,
}: {
  searchParams: Promise<{ action?: string; before?: string }>;
}) {
  const session = await currentUser();
  if (!session) redirect("/login");
  if (!session.me.permissions?.includes("audit:view")) redirect("/dashboard");
  const { action, before } = await searchParams;

  const client = await api(session.accessToken);
  const { data = [] } = await client.GET("/api/admin/v1/audit", {
    params: {
      query: {
        action: action || undefined,
        beforeId: before ? Number(before) : undefined,
        limit: 50,
      },
    },
  });
  const last = data.at(-1);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Audit log</h1>
          <p className="text-ink-muted mt-1">
            Every change made in Studio, newest first. Entries cannot be edited.
          </p>
        </div>
        <form className="flex gap-2" role="search">
          <label htmlFor="action" className="sr-only">
            Filter by action
          </label>
          <input
            id="action"
            name="action"
            defaultValue={action}
            placeholder="e.g. media. or auth.login"
            className="focus-visible:ring-primary/30 h-10 w-56 rounded-full border border-black/10 bg-white px-4 text-sm outline-none focus-visible:ring-2"
          />
          <button className="bg-primary text-on-primary h-10 rounded-full px-4 text-sm font-semibold">
            Filter
          </button>
        </form>
      </div>

      <div className="overflow-x-auto rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">Audit entries</caption>
          <thead className="text-ink-muted border-b border-black/5 text-xs uppercase">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">
                When
              </th>
              <th scope="col" className="px-4 py-3 font-medium">
                Action
              </th>
              <th scope="col" className="px-4 py-3 font-medium">
                Item
              </th>
              <th scope="col" className="px-4 py-3 font-medium">
                Details
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-black/5">
            {data.map((e) => (
              <tr key={e.id} className="align-top transition hover:bg-indigo-50/40">
                <td className="px-4 py-3 whitespace-nowrap">
                  {e.occurredAt ? dateFmt.format(new Date(e.occurredAt)) : ""}
                </td>
                <td className="px-4 py-3">
                  <span className="rounded-full bg-indigo-50 px-2.5 py-1 font-mono text-xs text-indigo-800">
                    {e.action}
                  </span>
                </td>
                <td className="text-ink-muted px-4 py-3">
                  {e.entityType}
                  {e.entityId ? (
                    <span className="block font-mono text-xs">{e.entityId.slice(0, 36)}</span>
                  ) : null}
                </td>
                <td className="text-ink-muted max-w-md px-4 py-3 font-mono text-xs break-all">
                  {e.after ?? e.before ?? "—"}
                </td>
              </tr>
            ))}
            {data.length === 0 ? (
              <tr>
                <td colSpan={4} className="text-ink-muted px-4 py-10 text-center">
                  No entries match.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
      {data.length === 50 && last ? (
        <a
          className="text-primary text-sm font-medium hover:underline"
          href={`/audit?${new URLSearchParams({ ...(action ? { action } : {}), before: String(last.id) })}`}
        >
          Older entries →
        </a>
      ) : null}
    </div>
  );
}
