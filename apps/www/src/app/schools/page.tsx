import Image from "next/image";
import { Icon } from "@/components/Icon";
import { BrowserFrame, ButtonLink, CtaBand, JsonLd, PageHero, PhoneFrame, SectionHeading } from "@/components/ui";
import { schools } from "@/data/schools";
import { breadcrumbLd, pageMetadata } from "@/lib/seo";

export const metadata = pageMetadata({
  title: "Schools on ShikshaBlox: Customer Stories",
  description:
    "See schools running on ShikshaBlox, including Moriah School, Guntur, whose modern, mobile-first school website is built and managed on ShikshaBlox.",
  path: "/schools",
});

const crumbs = [
  { name: "Home", path: "/" },
  { name: "Schools", path: "/schools" },
];

export default function SchoolsPage() {
  return (
    <>
      <JsonLd data={breadcrumbLd(crumbs)} />
      <PageHero
        crumbs={crumbs}
        eyebrow={<p className="eyebrow">Schools on ShikshaBlox</p>}
        title="Real schools, real websites, live today"
        lead="Every ShikshaBlox school gets its own branded website and Studio on one shared, secure platform. Here's who's already live."
      />

      {schools.map((s) => (
        <article key={s.slug} className="container-x py-16 sm:py-20" aria-labelledby={`school-${s.slug}`}>
          <div className="grid items-center gap-12 lg:grid-cols-[0.95fr_1.05fr]">
            <div>
              <Image src={s.logo.src} alt={`${s.name} logo`} width={s.logo.w} height={s.logo.h} className="h-16 w-auto" />
              <h2 id={`school-${s.slug}`} className="mt-6 font-display text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">
                {s.name}
              </h2>
              <p className="mt-2 inline-flex items-center gap-1.5 text-ink-3">
                <Icon name="pin" className="size-4" /> {s.location} · {s.plan} · since {s.since}
              </p>
              <p className="mt-5 text-lg leading-relaxed text-ink-2">{s.summary}</p>
              <dl className="mt-8 grid grid-cols-3 gap-4">
                {s.facts.map((f) => (
                  <div key={f.label} className="card flex flex-col-reverse p-4 text-center">
                    <dt className="mt-1 text-xs text-ink-3 sm:text-sm">{f.label}</dt>
                    <dd className="font-display text-2xl font-extrabold text-brand sm:text-3xl">{f.value}</dd>
                  </div>
                ))}
              </dl>
              <div className="mt-8 flex flex-wrap gap-3">
                <ButtonLink href={s.siteUrl} external>
                  Visit {s.name}&apos;s website <Icon name="external" className="size-4" />
                </ButtonLink>
              </div>
            </div>
            <div className="relative pb-10">
              <BrowserFrame src={s.screenshots.desktop} alt={`${s.name} homepage`} url={`${s.name} · website`} sizes="(min-width: 1024px) 600px, 92vw" priority />
              <PhoneFrame src={s.screenshots.mobile} alt={`${s.name} homepage on a phone`} className="absolute right-2 -bottom-2 w-[28%] max-w-[170px]" />
            </div>
          </div>

          <div className="mt-16 grid gap-8 lg:grid-cols-3">
            <div className="card p-7">
              <h3 className="font-display text-lg font-bold text-ink">The starting point</h3>
              <p className="mt-2 leading-relaxed text-ink-2">
                An older, donation-focused website that was hard to update and didn&apos;t show parents what mattered: academics,
                admissions, facilities and school life.
              </p>
            </div>
            <div className="card p-7">
              <h3 className="font-display text-lg font-bold text-ink">What we built</h3>
              <ul className="mt-2 space-y-2 text-ink-2">
                {s.liveModules.map((m) => (
                  <li key={m} className="flex gap-2">
                    <Icon name="check" className="mt-0.5 size-4 shrink-0 text-live" strokeWidth={2.6} />
                    {m}
                  </li>
                ))}
              </ul>
            </div>
            <div className="card p-7">
              <h3 className="font-display text-lg font-bold text-ink">How it runs today</h3>
              <p className="mt-2 leading-relaxed text-ink-2">
                The school&apos;s office edits pages, posts notices and answers admission enquiries in ShikshaBlox Studio. Changes
                go through review before they&apos;re published, and every version can be rolled back.
              </p>
            </div>
          </div>
        </article>
      ))}

      <section className="bg-bg-soft py-16 sm:py-20">
        <div className="container-x">
          <SectionHeading
            eyebrow="Founding schools"
            title="Become one of our first 25 founding schools"
            lead="Founding schools get 25% off the first year, a 3-year price lock, free data migration and a direct line to the team building the product, in return for honest feedback and, if you're happy, a case study like this one."
            center
          />
          <div className="mt-8 flex justify-center">
            <ButtonLink href="/contact" arrow>
              Apply as a founding school
            </ButtonLink>
          </div>
        </div>
      </section>
      <CtaBand />
    </>
  );
}
