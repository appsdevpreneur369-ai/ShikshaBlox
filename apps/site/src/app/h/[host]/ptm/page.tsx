import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getSite } from "@/lib/api";
import { normaliseHost } from "@/lib/host";
import { absoluteUrl } from "@/lib/seo";
import { PageHero } from "@/components/PageHero";
import { PtmBooking } from "@/components/engage/PtmBooking";

type Params = { host: string };

async function load(params: Promise<Params>) {
  const host = normaliseHost(decodeURIComponent((await params).host));
  if (!host) notFound();
  const site = await getSite(host);
  if (!site) notFound();
  return { host, site };
}

export async function generateMetadata({ params }: { params: Promise<Params> }): Promise<Metadata> {
  const { host, site } = await load(params);
  const title = `Book a parent-teacher meeting | ${site.name}`;
  return {
    title,
    description: `Book a parent-teacher meeting slot at ${site.name} online.`,
    alternates: { canonical: absoluteUrl(host, "ptm") },
    robots: { index: false, follow: true },
  };
}

export default async function PtmPage({ params }: { params: Promise<Params> }) {
  const { site } = await load(params);
  return (
    <>
      <PageHero eyebrow={site.name} title="Parent-teacher meeting">
        Choose your child&apos;s teacher and a time that suits you. You will get a booking code
        straight away.
      </PageHero>
      <section className="sb-section">
        <div className="sb-container max-w-5xl">
          <PtmBooking />
        </div>
      </section>
    </>
  );
}
