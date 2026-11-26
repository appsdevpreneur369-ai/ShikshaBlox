import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getSite } from "@/lib/api";
import { normaliseHost } from "@/lib/host";
import { absoluteUrl } from "@/lib/seo";
import { PageHero } from "@/components/PageHero";
import { Helpdesk } from "@/components/engage/Helpdesk";

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
  const title = `Parent helpdesk | ${site.name}`;
  const description = `Ask ${site.name} a question or raise a request about fees, transport, certificates and more, and follow it online.`;
  const url = absoluteUrl(host, "help");
  return {
    title,
    description,
    alternates: { canonical: url },
    openGraph: { title, description, url, siteName: site.name, type: "website" },
  };
}

export default async function HelpPage({ params }: { params: Promise<Params> }) {
  const { site } = await load(params);
  return (
    <>
      <PageHero eyebrow={site.name} title="Parent helpdesk">
        Ask a question or raise a request. You get a request number to follow its progress here.
      </PageHero>
      <section className="sb-section">
        <div className="sb-container max-w-4xl">
          <Helpdesk />
        </div>
      </section>
    </>
  );
}
