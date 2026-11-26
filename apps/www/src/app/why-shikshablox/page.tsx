import { Icon } from "@/components/Icon";
import { ArchitectureDiagram } from "@/components/visuals";
import { ButtonLink, CtaBand, JsonLd, PageHero, Photo, SectionHeading } from "@/components/ui";
import { breadcrumbLd, pageMetadata } from "@/lib/seo";

export const metadata = pageMetadata({
  title: "Why ShikshaBlox: A Modern Alternative to Legacy School ERPs",
  description:
    "Why schools choose ShikshaBlox over legacy school ERPs: composable modules, a built-in website, WhatsApp-first communication, board-ready report cards, transparent pricing and DPDP-aware privacy.",
  path: "/why-shikshablox",
});

const crumbs = [
  { name: "Home", path: "/" },
  { name: "Why ShikshaBlox", path: "/why-shikshablox" },
];

const reasons = [
  { icon: "layers", title: "Grows without migrating", text: "Start with a website, add the ERP later. Same data, same login: modules switch on through entitlements, never re-installs." },
  { icon: "users", title: "Right-sized for your school", text: "Simple mode for a 150-student school, advanced depth for an IB campus, all on one codebase, so nobody outgrows it." },
  { icon: "globe", title: "Website included, not bolted on", text: "Your website, notices, events and admissions forms share one source of truth. Post once, publish everywhere." },
  { icon: "chat", title: "WhatsApp-first communication", text: "Approved templates, opt-in, two-way inbox and delivery tracking, with SMS and email fallback." },
  { icon: "award", title: "Board-ready, not hard-coded", text: "Versioned curriculum packs for CBSE (incl. HPC), ICSE/ISC, State Boards, IB, Cambridge and AP." },
  { icon: "shield", title: "Privacy by design", text: "Row-level data isolation, audit trails, consent records and data export, built for India's DPDP Act." },
  { icon: "wallet", title: "Honest pricing", text: "Published prices, 0% fee on online fee payments, messaging at cost + 10%, and no charge for modules that aren't live." },
  { icon: "sparkles", title: "AI with a human in charge", text: "Opt-in co-pilots that draft and summarise, with citations, approvals and an audit log. Never a black box." },
] as const;

export default function WhyPage() {
  return (
    <>
      <JsonLd data={breadcrumbLd(crumbs)} />
      <PageHero
        crumbs={crumbs}
        eyebrow={<p className="eyebrow">Why ShikshaBlox</p>}
        title="The school platform that grows from your first website to your fifth campus"
        lead="Most school ERPs were designed a decade ago for one kind of school. ShikshaBlox is built fresh, mobile-first and modular, for how Indian and international schools run today."
        aside={
          <div className="overflow-hidden rounded-3xl border border-line shadow-pop">
            <Photo src="/images/modern-classroom.webp" alt="Students in a modern classroom" sizes="(min-width: 1024px) 560px, 92vw" priority className="aspect-[4/3] h-full w-full" />
          </div>
        }
      >
        <ButtonLink href="/contact" arrow>
          Book a free demo
        </ButtonLink>
      </PageHero>

      <section className="container-x py-16 sm:py-24">
        <SectionHeading eyebrow="Eight reasons" title="What makes ShikshaBlox different" center />
        <ul className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {reasons.map((r) => (
            <li key={r.title} className="card card-hover reveal p-6">
              <span className="grid size-11 place-items-center rounded-xl bg-brand-soft text-brand">
                <Icon name={r.icon} />
              </span>
              <h3 className="mt-4 font-display text-lg font-bold text-ink">{r.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-ink-2">{r.text}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="bg-bg-soft py-16 sm:py-24">
        <div className="container-x">
          <SectionHeading
            eyebrow="Under the hood"
            title="How ShikshaBlox works"
            lead="One core platform serves every school. Each school gets its own website, Studio and parent channels, and its data is isolated at the database level."
          />
          <div className="mt-10">
            <ArchitectureDiagram />
          </div>
        </div>
      </section>

      <section className="container-x py-16 sm:py-24">
        <div className="grid gap-10 lg:grid-cols-2">
          <div>
            <SectionHeading title="Switching from another ERP?" lead="We make moving painless." />
            <ul className="mt-6 space-y-3 text-ink-2">
              {[
                "Free migration from Excel and common ERP exports (founding schools)",
                "Fee balances reconciled and signed off before go-live",
                "Run both systems in parallel for a cycle if you prefer",
                "Training for office, accounts and teachers included",
              ].map((x) => (
                <li key={x} className="flex gap-3">
                  <Icon name="check" className="mt-0.5 size-5 shrink-0 text-live" strokeWidth={2.6} />
                  {x}
                </li>
              ))}
            </ul>
          </div>
          <div className="card p-7">
            <h2 className="font-display text-xl font-bold text-ink">Year-1 cost for a 1,000-student school</h2>
            <p className="mt-1 text-sm text-ink-3">Illustrative planning estimate, excluding GST and messaging</p>
            <dl className="mt-6 space-y-3">
              <div className="flex items-center justify-between gap-4 rounded-xl bg-bg-soft p-4">
                <dt className="text-ink-2">Typical legacy ERP (subscription, migration, training, app, formats)</dt>
                <dd className="shrink-0 font-display text-lg font-extrabold text-ink">₹2.2L–₹7.2L</dd>
              </div>
              <div className="flex items-center justify-between gap-4 rounded-xl bg-brand-soft p-4">
                <dt className="font-semibold text-ink">ShikshaBlox Standard (incl. onboarding)</dt>
                <dd className="shrink-0 font-display text-lg font-extrabold text-brand">≈ ₹1.2L</dd>
              </div>
            </dl>
            <p className="mt-4 text-xs text-ink-3">Legacy ranges are third-party estimates; ask us for a quote for your school.</p>
          </div>
        </div>
      </section>
      <CtaBand />
    </>
  );
}
