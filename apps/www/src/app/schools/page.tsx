import Image from "next/image";
import { Icon } from "@/components/Icon";
import { BrowserFrame, ButtonLink, CtaBand, JsonLd, PageHero, PhoneFrame, SectionHeading } from "@/components/ui";
import { demoSchool as s } from "@/data/schools";
import { breadcrumbLd, pageMetadata } from "@/lib/seo";

export const metadata = pageMetadata({
  title: "Demo School Website: See ShikshaBlox in Action",
  description:
    "Click through Greenfield Demo School, a complete sample school website built on ShikshaBlox: homepage, facilities, gallery, notices, events and an admissions enquiry form.",
  path: "/schools",
});

const crumbs = [
  { name: "Home", path: "/" },
  { name: "Demo school", path: "/schools" },
];

export default function SchoolsPage() {
  return (
    <>
      <JsonLd data={breadcrumbLd(crumbs)} />
      <PageHero
        crumbs={crumbs}
        eyebrow={<p className="eyebrow">Demo school</p>}
        title="See exactly what your school gets"
        lead="Greenfield Demo School is a fictional school we built on ShikshaBlox, so you can click through a complete school website before you decide."
      >
        <ButtonLink href={s.siteUrl} external>
          Open the demo website <Icon name="external" className="size-4" />
        </ButtonLink>
        <ButtonLink href="/contact" variant="secondary">
          Book a free demo
        </ButtonLink>
      </PageHero>

      <section className="container-x py-16 sm:py-20" aria-labelledby="demo-school">
        <div className="grid items-center gap-12 lg:grid-cols-[0.95fr_1.05fr]">
          <div>
            <Image src={s.logo.src} alt={`${s.name} logo`} width={s.logo.w} height={s.logo.h} className="h-16 w-auto" />
            <h2 id="demo-school" className="mt-6 font-display text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">
              {s.name}
            </h2>
            <p className="mt-2 text-ink-3">{s.tagline}</p>
            <p className="mt-5 text-lg leading-relaxed text-ink-2">{s.summary}</p>
            <ul className="mt-6 flex flex-wrap gap-2">
              {s.highlights.map((h) => (
                <li key={h} className="rounded-full bg-brand-soft px-3 py-1 text-sm font-semibold text-brand">
                  {h}
                </li>
              ))}
            </ul>
            <p className="mt-6 rounded-2xl bg-bg-tint p-4 text-sm leading-relaxed text-ink-2">
              <strong className="text-ink">About this demo:</strong> the school, its staff and its news are sample content, and the
              photos are licensed stock images. Enquiries sent from the demo website go to our demo inbox.
            </p>
          </div>
          <div className="relative pb-10">
            <BrowserFrame src="/product/demo-desktop.webp" alt={`${s.name} homepage`} url="greenfield.shikshablox.com" sizes="(min-width: 1024px) 600px, 92vw" priority />
            <PhoneFrame src="/product/demo-mobile.webp" alt={`${s.name} homepage on a phone`} className="absolute right-2 -bottom-2 w-[28%] max-w-[170px]" />
          </div>
        </div>
      </section>

      <section className="bg-bg-soft py-16 sm:py-20">
        <div className="container-x">
          <SectionHeading eyebrow="Guided tour" title="Three pages worth a look" />
          <div className="mt-12 grid gap-8 lg:grid-cols-3">
            {s.tour.map((t) => (
              <article key={t.title} className="card card-hover reveal flex flex-col overflow-hidden">
                <div className="bg-bg-tint p-4">
                  <BrowserFrame src={t.shot} alt={`${t.title} page of the demo school website`} url={`${t.title} · ${s.name}`} sizes="(min-width: 1024px) 360px, 92vw" />
                </div>
                <div className="flex flex-1 flex-col p-6">
                  <h3 className="font-display text-lg font-bold text-ink">{t.title}</h3>
                  <p className="mt-2 flex-1 leading-relaxed text-ink-2">{t.text}</p>
                  <a
                    href={`${s.siteUrl}${t.path}`}
                    target="_blank"
                    rel="noopener"
                    className="mt-4 inline-flex min-h-11 items-center gap-1.5 font-semibold text-brand hover:underline"
                  >
                    Open {t.title.toLowerCase()} <Icon name="external" className="size-4" />
                  </a>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="container-x py-16 sm:py-20">
        <div className="grid gap-8 lg:grid-cols-3">
          {[
            ["pencil", "Edited in Studio", "Office staff update pages, post notices and answer enquiries in ShikshaBlox Studio, with review before anything is published."],
            ["smartphone", "Fast on every phone", "Server-rendered, image-optimised pages that load quickly on 4G and look right on any screen."],
            ["funnel", "Enquiries never lost", "Every admission enquiry lands in the Studio inbox with status and notes, ready for your admissions team."],
          ].map(([icon, title, text]) => (
            <div key={title} className="card p-7">
              <span className="grid size-11 place-items-center rounded-xl bg-brand-soft text-brand">
                <Icon name={icon as "pencil"} />
              </span>
              <h3 className="mt-4 font-display text-lg font-bold text-ink">{title}</h3>
              <p className="mt-2 leading-relaxed text-ink-2">{text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="bg-bg-soft py-16 sm:py-20">
        <div className="container-x">
          <SectionHeading
            eyebrow="Founding schools"
            title="Become one of our first 25 founding schools"
            lead="Founding schools get 25% off the first year, a 3-year price lock, free data migration and a direct line to the team building the product."
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
