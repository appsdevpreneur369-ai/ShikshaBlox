import Link from "next/link";
import { Icon } from "@/components/Icon";
import { ButtonLink, CtaBand, JsonLd, PageHero, SectionHeading } from "@/components/ui";
import { allModules, catalogueGroups } from "@/data/modules";
import { breadcrumbLd, pageMetadata } from "@/lib/seo";

export const metadata = pageMetadata({
  title: "School ERP Modules: Website, Admissions, Fees, Attendance, Exams & More",
  description: `All ${allModules.length} ShikshaBlox modules in one place, covering school website & CMS, admissions CRM, SIS, attendance, fees, exams, parent app, WhatsApp, transport, HR and more.`,
  path: "/features",
});

const crumbs = [
  { name: "Home", path: "/" },
  { name: "Features", path: "/features" },
];

const anchor = (t: string) => t.toLowerCase().replace(/[^a-z]+/g, "-");

const foundations: [string, string, string][] = [
  ["lock", "Roles & permissions", "Each person sees only what their role allows."],
  ["shield", "Audit trail", "Every change logged: who, what, when."],
  ["check", "Consent ledger", "DPDP-aware consent for photos and messages."],
  ["cloud", "Backups & export", "Daily backups; export your data any time."],
  ["smartphone", "Mobile-first", "Works on any phone, even on slow networks."],
  ["globe", "Multi-language ready", "English first, with Indian languages built in."],
  ["layers", "Entitlements", "Switch modules on or off without migration."],
  ["building", "Multi-tenant core", "Every school's data isolated in the database."],
];

export default function FeaturesPage() {
  return (
    <>
      <JsonLd data={breadcrumbLd(crumbs)} />
      <PageHero
        crumbs={crumbs}
        eyebrow={<p className="eyebrow">{allModules.length} modules · one platform</p>}
        title="Every module a school needs, as blocks you switch on"
        lead="Start with what you need today and switch on more as you grow: same data, same login, no migrations."
      >
        <ButtonLink href="/contact" arrow>
          Book a free demo
        </ButtonLink>
        <ButtonLink href="/pricing" variant="secondary">
          See pricing
        </ButtonLink>
      </PageHero>

      <nav aria-label="Module groups" className="sticky top-[4.25rem] z-30 border-b border-line bg-bg/90 backdrop-blur">
        <ul className="container-x marquee-mask flex gap-2 overflow-x-auto py-3 [scrollbar-width:none]">
          {catalogueGroups.map((g) => (
            <li key={g.title} className="shrink-0">
              <a
                href={`#${anchor(g.title)}`}
                className="inline-flex min-h-10 items-center rounded-full border border-line bg-card px-4 text-sm font-medium text-ink-2 hover:border-brand hover:text-brand"
              >
                {g.title}
              </a>
            </li>
          ))}
        </ul>
      </nav>

      <div className="container-x space-y-20 py-16 sm:py-24">
        {catalogueGroups.map((g) => (
          <section key={g.title} id={anchor(g.title)} aria-labelledby={`h-${anchor(g.title)}`} className="scroll-mt-36">
            <h2 id={`h-${anchor(g.title)}`} className="font-display text-2xl font-extrabold tracking-tight text-ink sm:text-3xl">
              {g.title}
            </h2>
            <p className="mt-1 text-ink-2">{g.blurb}</p>
            <ul className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {g.modules.map((m) => {
                const inner = (
                  <>
                    <div className="flex items-start justify-between gap-3">
                      <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-brand-soft text-brand">
                        <Icon name={m.icon} />
                      </span>
                      <span className="text-xs font-semibold text-ink-3">{m.id}</span>
                    </div>
                    <h3 className="mt-4 font-display text-lg font-bold text-ink">{m.name}</h3>
                    <p className="mt-1.5 flex-1 text-sm leading-relaxed text-ink-2">{m.summary}</p>
                    <div className="mt-4 flex flex-wrap items-center justify-end gap-2">
                      {m.page && (
                        <span className="inline-flex items-center gap-1 text-sm font-semibold text-brand">
                          Details <Icon name="arrow" className="size-4" />
                        </span>
                      )}
                    </div>
                  </>
                );
                return (
                  <li key={m.id} className="reveal">
                    {m.page ? (
                      <Link prefetch={false} href={`/features/${m.page}`} className="card card-hover flex h-full flex-col p-6">
                        {inner}
                      </Link>
                    ) : (
                      <div className="card flex h-full flex-col p-6">{inner}</div>
                    )}
                  </li>
                );
              })}
            </ul>
          </section>
        ))}
        <section className="rounded-3xl bg-bg-tint p-6 sm:p-12">
          <SectionHeading
            title="Platform foundations in every plan"
            lead="Built once, shared by every module: no add-on fees for the basics that keep children's data safe."
          />
          <ul className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {foundations.map(([i, t, d]) => (
              <li key={t} className="card p-5">
                <Icon name={i as "lock"} className="size-6 text-brand" />
                <h3 className="mt-3 font-semibold text-ink">{t}</h3>
                <p className="mt-1 text-sm text-ink-2">{d}</p>
              </li>
            ))}
          </ul>
        </section>
      </div>
      <CtaBand />
    </>
  );
}
