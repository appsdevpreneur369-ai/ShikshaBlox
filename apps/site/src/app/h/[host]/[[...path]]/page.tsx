import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { normaliseHost } from "@/lib/host";

type Params = { host: string; path?: string[] };

export const revalidate = 60;

export async function generateMetadata({ params }: { params: Promise<Params> }): Promise<Metadata> {
  const { host } = await params;
  return { title: `School site · ${host}`, robots: { index: false } };
}

/**
 * M0 placeholder. In M2 this resolves the host to a site via the backend and renders the
 * published block document for the requested path.
 */
export default async function SitePage({ params }: { params: Promise<Params> }) {
  const { host: rawHost, path = [] } = await params;
  const host = normaliseHost(decodeURIComponent(rawHost));
  if (!host) notFound();

  return (
    <main className="mx-auto flex min-h-dvh max-w-3xl flex-col justify-center gap-6 px-6 py-16">
      <p className="text-ink-muted text-sm font-semibold tracking-widest uppercase">ShikshaBlox</p>
      <h1 className="font-heading text-primary text-4xl font-bold sm:text-5xl">
        School website coming soon
      </h1>
      <p className="text-ink-muted text-lg">
        Tenant host <code className="bg-surface-muted rounded-md px-2 py-1">{host}</code>
        {path.length > 0 && (
          <>
            {" "}
            · path <code className="bg-surface-muted rounded-md px-2 py-1">/{path.join("/")}</code>
          </>
        )}
      </p>
    </main>
  );
}
