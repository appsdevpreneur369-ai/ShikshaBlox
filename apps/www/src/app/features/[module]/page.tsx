import Link from "next/link";
import { notFound } from "next/navigation";
import { Icon } from "@/components/Icon";
import { ButtonLink, CtaBand, JsonLd, PageHero, Photo, SectionHeading } from "@/components/ui";
import { findModulePage, modulePages } from "@/data/modules";
import { breadcrumbLd, pageMetadata } from "@/lib/seo";

export const dynamicParams = false;

export function generateStaticParams() {
  return modulePages.map((m) => ({ module: m.slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ module: string }> }) {
  const m = findModulePage((await params).module);
  if (!m) return {};
  return pageMetadata({ title: m.title, description: m.description, path: `/features/${m.slug}` });
}

export default async function ModulePage({ params }: { params: Promise<{ module: string }> }) {
  const m = findModulePage((await params).module);
  if (!m) notFound();
  const plan = { R1: "web-pro", R2: "lite", R5: "global" }[m.release as string] ?? "standard";
  const crumbs = [
    { name: "Home", path: "/" },
    { name: "Features", path: "/features" },
    { name: m.name, path: `/features/${m.slug}` },
  ];
  const others = modulePages.filter((o) => o.slug !== m.slug).slice(0, 6);
  return (
    <>
      <JsonLd data={breadcrumbLd(crumbs)} />
      <PageHero
        crumbs={crumbs}
        eyebrow={<p className="eyebrow">{m.name}</p>}
        title={m.h1}
        lead={m.intro}
        aside={
          <div className="relative">
            <div className="overflow-hidden rounded-3xl border border-line shadow-pop">
              <Photo src={m.image.src} alt={m.image.alt} sizes="(min-width: 1024px) 560px, 92vw" priority className="aspect-[4/3] h-full w-full" />
            </div>
          </div>
        }
      >
        <ButtonLink href={`/contact?plan=${plan}`} arrow>
          Book a free demo
        </ButtonLink>
        <ButtonLink href="/pricing" variant="secondary">
          Pricing
        </ButtonLink>
      </PageHero>

      <section className="container-x py-20 sm:py-24">
        <SectionHeading eyebrow="Features" title={`What you get with ${m.name}`} />
        <ul className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {m.features.map((f) => (
            <li key={f.title} className="card card-hover reveal p-6">
              <span className="grid size-11 place-items-center rounded-xl bg-brand-soft text-brand">
                <Icon name={f.icon} />
              </span>
              <h3 className="mt-4 font-display text-lg font-bold text-ink">{f.title}</h3>
              <p className="mt-2 leading-relaxed text-ink-2">{f.text}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="bg-bg-soft py-20 sm:py-24">
        <div className="container-x grid gap-12 lg:grid-cols-2">
          <div>
            <SectionHeading eyebrow="Outcomes" title="What changes at your school" />
            <ul className="mt-8 space-y-4">
              {m.outcomes.map((o) => (
                <li key={o} className="flex items-start gap-3 text-lg text-ink">
                  <span className="mt-0.5 grid size-7 shrink-0 place-items-center rounded-full bg-live-soft text-live">
                    <Icon name="check" className="size-4" strokeWidth={3} />
                  </span>
                  {o}
                </li>
              ))}
            </ul>
          </div>
          <div>
            <SectionHeading eyebrow="Right-sized" title="Depth by school profile" />
            <dl className="mt-8 divide-y divide-line overflow-hidden rounded-2xl border border-line bg-card">
              {(
                [
                  ["Small schools (Lite)", m.depth.small, "/solutions/small-schools"],
                  ["Medium schools (Standard)", m.depth.medium, "/solutions/medium-schools"],
                  ["International (Global)", m.depth.international, "/solutions/international-schools"],
                ] as const
              ).map(([k, v, href]) => (
                <div key={k} className="grid gap-1 p-5 sm:grid-cols-[13rem_1fr] sm:gap-4">
                  <dt>
                    <Link prefetch={false} href={href} className="font-semibold text-ink hover:text-brand">
                      {k}
                    </Link>
                  </dt>
                  <dd className="text-ink-2">{v}</dd>
                </div>
              ))}
            </dl>
          </div>
        </div>
      </section>

      <section className="container-x py-20 sm:py-24">
        <h2 className="font-display text-2xl font-extrabold tracking-tight text-ink">Explore more modules</h2>
        <ul className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {others.map((o) => (
            <li key={o.slug}>
              <Link prefetch={false} href={`/features/${o.slug}`} className="card card-hover flex items-center justify-between gap-4 p-5">
                <span>
                  <span className="block font-semibold text-ink">{o.name}</span>
                  <span className="mt-1 block text-sm text-ink-3">{o.description.split(". ")[0]}</span>
                </span>
                <Icon name="arrow" className="size-5 shrink-0 text-brand" />
              </Link>
            </li>
          ))}
        </ul>
      </section>
      <CtaBand />
    </>
  );
}
