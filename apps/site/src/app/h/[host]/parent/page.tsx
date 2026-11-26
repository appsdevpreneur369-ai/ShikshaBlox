import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getSite } from "@/lib/api";
import { normaliseHost } from "@/lib/host";
import { PageHero } from "@/components/PageHero";
import { ParentPortal } from "@/components/portal/Portal";

type Params = { host: string };

export const dynamic = "force-dynamic";

export async function generateMetadata({ params }: { params: Promise<Params> }): Promise<Metadata> {
  const host = normaliseHost(decodeURIComponent((await params).host));
  const site = host ? await getSite(host) : null;
  return {
    title: site ? `Parent portal | ${site.name}` : "Parent portal",
    robots: { index: false, follow: false },
  };
}

export default async function ParentPage({ params }: { params: Promise<Params> }) {
  const host = normaliseHost(decodeURIComponent((await params).host));
  if (!host) notFound();
  const site = await getSite(host);
  if (!site) notFound();
  return (
    <>
      <PageHero eyebrow={site.name} title="Parent portal" />
      <section className="sb-section">
        <div className="sb-container max-w-5xl">
          <ParentPortal />
        </div>
      </section>
    </>
  );
}
