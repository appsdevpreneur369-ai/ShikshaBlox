import Link from "next/link";
import type { FeedItem, MediaMap } from "@/lib/types";
import { Img } from "./Img";
import { ArrowRight, Calendar, Pin } from "./Icons";
import { formatDate } from "./blocks/Feeds";

export type FeedKind = "news" | "events" | "albums" | "notices";

/** `/news/<slug>`, `/events/<slug>`, `/gallery/<slug>` detail pages for content types. */
export function isFeedPath(segments: string[]): FeedKind | null {
  if (segments.length !== 2) return null;
  return ({ news: "news", events: "events", gallery: "albums" } as const)[segments[0] as "news"] ?? null;
}

const BACK: Record<FeedKind, { href: string; label: string }> = {
  news: { href: "/news", label: "All news" },
  events: { href: "/events", label: "All events" },
  albums: { href: "/gallery", label: "All albums" },
  notices: { href: "/news", label: "All notices" },
};

export function FeedDetail({ kind, item, media }: { kind: FeedKind; item: FeedItem; media: MediaMap }) {
  const back = BACK[kind];
  return (
    <article>
      <header className="relative overflow-hidden bg-primary-soft">
        <div className="sb-blob" style={{ width: 340, height: 340, right: -100, top: -140, background: "var(--sb-color-accent)" }} />
        <div className="sb-container relative max-w-4xl py-14 md:py-20">
          <Link href={back.href} className="sb-focus -ml-2 inline-flex min-h-11 items-center gap-1.5 rounded-full px-2 text-sm font-semibold text-primary hover:underline">
            <ArrowRight width={16} height={16} className="rotate-180" /> {back.label}
          </Link>
          <h1 className="mt-4 text-4xl font-extrabold sm:text-5xl">{item.title}</h1>
          <p className="sb-muted mt-4 flex flex-wrap items-center gap-x-5 gap-y-2">
            {item.date && (
              <span className="inline-flex items-center gap-1.5">
                <Calendar width={17} height={17} />
                {formatDate(item.date)}
                {item.endDate && item.endDate !== item.date ? ` – ${formatDate(item.endDate)}` : ""}
              </span>
            )}
            {item.location && (
              <span className="inline-flex items-center gap-1.5">
                <Pin width={17} height={17} /> {item.location}
              </span>
            )}
            {kind === "albums" && <span>{item.imageIds?.length ?? 0} photos</span>}
          </p>
        </div>
      </header>
      <div className="sb-section">
        <div className="sb-container max-w-4xl">
          {kind !== "albums" && item.imageId && media[item.imageId]?.src && (
            <div className="sb-reveal mb-10 overflow-hidden rounded-[calc(var(--sb-radius)*1.4)] shadow-xl">
              <Img id={item.imageId} media={media} priority className="block h-auto w-full" />
            </div>
          )}
          {item.summary && kind === "albums" && <p className="sb-muted mb-8 text-lg">{item.summary}</p>}
          {item.bodyHtml && <div className="sb-prose" dangerouslySetInnerHTML={{ __html: item.bodyHtml }} />}
        </div>
        {kind === "albums" && (
          <div className="sb-container">
            <div className="columns-2 gap-4 md:columns-3 [&>*]:mb-4">
              {(item.imageIds ?? [])
                .filter((id) => media[id]?.src)
                .map((id) => (
                  <figure key={id} className="sb-zoom sb-reveal break-inside-avoid overflow-hidden rounded-[var(--sb-radius)] shadow-sm">
                    <a href={media[id]!.src} target="_blank" rel="noopener">
                      <Img id={id} media={media} sizes="(min-width: 768px) 33vw, 50vw" className="w-full" />
                    </a>
                    {media[id]?.caption && <figcaption className="sb-muted px-1 pt-2 text-sm">{media[id]!.caption}</figcaption>}
                  </figure>
                ))}
            </div>
          </div>
        )}
      </div>
    </article>
  );
}
