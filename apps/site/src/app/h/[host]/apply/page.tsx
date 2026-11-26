import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getApplicationForm, getSite } from "@/lib/api";
import { normaliseHost } from "@/lib/host";
import { absoluteUrl } from "@/lib/seo";
import { ApplicationForm } from "@/components/ApplicationForm";

type Params = { host: string };

export const revalidate = 300;

async function load(params: Promise<Params>) {
  const { host: raw } = await params;
  const host = normaliseHost(decodeURIComponent(raw));
  if (!host) notFound();
  const [site, form] = await Promise.all([getSite(host), getApplicationForm(host)]);
  if (!site || !form) notFound();
  return { host, site, form };
}

export async function generateMetadata({ params }: { params: Promise<Params> }): Promise<Metadata> {
  const { host, site, form } = await load(params);
  const title = `Apply online for ${form.academicYear} | ${site.name}`;
  const description = `Apply for admission to ${site.name} for ${form.academicYear}. Fill in your child's details and upload documents online in a few minutes.`;
  const url = absoluteUrl(host, "apply");
  return {
    title,
    description,
    alternates: { canonical: url },
    openGraph: { title, description, url, siteName: site.name, locale: "en_IN", type: "website" },
    twitter: { card: "summary", title, description },
  };
}

export default async function ApplyPage({ params }: { params: Promise<Params> }) {
  const { site, form } = await load(params);
  return (
    <>
      <section className="bg-primary-soft relative overflow-hidden">
        <div
          className="sb-blob"
          style={{
            width: 340,
            height: 340,
            right: -100,
            top: -140,
            backgroundImage:
              "radial-gradient(circle, var(--sb-color-accent) 0%, var(--sb-color-accent) 55%, transparent 72%)",
          }}
        />
        <div className="sb-container relative py-14 md:py-20">
          <p className="sb-eyebrow mb-3">{site.name}</p>
          <h1 className="text-4xl font-extrabold sm:text-5xl">
            Apply online · {form.academicYear}
          </h1>
          <p className="sb-muted mt-4 max-w-2xl text-lg">
            {form.intro ||
              "Fill in your child's details and upload the documents below. It takes about ten minutes."}
          </p>
        </div>
      </section>
      <section className="sb-section">
        <div className="sb-container max-w-4xl">
          <ApplicationForm form={form} />
        </div>
      </section>
    </>
  );
}
