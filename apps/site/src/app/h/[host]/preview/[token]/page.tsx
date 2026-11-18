import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getPreview, getSite } from "@/lib/api";
import { normaliseHost } from "@/lib/host";
import { RenderBlock } from "@/components/blocks/Blocks";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { robots: { index: false, follow: false }, title: "Preview" };

/** Draft preview from a signed, short-lived token issued in Studio. Never cached. */
export default async function Preview({ params }: { params: Promise<{ host: string; token: string }> }) {
  const { host: raw, token } = await params;
  const host = normaliseHost(decodeURIComponent(raw));
  if (!host || !/^[A-Za-z0-9_-]{10,400}~[a-f0-9]{64}$/.test(decodeURIComponent(token))) notFound();
  const [site, page] = await Promise.all([getSite(host), getPreview(host, decodeURIComponent(token))]);
  if (!site || !page) notFound();
  const blocks = page.document.blocks;
  const startsWithHero = blocks[0]?.type === "hero";
  return (
    <>
      <div className="sticky top-[4.5rem] z-30 bg-amber-400 px-4 py-2 text-center text-sm font-semibold text-amber-950">
        Preview of unpublished changes — visitors do not see this yet
      </div>
      {!startsWithHero && (
        <section className="bg-primary-soft">
          <div className="sb-container py-14">
            <h1 className="text-4xl font-extrabold">{page.title}</h1>
          </div>
        </section>
      )}
      {blocks.map((b, i) => (
        <RenderBlock key={b.id} block={b} ctx={{ host, site, media: page.media, lang: page.language, first: i === 0 && startsWithHero }} />
      ))}
    </>
  );
}
