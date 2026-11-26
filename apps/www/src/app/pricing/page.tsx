import { Icon } from "@/components/Icon";
import { ButtonLink, CtaBand, FaqList, JsonLd, PageHero, SectionHeading } from "@/components/ui";
import { extraPlans, FOUNDING_DISCOUNT, FOUNDING_SEATS, plans, pricingFaqs, pricingPromises } from "@/data/plans";
import { breadcrumbLd, faqLd, pageMetadata, softwareLd } from "@/lib/seo";

export const metadata = pageMetadata({
  title: "School ERP Pricing: Transparent Plans from ₹0",
  description:
    "Published School ERP pricing in INR: Web Free ₹0, Web Pro ₹7,999/yr, Lite ₹19,999/yr, Standard ₹59,999/yr and Global plans. 0% fee on online fee payments; pay only for live modules.",
  path: "/pricing",
});

const crumbs = [
  { name: "Home", path: "/" },
  { name: "Pricing", path: "/pricing" },
];

const addOns = [
  ["Admissions CRM (for Lite)", "₹9,999 / campus / yr"],
  ["Timetable & substitutions (for Lite)", "₹6,999 / campus / yr"],
  ["HR & payroll (for Lite)", "₹600 / staff / yr"],
  ["Transport (routes, attendant app)", "₹1,200 / vehicle / yr"],
  ["GPS live tracking (software)", "₹2,400 / vehicle / yr"],
  ["Library · Visitor & gate", "₹4,999 / campus / yr each"],
  ["Hostel & boarding", "₹99 / boarder / yr"],
  ["White-label parent app (Android + iOS)", "₹24,999 one-time + ₹14,999 / yr"],
  ["Extra storage", "₹2,999 per 100 GB / yr"],
  ["Additional report-card format", "₹7,500 each"],
];

export default function PricingPage() {
  return (
    <>
      <JsonLd
        data={[
          breadcrumbLd(crumbs),
          faqLd(pricingFaqs),
          softwareLd([
            { name: "Web Free", price: 0 },
            { name: "Web Pro", price: 7999 },
            { name: "Lite", price: 19999 },
            { name: "Standard", price: 59999 },
            { name: "Global", price: 249999 },
          ]),
        ]}
      />
      <PageHero
        crumbs={crumbs}
        eyebrow={<p className="eyebrow">Transparent pricing</p>}
        title="Simple, published prices. No surprises."
        lead={`Start free with a school website and add ERP modules as you grow. Founding schools get ${FOUNDING_DISCOUNT}% off year 1 and a 3-year price lock (first ${FOUNDING_SEATS} schools). All prices in INR, excluding GST.`}
      />

      <section className="container-x py-16 sm:py-20">
        <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-5">
          {plans.map((p) => (
            <article
              key={p.id}
              id={p.id}
              className={`card relative flex flex-col p-6 ${p.highlight ? "ring-2 ring-brand xl:-my-3 xl:py-9" : ""}`}
            >
              {p.highlight && (
                <span className="absolute -top-3 left-6 rounded-full bg-brand px-3 py-1 text-xs font-bold text-brand-ink">Most popular today</span>
              )}
              <h2 className="font-display text-xl font-bold text-ink">{p.name}</h2>
              <p className="mt-1 min-h-10 text-sm text-ink-3">{p.for}</p>
              <p className="mt-4">
                <span className="font-display text-[2rem] leading-none font-extrabold text-ink">{p.price}</span>{" "}
                <span className="text-sm text-ink-3">{p.period}</span>
              </p>
              <p className="mt-1.5 min-h-10 text-xs text-ink-3">{p.priceNote}</p>
              {p.founding && <p className="mt-2 text-sm font-semibold text-brand">{p.founding}</p>}
              <ButtonLink href={p.cta.href} variant={p.highlight ? "primary" : "secondary"} className="mt-5 w-full">
                {p.cta.label}
              </ButtonLink>
              <ul className="mt-6 space-y-2.5 text-sm text-ink-2">
                {p.features.map((f) => (
                  <li key={f} className="flex gap-2">
                    <Icon name="check" className="mt-0.5 size-4 shrink-0 text-brand" strokeWidth={2.4} />
                    {f}
                  </li>
                ))}
              </ul>
            </article>
          ))}
        </div>

        <div className="mt-8 grid gap-5 md:grid-cols-2">
          {extraPlans.map((e) => (
            <article key={e.name} className="card flex flex-col gap-3 p-6 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <h2 className="font-display text-lg font-bold text-ink">{e.name}</h2>
                <p className="mt-2 text-sm leading-relaxed text-ink-2">{e.text}</p>
                <p className="mt-2 text-xs font-semibold text-ink-3">{e.status}</p>
              </div>
              <p className="shrink-0 font-display text-lg font-extrabold text-brand">{e.price}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="bg-bg-soft py-16 sm:py-20">
        <div className="container-x">
          <SectionHeading eyebrow="Our promises" title="Pricing that's fair to schools" center />
          <ul className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {pricingPromises.map((p) => (
              <li key={p.title} className="card p-6">
                <span className="grid size-10 place-items-center rounded-xl bg-live-soft text-live">
                  <Icon name="check" className="size-5" strokeWidth={2.6} />
                </span>
                <h3 className="mt-4 font-semibold text-ink">{p.title}</h3>
                <p className="mt-1.5 text-sm leading-relaxed text-ink-2">{p.text}</p>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className="container-x grid gap-10 py-16 sm:py-20 lg:grid-cols-2">
        <div>
          <SectionHeading title="Add-on modules" lead="For plans that don't include them. Available as each module goes live." />
          <dl className="mt-8 divide-y divide-line overflow-hidden rounded-2xl border border-line bg-card">
            {addOns.map(([k, v]) => (
              <div key={k} className="flex flex-col gap-1 p-4 sm:flex-row sm:items-center sm:justify-between sm:gap-6 sm:px-5">
                <dt className="text-ink-2">{k}</dt>
                <dd className="shrink-0 font-semibold text-ink">{v}</dd>
              </div>
            ))}
          </dl>
        </div>
        <div>
          <SectionHeading title="Usage, at cost" lead="Pass-through charges are billed from a prepaid wallet, with budget caps and monthly statements." />
          <dl className="mt-8 divide-y divide-line overflow-hidden rounded-2xl border border-line bg-card">
            {[
              ["WhatsApp messages", "Meta/BSP cost + 10%"],
              ["SMS (DLT)", "Provider cost + 10%"],
              ["Email", "Free up to 10× students / month"],
              ["Online fee payments", "0% ShikshaBlox fee"],
              ["GPS SIM / data", "At cost"],
            ].map(([k, v]) => (
              <div key={k} className="flex flex-col gap-1 p-4 sm:flex-row sm:items-center sm:justify-between sm:gap-6 sm:px-5">
                <dt className="text-ink-2">{k}</dt>
                <dd className="shrink-0 font-semibold text-ink">{v}</dd>
              </div>
            ))}
          </dl>
          <div className="mt-10">
            <h2 className="font-display text-2xl font-extrabold text-ink">Pricing FAQ</h2>
            <div className="mt-5">
              <FaqList items={pricingFaqs} />
            </div>
          </div>
        </div>
      </section>
      <CtaBand title="Not sure which plan fits?" text="Tell us about your school and we'll recommend the smallest plan that does the job, and show you the numbers." />
    </>
  );
}
