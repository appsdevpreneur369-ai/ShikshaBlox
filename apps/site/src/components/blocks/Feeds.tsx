import Link from "next/link";
import { getFeed } from "@/lib/api";
import type { FeedItem } from "@/lib/types";
import { Img } from "../Img";
import { ArrowRight, Briefcase, Calendar, Download, Pin, Pinned } from "../Icons";

/** Feed blocks render live content types (M3). An empty feed shows a friendly empty state. */

function Heading({ heading, intro }: { heading?: string; intro?: string }) {
  if (!heading && !intro) return null;
  return (
    <div className="sb-heading-block center sb-reveal">
      {heading && <h2 className="sb-h2">{heading}</h2>}
      {intro && <p className="sb-muted mt-4 text-lg leading-relaxed">{intro}</p>}
    </div>
  );
}

function Empty({ text }: { text: string }) {
  return (
    <p className="sb-muted mx-auto max-w-md rounded-[var(--sb-radius)] border-2 border-dashed border-slate-200 p-8 text-center">
      {text}
    </p>
  );
}

const fmt = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric", timeZone: "Asia/Kolkata" });

export function formatDate(iso?: string) {
  if (!iso) return "";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "" : fmt.format(d);
}

function DateBadge({ iso }: { iso?: string }) {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const day = new Intl.DateTimeFormat("en-IN", { day: "2-digit", timeZone: "Asia/Kolkata" }).format(d);
  const mon = new Intl.DateTimeFormat("en-IN", { month: "short", timeZone: "Asia/Kolkata" }).format(d);
  return (
    <span className="flex h-16 w-16 shrink-0 flex-col items-center justify-center rounded-2xl bg-accent text-on-accent">
      <span className="font-heading text-2xl leading-none font-extrabold">{day}</span>
      <span className="text-xs font-bold uppercase">{mon}</span>
    </span>
  );
}

export async function NoticesFeed({ host, heading, limit }: { host: string; heading?: string; limit: number }) {
  const { items, media } = await getFeed(host, "notices", { limit });
  return (
    <div className="sb-container max-w-4xl">
      <Heading heading={heading} />
      {items.length === 0 ? (
        <Empty text="No notices at the moment. Please check back soon." />
      ) : (
        <ul className="space-y-3">
          {items.map((n) => (
            <li key={n.id} className="sb-card sb-lift sb-reveal flex gap-4 p-5">
              <span
                className={`inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${
                  n.pinned ? "bg-accent text-on-accent" : "bg-primary-soft text-primary"
                }`}
              >
                {n.pinned ? <Pinned /> : <Calendar />}
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="font-bold">{n.title}</h3>
                  {n.category && (
                    <span className="rounded-full bg-primary-soft px-2.5 py-0.5 text-xs font-semibold text-primary">{n.category}</span>
                  )}
                </div>
                <p className="sb-muted mt-0.5 text-sm">{formatDate(n.date)}</p>
                {n.summary && <p className="mt-2 leading-relaxed">{n.summary}</p>}
                {n.attachmentId && media[n.attachmentId]?.src && (
                  <a
                    href={media[n.attachmentId]!.src}
                    target="_blank"
                    rel="noopener"
                    className="mt-3 inline-flex items-center gap-2 text-sm font-semibold text-primary hover:underline"
                  >
                    <Download width={16} height={16} /> Download attachment
                  </a>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export async function NewsFeed({ host, heading, limit }: { host: string; heading?: string; limit: number }) {
  const { items, media } = await getFeed(host, "news", { limit });
  return (
    <div className="sb-container">
      <Heading heading={heading} />
      {items.length === 0 ? (
        <Empty text="News stories will appear here." />
      ) : (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {items.map((n) => (
            <Link key={n.id} href={`/news/${n.slug}`} className="sb-card sb-lift sb-reveal sb-focus flex flex-col">
              <div className="sb-zoom bg-primary-soft">
                {n.imageId && media[n.imageId]?.src ? (
                  <Img id={n.imageId} media={media} sizes="(min-width: 1024px) 33vw, 100vw" className="aspect-[16/10] w-full bg-primary-soft object-contain" />
                ) : (
                  <div className="aspect-[16/10]" />
                )}
              </div>
              <div className="flex flex-1 flex-col p-6">
                <p className="sb-muted text-sm">{formatDate(n.date)}</p>
                <h3 className="mt-1 text-xl font-bold">{n.title}</h3>
                {n.summary && <p className="sb-muted mt-2 line-clamp-3 leading-relaxed">{n.summary}</p>}
                <span className="mt-auto inline-flex items-center gap-1.5 pt-4 font-semibold text-primary">
                  Read more <ArrowRight width={16} height={16} />
                </span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

export async function EventsFeed({
  host,
  heading,
  limit,
  show,
}: {
  host: string;
  heading?: string;
  limit: number;
  show: "upcoming" | "past" | "all";
}) {
  const { items, media } = await getFeed(host, "events", { limit, show });
  return (
    <div className="sb-container max-w-5xl">
      <Heading heading={heading} />
      {items.length === 0 ? (
        <Empty text={show === "past" ? "Past events will appear here." : "No upcoming events right now."} />
      ) : (
        <ul className="grid gap-5 md:grid-cols-2">
          {items.map((e) => (
            <li key={e.id}>
              <Link href={`/events/${e.slug}`} className="sb-card sb-lift sb-reveal sb-focus flex h-full flex-col">
                {e.imageId && media[e.imageId]?.src && (
                  <div className="sb-zoom">
                    <Img id={e.imageId} media={media} sizes="(min-width: 768px) 50vw, 100vw" className="aspect-[16/9] w-full bg-primary-soft object-contain" />
                  </div>
                )}
                <div className="flex flex-1 gap-4 p-6">
                  <DateBadge iso={e.date} />
                  <div className="min-w-0">
                    <h3 className="text-lg font-bold">{e.title}</h3>
                    {e.location && (
                      <p className="sb-muted mt-1 flex items-center gap-1.5 text-sm">
                        <Pin width={15} height={15} /> {e.location}
                      </p>
                    )}
                    {e.summary && <p className="sb-muted mt-2 line-clamp-2">{e.summary}</p>}
                  </div>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export async function AlbumsFeed({ host, heading, limit }: { host: string; heading?: string; limit: number }) {
  const { items, media } = await getFeed(host, "albums", { limit });
  return (
    <div className="sb-container">
      <Heading heading={heading} />
      {items.length === 0 ? (
        <Empty text="Photo albums will appear here." />
      ) : (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((a) => (
            <Link key={a.id} href={`/gallery/${a.slug}`} className="sb-zoom sb-reveal sb-focus group relative block overflow-hidden rounded-[calc(var(--sb-radius)*1.3)] shadow-md">
              <Img id={a.imageId} media={media} sizes="(min-width: 1024px) 33vw, 50vw" className="aspect-[4/3] w-full bg-primary-soft object-contain" />
              <div className="absolute inset-0 bg-gradient-to-t from-black/75 via-black/10 to-transparent" />
              <div className="absolute inset-x-0 bottom-0 p-5 text-white">
                <h3 className="text-xl font-bold">{a.title}</h3>
                <p className="text-sm text-white/95">
                  {a.count ?? 0} photos{a.date ? ` · ${formatDate(a.date)}` : ""}
                </p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

export async function StaffGrid({
  host,
  heading,
  intro,
  department,
}: {
  host: string;
  heading?: string;
  intro?: string;
  department?: string;
}) {
  const { items, media } = await getFeed(host, "staff", { department });
  return (
    <div className="sb-container">
      <Heading heading={heading} intro={intro} />
      {items.length === 0 ? (
        <Empty text="Our staff directory is being updated." />
      ) : (
        <div className="grid grid-cols-2 gap-5 md:grid-cols-3 lg:grid-cols-4">
          {items.map((s) => (
            <div key={s.id} className="sb-card sb-lift sb-reveal p-5 text-center">
              <div className="sb-zoom mx-auto aspect-[4/5] w-full max-w-40 overflow-hidden rounded-[var(--sb-radius)] bg-primary-soft">
                {s.imageId && media[s.imageId]?.src ? (
                  <Img id={s.imageId} media={media} sizes="160px" className="h-full w-full object-contain" />
                ) : (
                  <span className="font-heading flex h-full w-full items-center justify-center text-3xl font-bold text-primary">
                    {s.title
                      .split(/\s+/)
                      .map((w) => w[0])
                      .slice(0, 2)
                      .join("")}
                  </span>
                )}
              </div>
              <h3 className="mt-4 font-bold">{s.title}</h3>
              {s.role && <p className="text-sm font-medium text-primary">{s.role}</p>}
              {s.qualification && <p className="sb-muted mt-1 text-sm">{s.qualification}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export async function CareersFeed({ host, heading, intro }: { host: string; heading?: string; intro?: string }) {
  const { items } = await getFeed(host, "careers");
  return (
    <div className="sb-container max-w-4xl">
      <Heading heading={heading} intro={intro} />
      {items.length === 0 ? (
        <Empty text="There are no open positions right now. You are welcome to send us your CV through the contact page." />
      ) : (
        <ul className="space-y-4">
          {items.map((j) => (
            <li key={j.id}>
              <details className="sb-details sb-card sb-reveal">
                <summary className="sb-focus flex items-center gap-4 p-6">
                  <span className="inline-flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-primary-soft text-primary">
                    <Briefcase />
                  </span>
                  <span className="flex-1">
                    <span className="block text-lg font-bold">{j.title}</span>
                    <span className="sb-muted text-sm">
                      {[j.department, j.location, j.date ? `Apply by ${formatDate(j.date)}` : null].filter(Boolean).join(" · ")}
                    </span>
                  </span>
                </summary>
                {j.bodyHtml && <div className="sb-prose border-t border-slate-100 px-6 py-5" dangerouslySetInnerHTML={{ __html: j.bodyHtml }} />}
              </details>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export async function FaqsFeed({
  host,
  render,
}: {
  host: string;
  heading?: string;
  render: (items: { question: string; answer: string }[]) => React.ReactNode;
}) {
  const { items } = await getFeed(host, "faqs");
  return <>{render(items.map((f: FeedItem) => ({ question: f.title, answer: f.summary ?? "" })))}</>;
}
