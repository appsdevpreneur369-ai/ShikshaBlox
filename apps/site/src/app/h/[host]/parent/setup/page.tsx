import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getSite } from "@/lib/api";
import { normaliseHost } from "@/lib/host";
import { PageHero } from "@/components/PageHero";
import { ParentSetup } from "@/components/portal/Portal";

type Params = { host: string };

export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "Set up your parent account",
  robots: { index: false, follow: false },
};

export default async function SetupPage({
  params,
  searchParams,
}: {
  params: Promise<Params>;
  searchParams: Promise<{ code?: string }>;
}) {
  const host = normaliseHost(decodeURIComponent((await params).host));
  const { code } = await searchParams;
  if (!host || !code || !/^[A-Za-z0-9_-]{20,64}$/.test(code)) notFound();
  const site = await getSite(host);
  if (!site) notFound();
  return (
    <>
      <PageHero eyebrow={site.name} title="Welcome to the parent portal" />
      <section className="sb-section">
        <div className="sb-container">
          <ParentSetup code={code} />
        </div>
      </section>
    </>
  );
}
