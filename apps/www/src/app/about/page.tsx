import { Icon } from "@/components/Icon";
import { ButtonLink, CtaBand, JsonLd, PageHero, Photo, SectionHeading } from "@/components/ui";
import { site } from "@/data/site";
import { breadcrumbLd, pageMetadata } from "@/lib/seo";

export const metadata = pageMetadata({
  title: "About ShikshaBlox by Aurora Blossom",
  description:
    "ShikshaBlox is a School ERP Software product of Aurora Blossom, built in India to give every school, from a 150-student village school to an IB campus, modern, affordable software.",
  path: "/about",
});

const crumbs = [
  { name: "Home", path: "/" },
  { name: "About", path: "/about" },
];

export default function AboutPage() {
  return (
    <>
      <JsonLd data={breadcrumbLd(crumbs)} />
      <PageHero
        crumbs={crumbs}
        eyebrow={<p className="eyebrow">About us</p>}
        title="Modern school software, for every school"
        lead="ShikshaBlox (from Shiksha, education, and blocks) is built by Aurora Blossom in India. We believe a small school deserves software as good as the biggest international campus, at a price it can afford."
        aside={
          <div className="overflow-hidden rounded-3xl border border-line shadow-pop">
            <Photo src="/images/campus-playground.webp" alt="School campus with a playground" sizes="(min-width: 1024px) 560px, 92vw" priority className="aspect-[4/3] h-full w-full" />
          </div>
        }
      />
      <section className="container-x grid gap-10 py-16 sm:py-24 lg:grid-cols-3">
        {[
          ["compass", "Our mission", "Give every school a simple, safe, affordable platform that grows with it, so teachers spend time on children, not paperwork."],
          ["heart", "What we value", "Honesty about what's live, privacy for children, fair published prices, and software that works on a ₹8,000 phone on a slow network."],
          ["building", "Who we are", "ShikshaBlox is a product of Aurora Blossom, the team behind ClinicFlow247, a multi-tenant clinic management platform used by clinics in India."],
        ].map(([i, t, d]) => (
          <div key={t} className="card p-7">
            <Icon name={i as "compass"} className="size-7 text-brand" />
            <h2 className="mt-4 font-display text-xl font-bold text-ink">{t}</h2>
            <p className="mt-2 leading-relaxed text-ink-2">{d}</p>
          </div>
        ))}
      </section>
      <section className="bg-bg-soft py-16 sm:py-24">
        <div className="container-x grid items-center gap-10 lg:grid-cols-2">
          <SectionHeading
            eyebrow="Aurora Blossom"
            title="Products built to serve, not to lock in"
            lead="We build multi-tenant software platforms: one core product, many organisations, each with its own brand and isolated data. ClinicFlow247 does it for clinics; ShikshaBlox does it for schools."
          />
          <div className="card p-7">
            <dl className="space-y-4">
              <div className="flex justify-between gap-4 border-b border-line pb-4">
                <dt className="text-ink-3">Product</dt>
                <dd className="font-semibold text-ink">ShikshaBlox: School ERP Software</dd>
              </div>
              <div className="flex justify-between gap-4 border-b border-line pb-4">
                <dt className="text-ink-3">Company</dt>
                <dd className="font-semibold text-ink">
                  <a href={site.company.url} className="text-brand hover:underline" rel="noopener">
                    Aurora Blossom
                  </a>
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-ink-3">Based in</dt>
                <dd className="font-semibold text-ink">India</dd>
              </div>
            </dl>
            <ButtonLink href="/contact" className="mt-6 w-full" arrow>
              Talk to the team
            </ButtonLink>
          </div>
        </div>
      </section>
      <CtaBand />
    </>
  );
}
