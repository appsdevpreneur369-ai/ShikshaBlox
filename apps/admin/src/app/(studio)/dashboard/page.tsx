import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Building2, ShieldCheck, ShieldAlert, Globe } from "lucide-react";
import { api, currentUser } from "@/lib/session";
import { switchSchool } from "../actions";
import { siteUrl } from "@/lib/site-url";

export const metadata: Metadata = { title: "Dashboard" };

const host = (slug?: string) => (siteUrl(slug) ?? "").replace(/^https?:\/\//, "");

export default async function Dashboard() {
  const session = await currentUser();
  if (!session) redirect("/login");
  const { me, accessToken } = session;

  // Platform admins can enter any school; others see the schools they belong to.
  let schools = (me.memberships ?? []).map((m) => ({
    id: m.organizationId!,
    name: m.name!,
    slug: m.slug!,
  }));
  if (me.platformAdmin) {
    const client = await api(accessToken);
    const { data } = await client.GET("/api/platform/v1/organizations");
    schools = (data ?? []).map((o) => ({ id: o.id!, name: o.name!, slug: o.slug! }));
  }
  const active = me.activeOrganization;

  return (
    <div className="space-y-8">
      <section>
        <h1 className="text-3xl font-bold tracking-tight">
          Welcome, {me.displayName?.split(" ")[0]}
        </h1>
        <p className="text-ink-muted mt-1">
          {active
            ? `You are working on ${active.name}.`
            : "Choose a school to start working on its website."}
        </p>
      </section>

      <div className="grid gap-5 md:grid-cols-3">
        <Card icon={<Building2 className="size-5" aria-hidden />} title="Active school">
          {active ? (
            <>
              <p className="text-lg font-semibold">{active.name}</p>
              <a
                className="text-primary mt-1 inline-flex items-center gap-1 text-sm font-medium hover:underline"
                href={siteUrl(active.slug) ?? "#"}
                target="_blank"
                rel="noreferrer"
              >
                <Globe className="size-4" aria-hidden /> {host(active.slug)}
              </a>
            </>
          ) : (
            <p className="text-ink-muted text-sm">None selected</p>
          )}
        </Card>
        <Card icon={<ShieldCheck className="size-5" aria-hidden />} title="Your access">
          <ul className="flex flex-wrap gap-2">
            {(me.permissions ?? []).map((p) => (
              <li
                key={p}
                className="rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-medium text-indigo-800"
              >
                {p}
              </li>
            ))}
          </ul>
        </Card>
        <Card
          icon={
            me.mfaEnabled ? (
              <ShieldCheck className="size-5" aria-hidden />
            ) : (
              <ShieldAlert className="size-5" aria-hidden />
            )
          }
          title="Two-step verification"
        >
          <p
            className={
              me.mfaEnabled ? "font-semibold text-emerald-700" : "font-semibold text-amber-700"
            }
          >
            {me.mfaEnabled ? "On" : "Off"}
          </p>
          <p className="text-ink-muted text-sm">
            {me.mfaEnabled
              ? "Your account asks for an authenticator code."
              : "Recommended for school administrators."}
          </p>
        </Card>
      </div>

      {schools.length > 0 ? (
        <section aria-labelledby="schools-heading">
          <h2 id="schools-heading" className="text-xl font-semibold">
            {me.platformAdmin ? "All schools" : "Your schools"}
          </h2>
          <ul className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {schools.map((s) => {
              const isActive = s.id === active?.organizationId;
              return (
                <li key={s.id}>
                  <form action={switchSchool}>
                    <input type="hidden" name="organizationId" value={s.id} />
                    <button
                      type="submit"
                      disabled={isActive}
                      aria-current={isActive ? "true" : undefined}
                      className="group hover:ring-primary/40 w-full rounded-2xl bg-white p-5 text-left shadow-sm ring-1 ring-black/5 transition duration-200 hover:-translate-y-0.5 hover:shadow-md disabled:cursor-default disabled:ring-2 disabled:ring-indigo-500 motion-reduce:transition-none motion-reduce:hover:translate-y-0"
                    >
                      <p className="font-semibold">{s.name}</p>
                      <p className="text-ink-muted text-sm">
                        {host(s.slug)}
                      </p>
                      <p className="text-primary mt-3 text-sm font-medium">
                        {isActive ? "Active" : "Switch to this school →"}
                      </p>
                    </button>
                  </form>
                </li>
              );
            })}
          </ul>
        </section>
      ) : null}
    </div>
  );
}

function Card({
  icon,
  title,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-black/5 transition hover:shadow-md">
      <div className="text-ink-muted flex items-center gap-2 text-sm font-medium">
        <span className="text-primary grid size-8 place-items-center rounded-lg bg-indigo-50">
          {icon}
        </span>
        {title}
      </div>
      <div className="mt-3">{children}</div>
    </div>
  );
}
