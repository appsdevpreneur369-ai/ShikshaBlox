import Link from "next/link";
import { notFound } from "next/navigation";
import { Icon } from "@/components/Icon";
import { ButtonLink, CtaBand, JsonLd, PageHero, Photo, SectionHeading } from "@/components/ui";
import { plans } from "@/data/plans";
import { findProfile, profiles } from "@/data/profiles";
import { breadcrumbLd, pageMetadata } from "@/lib/seo";

type Props = { params: Promise<{ profile: string }> };

export const dynamicParams = false;

export function generateStaticParams() {
  return profiles.map((p) => ({ profile: p.slug }));
}

export async function generateMetadata({ params }: Props) {
  const p = findProfile((await params).profile);
  if (!p) return {};
  return pageMetadata({ title: p.title, description: p.description, path: `/solutions/${p.slug}` });
}

export default async function SolutionPage({ params }: Props) {
  const p = findProfile((await params).profile);
  if (!p) notFound();
  const plan = plans.find((x) => x.name === p.plan);
  const crumbs = [
    { name: "Home", path: "/" },
    { name: p.name, path: `/solutions/${p.slug}` },
  ];
  return (
    <>
      <JsonLd data={breadcrumbLd(crumbs)} />
      <PageHero
        crumbs={crumbs}
        eyebrow={<p className="eyebrow">For {p.name.toLowerCase()}</p>}
        title={p.h1}
        lead={p.intro}
        aside={
          <div className="overflow-hidden rounded-3xl border border-line shadow-pop">
            <Photo src={p.image.src} alt={p.image.alt} sizes="(min-width: 1024px) 560px, 92vw" priority className="aspect-[4/3] h-full w-full" />
          </div>
        }
      >
        <ButtonLink href={`/contact?plan=${p.plan.toLowerCase()}`} arrow>
          Book a demo
        </ButtonLink>
        <ButtonLink href="/pricing" variant="secondary">
          {p.plan} pricing
        </ButtonLink>
      </PageHero>

      <section className="container-x grid gap-6 py-16 sm:grid-cols-3 sm:py-20">
        {[
          ["users", "Who it's for", p.who],
          ["zap", "Go-live", p.goLive],
          ["wallet", "Plan", plan ? `${plan.name}: ${plan.price} ${plan.period}` : `${p.plan}: see pricing`],
        ].map(([i, t, d]) => (
          <div key={t} className="card p-6">
            <Icon name={i as "users"} className="size-6 text-brand" />
            <h2 className="mt-3 text-sm font-bold tracking-wide text-ink-3 uppercase">{t}</h2>
            <p className="mt-1 text-lg font-semibold text-ink">{d}</p>
          </div>
        ))}
      </section>

      <section className="bg-bg-soft py-20 sm:py-24">
        <div className="container-x grid gap-12 lg:grid-cols-[0.9fr_1.1fr]">
          <div>
            <SectionHeading eyebrow="Sound familiar?" title="The problems we solve" />
            <ul className="mt-8 space-y-4">
              {p.pains.map((x) => (
                <li key={x} className="flex gap-3 text-lg text-ink-2">
                  <span className="mt-1 grid size-6 shrink-0 place-items-center rounded-full bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300">
                    <Icon name="close" className="size-3.5" strokeWidth={3} />
                  </span>
                  {x}
                </li>
              ))}
            </ul>
          </div>
          <div>
            <SectionHeading eyebrow="Your building blocks" title={`What ${p.name.toLowerCase()} switch on`} />
            <ul className="mt-8 grid gap-4 sm:grid-cols-2">
              {p.blocks.map((b) => {
                const inner = (
                  <>
                    <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-brand-soft text-brand">
                      <Icon name={b.icon} />
                    </span>
                    <span>
                      <span className="block font-semibold text-ink">{b.title}</span>
                      <span className="block text-sm text-ink-2">{b.text}</span>
                    </span>
                  </>
                );
                return (
                  <li key={b.title}>
                    {b.href ? (
                      <Link prefetch={false} href={b.href} className="card card-hover flex h-full items-center gap-4 p-5">
                        {inner}
                      </Link>
                    ) : (
                      <div className="card flex h-full items-center gap-4 p-5">{inner}</div>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        </div>
      </section>

      <section className="container-x py-16 sm:py-20">
        <h2 className="font-display text-2xl font-extrabold tracking-tight text-ink">Other school profiles</h2>
        <ul className="mt-6 grid gap-4 sm:grid-cols-3">
          {profiles
            .filter((o) => o.slug !== p.slug)
            .map((o) => (
              <li key={o.slug}>
                <Link prefetch={false} href={`/solutions/${o.slug}`} className="card card-hover flex items-center justify-between gap-4 p-5">
                  <span>
                    <span className="block font-semibold text-ink">{o.name}</span>
                    <span className="block text-sm text-ink-3">{o.short}</span>
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
