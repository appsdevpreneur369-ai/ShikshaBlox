import { Icon } from "@/components/Icon";
import { RoadmapTimeline } from "@/components/visuals";
import { ButtonLink, CtaBand, JsonLd, PageHero, SectionHeading, StatusBadge } from "@/components/ui";
import { catalogueGroups, releases, type Release } from "@/data/modules";
import { breadcrumbLd, pageMetadata } from "@/lib/seo";

export const metadata = pageMetadata({
  title: "Product Roadmap: What Ships When",
  description:
    "The ShikshaBlox roadmap with target dates: School Website & CMS live now; Lite ERP (students, attendance, fees, report cards, WhatsApp) Q2 2027; Standard Q4 2027; Global Q3 2028.",
  path: "/roadmap",
});

const crumbs = [
  { name: "Home", path: "/" },
  { name: "Roadmap", path: "/roadmap" },
];

export default function RoadmapPage() {
  const order = Object.keys(releases) as Release[];
  const byRelease = order.map((r) => ({
    r,
    modules: catalogueGroups.flatMap((g) => g.modules).filter((m) => m.release === r),
  }));
  return (
    <>
      <JsonLd data={breadcrumbLd(crumbs)} />
      <PageHero
        crumbs={crumbs}
        eyebrow={<p className="eyebrow">Published roadmap</p>}
        title="What ships, and when"
        lead="We publish our roadmap because schools plan a year ahead. Dates are targets, refined with our pilot schools; a module ships only when it passes our release gate."
      >
        <ButtonLink href="/contact?plan=lite" arrow>
          Become a pilot school
        </ButtonLink>
      </PageHero>

      <section className="container-x py-16 sm:py-20">
        <RoadmapTimeline />
      </section>

      <section className="bg-bg-soft py-16 sm:py-20">
        <div className="container-x">
          <SectionHeading title="Every module, by release" lead="Some modules are partly live already (for example the enquiry inbox ahead of the full Admissions CRM)." />
          <div className="mt-10 grid gap-5 lg:grid-cols-2">
            {byRelease.map(({ r, modules }) => (
              <section key={r} className="card p-6" aria-labelledby={`rel-${r}`}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h3 id={`rel-${r}`} className="font-display text-lg font-bold text-ink">
                    {r}: {releases[r].name}
                  </h3>
                  <StatusBadge release={r} />
                </div>
                <ul className="mt-4 flex flex-wrap gap-2">
                  {modules.map((m) => (
                    <li key={m.id} className="inline-flex items-center gap-1.5 rounded-full border border-line px-3 py-1.5 text-sm text-ink-2">
                      <Icon name={m.icon} className="size-4 text-brand" />
                      {m.name}
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        </div>
      </section>

      <section className="container-x py-16 sm:py-20">
        <SectionHeading eyebrow="Release gate" title="Nothing ships until it's safe for children's data" />
        <ul className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[
            "Role & permission tests",
            "Tenant-isolation tests",
            "Audit coverage",
            "Data export & migration",
            "Backup & restore drill",
            "Monitoring & alerts",
            "User documentation",
            "Pilot school sign-off",
          ].map((g) => (
            <li key={g} className="card flex items-center gap-3 p-5 font-medium text-ink">
              <span className="grid size-8 shrink-0 place-items-center rounded-full bg-live-soft text-live">
                <Icon name="check" className="size-4" strokeWidth={3} />
              </span>
              {g}
            </li>
          ))}
        </ul>
        <p className="mt-8 max-w-3xl text-ink-2">
          <strong className="text-ink">How we estimate:</strong> each release is planned at 10–24 weeks of build time followed by a 4–6
          week pilot with 2–3 schools. The full catalogue takes about 24 months. A pilot school that sponsors a module can move it up by
          one release.
        </p>
      </section>
      <CtaBand title="Shape what we build next" text="Pilot schools get early access, founding prices and a direct say in the roadmap." />
    </>
  );
}
