import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getFeedItem, getPage, getSite } from "@/lib/api";
import { normaliseHost } from "@/lib/host";
import { RenderBlock } from "@/components/blocks/Blocks";
import { FeedDetail, isFeedPath } from "@/components/FeedDetail";

type Params = { host: string; path?: string[] };

export const revalidate = 300;

async function load(params: Promise<Params>) {
  const { host: rawHost, path = [] } = await params;
  const host = normaliseHost(decodeURIComponent(rawHost));
  if (!host) notFound();
  const segments = path.map((s) => decodeURIComponent(s));
  if (segments.some((s) => !/^[a-z0-9-]{1,80}$/.test(s))) notFound();
  const site = await getSite(host);
  if (!site) notFound();
  return { host, site, segments };
}

async function loadFeed(host: string, segments: string[]) {
  const kind = isFeedPath(segments);
  if (!kind) return null;
  const feed = await getFeedItem(host, kind, segments[1]!);
  const item = feed?.items[0];
  return item && feed ? { kind, item, media: feed.media } : null;
}

export async function generateMetadata({ params }: { params: Promise<Params> }): Promise<Metadata> {
  const { host, site, segments } = await load(params);
  const page = await getPage(host, segments.join("/"));
  const ogId = page?.seo.ogImageId;
  const og = ogId ? page?.media[ogId]?.src : undefined;
  if (!page) {
    const f = await loadFeed(host, segments);
    if (!f) return { title: site.name };
    return { title: `${f.item.title} | ${site.name}`, description: f.item.summary };
  }
  const title = page.seo.metaTitle || (page.slug === "" ? `${site.name}${site.profile.tagline ? ` – ${site.profile.tagline}` : ""}` : `${page.title} | ${site.name}`);
  return {
    title,
    description: page.seo.metaDescription || site.profile.tagline,
    robots: page.seo.noindex ? { index: false } : undefined,
    openGraph: { title, siteName: site.name, images: og ? [og] : undefined, type: "website" },
  };
}

export default async function SitePage({ params }: { params: Promise<Params> }) {
  const { host, site, segments } = await load(params);
  const page = await getPage(host, segments.join("/"));
  if (!page) {
    const f = await loadFeed(host, segments);
    if (!f) notFound();
    return <FeedDetail kind={f.kind} item={f.item} media={f.media} />;
  }
  const blocks = page.document.blocks;
  const startsWithHero = blocks[0]?.type === "hero";
  return (
    <>
      {!startsWithHero && (
        <section className="relative overflow-hidden bg-primary-soft">
          <div className="sb-blob" style={{ width: 340, height: 340, right: -100, top: -140, background: "var(--sb-color-accent)" }} />
          <div className="sb-container relative py-14 md:py-20">
            <p className="sb-eyebrow mb-3">{site.name}</p>
            <h1 className="text-4xl font-extrabold sm:text-5xl">{page.title}</h1>
          </div>
        </section>
      )}
      {blocks.map((b, i) => (
        <RenderBlock
          key={b.id}
          block={b}
          ctx={{ host, site, media: page.media, lang: page.language, first: i === 0 && startsWithHero }}
        />
      ))}
    </>
  );
}
