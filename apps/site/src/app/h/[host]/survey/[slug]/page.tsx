import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getSite } from "@/lib/api";
import { normaliseHost } from "@/lib/host";
import { PageHero } from "@/components/PageHero";
import { SurveyForm, type PublicSurvey } from "@/components/engage/SurveyForm";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

type Params = { host: string; slug: string };

async function load(params: Promise<Params>) {
  const p = await params;
  const host = normaliseHost(decodeURIComponent(p.host));
  const slug = decodeURIComponent(p.slug);
  if (!host || !/^[a-z0-9-]{3,60}$/.test(slug)) notFound();
  const site = await getSite(host);
  if (!site) notFound();
  // Not cached: surveys open and close at any time.
  const res = await fetch(
    `${API}/api/public/v1/engagement/surveys/${slug}?host=${encodeURIComponent(host)}`,
    { cache: "no-store" },
  );
  if (!res.ok) notFound();
  return { site, slug, survey: (await res.json()) as PublicSurvey };
}

export async function generateMetadata({ params }: { params: Promise<Params> }): Promise<Metadata> {
  const { site, survey } = await load(params);
  return { title: `${survey.title} | ${site.name}`, robots: { index: false, follow: false } };
}

export default async function SurveyPage({ params }: { params: Promise<Params> }) {
  const { site, slug, survey } = await load(params);
  return (
    <>
      <PageHero eyebrow={site.name} title={survey.title}>
        {survey.description}
      </PageHero>
      <section className="sb-section">
        <div className="sb-container max-w-4xl">
          <SurveyForm slug={slug} survey={survey} />
        </div>
      </section>
    </>
  );
}
