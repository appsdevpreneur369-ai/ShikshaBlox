import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { resolveSite } from "@/lib/api";
import { normaliseHost } from "@/lib/host";

type Params = { host: string; path?: string[] };

export const revalidate = 60;

async function load(params: Promise<Params>) {
  const { host: rawHost } = await params;
  const host = normaliseHost(decodeURIComponent(rawHost));
  if (!host) notFound();
  const site = await resolveSite(host);
  if (!site) notFound();
  return site;
}

export async function generateMetadata({ params }: { params: Promise<Params> }): Promise<Metadata> {
  const site = await load(params);
  return { title: site.name, robots: { index: false } };
}

/**
 * M1 shell: the host is resolved to a school by the backend. M2 replaces this with the school's
 * published pages rendered from its block documents.
 */
export default async function SitePage({ params }: { params: Promise<Params> }) {
  const site = await load(params);
  return (
    <main className="mx-auto flex min-h-dvh max-w-3xl flex-col justify-center gap-6 px-6 py-16">
      <p className="text-ink-muted text-sm font-semibold tracking-widest uppercase">Welcome to</p>
      <h1 className="font-heading text-primary text-4xl font-bold sm:text-5xl">{site.name}</h1>
      <p className="text-ink-muted text-lg">
        Our new website is being prepared. Please check back soon.
      </p>
      <p className="text-ink-muted mt-10 text-xs">Powered by ShikshaBlox · © aurora-blossom.com</p>
    </main>
  );
}
