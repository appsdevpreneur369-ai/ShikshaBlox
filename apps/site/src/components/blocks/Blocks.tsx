import Link from "next/link";
import type { Block, BlockOf } from "@shikshablox/blocks";
import type { MediaMap, PublicSite } from "@/lib/types";
import { Img } from "../Img";
import { ArrowRight, CardIcon, Download, Mail, Phone, Pin, Clock, Quote, ChevronDown } from "../Icons";
import { addressLines } from "../SiteFooter";
import { AlbumsFeed, CareersFeed, EventsFeed, FaqsFeed, NewsFeed, NoticesFeed, StaffGrid } from "./Feeds";
import { EnquiryForm } from "./EnquiryForm";

export type Ctx = { host: string; site: PublicSite; media: MediaMap; lang: string; first: boolean };

type L = { label: string; href: string };

export function Cta({ link, variant = "primary" }: { link?: L; variant?: string }) {
  if (!link) return null;
  const external = link.href.startsWith("https://");
  const cls = `sb-btn sb-btn-${variant} sb-focus`;
  return external ? (
    <a href={link.href} className={cls} target="_blank" rel="noopener noreferrer">
      {link.label} <ArrowRight width={18} height={18} />
    </a>
  ) : (
    <Link href={link.href} className={cls}>
      {link.label} <ArrowRight width={18} height={18} />
    </Link>
  );
}

export function SectionHeading({
  eyebrow,
  heading,
  intro,
  center = false,
}: {
  eyebrow?: string;
  heading?: string;
  intro?: string;
  center?: boolean;
}) {
  if (!heading && !intro && !eyebrow) return null;
  return (
    <div className={`sb-heading-block sb-reveal ${center ? "center" : ""}`}>
      {eyebrow && <p className="sb-eyebrow mb-3">{eyebrow}</p>}
      {heading && <h2 className="sb-h2">{heading}</h2>}
      {intro && <p className="sb-muted mt-4 text-lg leading-relaxed">{intro}</p>}
    </div>
  );
}

function Section({
  block,
  children,
  defaultBg,
  className = "",
}: {
  block: Block;
  children: React.ReactNode;
  defaultBg?: string;
  className?: string;
}) {
  return (
    <section
      id={block.settings?.anchor}
      className={`sb-section ${className}`}
      data-bg={block.settings?.background ?? defaultBg ?? "default"}
      data-spacing={block.settings?.spacing ?? "normal"}
    >
      {children}
    </section>
  );
}

// --------------------------------------------------------------------------- hero

function Hero({ b, ctx }: { b: BlockOf<"hero">; ctx: Ctx }) {
  const p = b.props;
  const hasImage = !!(p.imageId && ctx.media[p.imageId]?.src);
  const Tag = ctx.first ? "h1" : "h2";
  if (p.variant === "fullBleed" && hasImage) {
    return (
      <section id={b.settings?.anchor} className="sb-hero-full">
        <Img id={p.imageId} media={ctx.media} priority={ctx.first} sizes="100vw" />
        <div className="sb-container pt-8 pb-12 lg:pt-40 lg:pb-24">
          <div className="max-w-3xl">
            {p.eyebrow && (
              <p className="mb-4 inline-flex rounded-full bg-white/15 px-4 py-1.5 text-sm font-semibold backdrop-blur">
                {p.eyebrow}
              </p>
            )}
            <Tag className="text-4xl leading-[1.08] font-extrabold sm:text-5xl md:text-6xl">{p.heading}</Tag>
            {p.subheading && <p className="mt-5 max-w-2xl text-lg text-white/90 md:text-xl">{p.subheading}</p>}
            <div className="mt-8 flex flex-wrap gap-3">
              <Cta link={p.primaryCta} variant="accent" />
              <Cta link={p.secondaryCta} variant="ghost" />
            </div>
          </div>
        </div>
      </section>
    );
  }
  if (p.variant === "split" && hasImage) {
    return (
      <section id={b.settings?.anchor} className="relative overflow-hidden bg-primary-soft">
        <div className="sb-blob" style={{ width: 380, height: 380, left: -140, top: -120, backgroundImage: "radial-gradient(circle, var(--sb-color-accent) 0%, var(--sb-color-accent) 55%, transparent 72%)" }} />
        <div className="sb-container relative grid items-center gap-10 py-14 md:grid-cols-2 md:py-20">
          <div>
            {p.eyebrow && <p className="sb-eyebrow mb-4">{p.eyebrow}</p>}
            <Tag className="text-4xl leading-[1.1] font-extrabold text-ink sm:text-5xl">{p.heading}</Tag>
            {p.subheading && <p className="sb-muted mt-5 text-lg md:text-xl">{p.subheading}</p>}
            <div className="mt-8 flex flex-wrap gap-3">
              <Cta link={p.primaryCta} variant="primary" />
              <Cta link={p.secondaryCta} variant="ghost" />
            </div>
          </div>
          <div className="relative">
            <div className="absolute -inset-3 rotate-2 rounded-[calc(var(--sb-radius)*1.6)] bg-accent/30" />
            <div className="sb-zoom relative mx-auto w-fit max-w-full overflow-hidden rounded-[calc(var(--sb-radius)*1.5)] shadow-2xl">
              <Img
                id={p.imageId}
                media={ctx.media}
                priority={ctx.first}
                sizes="(min-width: 768px) 50vw, 100vw"
                className="block h-auto w-auto max-w-full max-h-[min(34rem,80vh)]"
              />
            </div>
          </div>
        </div>
      </section>
    );
  }
  return (
    <section id={b.settings?.anchor} className="relative overflow-hidden bg-primary text-on-primary">
      <div className="sb-blob" style={{ width: 420, height: 420, right: -120, top: -160, backgroundImage: "radial-gradient(circle, var(--sb-color-accent) 0%, var(--sb-color-accent) 55%, transparent 72%)" }} />
      <div className="sb-container relative py-20 text-center md:py-28">
        {p.eyebrow && <p className="mb-4 text-sm font-bold tracking-[0.14em] text-accent uppercase">{p.eyebrow}</p>}
        <Tag className="mx-auto max-w-4xl text-4xl leading-[1.1] font-extrabold sm:text-5xl">{p.heading}</Tag>
        {p.subheading && <p className="mx-auto mt-5 max-w-2xl text-lg md:text-xl">{p.subheading}</p>}
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Cta link={p.primaryCta} variant="accent" />
          <Cta link={p.secondaryCta} variant="ghost" />
        </div>
      </div>
    </section>
  );
}

// --------------------------------------------------------------------------- content

function RichText({ b }: { b: BlockOf<"richText"> }) {
  return (
    <Section block={b}>
      <div className={`sb-container ${b.props.width === "wide" ? "max-w-5xl" : "max-w-3xl"}`}>
        <div className="sb-prose sb-reveal" dangerouslySetInnerHTML={{ __html: b.props.html }} />
      </div>
    </Section>
  );
}

function ImageText({ b, ctx }: { b: BlockOf<"imageText">; ctx: Ctx }) {
  const p = b.props;
  const img = p.imageId && ctx.media[p.imageId]?.src;
  return (
    <Section block={b}>
      <div className="sb-container grid items-center gap-10 md:grid-cols-2 md:gap-16">
        {img && (
          <div className={`sb-reveal relative ${p.imagePosition === "right" ? "md:order-2" : ""}`}>
            <div className="sb-zoom mx-auto w-fit max-w-full overflow-hidden rounded-[calc(var(--sb-radius)*1.4)] shadow-xl">
              <Img id={p.imageId} media={ctx.media} sizes="(min-width: 768px) 50vw, 100vw" className="block h-auto w-auto max-w-full max-h-[min(34rem,80vh)]" />
            </div>
            <div
              className={`absolute -bottom-4 ${p.imagePosition === "right" ? "-left-4" : "-right-4"} -z-10 h-28 w-28 rounded-[var(--sb-radius)] bg-accent/40`}
            />
          </div>
        )}
        <div className={`sb-reveal ${img ? "" : "md:col-span-2 max-w-3xl"}`}>
          {p.eyebrow && <p className="sb-eyebrow mb-3">{p.eyebrow}</p>}
          {p.heading && <h2 className="sb-h2 mb-5">{p.heading}</h2>}
          <div className="sb-prose sb-muted" dangerouslySetInnerHTML={{ __html: p.html }} />
          {p.cta && (
            <div className="mt-7">
              <Cta link={p.cta} />
            </div>
          )}
        </div>
      </div>
    </Section>
  );
}

function ImageBlock({ b, ctx }: { b: BlockOf<"image">; ctx: Ctx }) {
  const p = b.props;
  const width = p.size === "medium" ? "max-w-3xl" : p.size === "wide" ? "max-w-6xl" : "max-w-none px-0";
  return (
    <Section block={b}>
      <figure className={`sb-container ${width} sb-reveal`}>
        <div className={`sb-zoom overflow-hidden ${p.size === "full" ? "" : "rounded-[calc(var(--sb-radius)*1.4)] shadow-lg"}`}>
          <Img id={p.imageId} media={ctx.media} className="block h-auto w-full" />
        </div>
        {(p.caption || ctx.media[p.imageId]?.caption) && (
          <figcaption className="sb-muted mt-3 text-center text-sm">{p.caption || ctx.media[p.imageId]?.caption}</figcaption>
        )}
      </figure>
    </Section>
  );
}

function GalleryStrip({ b, ctx }: { b: BlockOf<"galleryStrip">; ctx: Ctx }) {
  const p = b.props;
  const ids = p.imageIds.filter((id) => ctx.media[id]?.src);
  if (ids.length === 0) return null;
  return (
    <Section block={b}>
      <div className="sb-container">
        <SectionHeading heading={p.heading} intro={p.intro} center />
        {p.layout === "masonry" ? (
          <div className="columns-2 gap-4 md:columns-3 lg:columns-4 [&>*]:mb-4">
            {ids.map((id) => (
              <figure key={id} className="sb-zoom sb-reveal break-inside-avoid overflow-hidden rounded-[var(--sb-radius)] shadow-sm">
                <Img id={id} media={ctx.media} sizes="(min-width: 1024px) 25vw, 50vw" className="w-full" />
              </figure>
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
            {ids.map((id, i) => (
              <figure
                key={id}
                className={`sb-zoom sb-reveal overflow-hidden rounded-[var(--sb-radius)] shadow-sm ${
                  i === 0 && ids.length > 4 ? "col-span-2 row-span-2" : ""
                }`}
              >
                <Img id={id} media={ctx.media} sizes="(min-width: 1024px) 25vw, 50vw" className="aspect-[4/3] h-full w-full bg-primary-soft object-contain" />
              </figure>
            ))}
          </div>
        )}
        {p.cta && (
          <div className="mt-10 text-center">
            <Cta link={p.cta} />
          </div>
        )}
      </div>
    </Section>
  );
}

function Cards({ b, ctx }: { b: BlockOf<"cards">; ctx: Ctx }) {
  const p = b.props;
  const cols = { 2: "md:grid-cols-2", 3: "md:grid-cols-2 lg:grid-cols-3", 4: "md:grid-cols-2 lg:grid-cols-4" }[p.columns];
  return (
    <Section block={b}>
      <div className="sb-container">
        <SectionHeading heading={p.heading} intro={p.intro} center />
        <div className={`grid gap-6 ${cols}`}>
          {p.items.map((it, i) => {
            const hasImg = !!(it.imageId && ctx.media[it.imageId]?.src);
            const inner = (
              <>
                {hasImg && (
                  <div className="sb-zoom">
                    <Img id={it.imageId} media={ctx.media} sizes="(min-width: 1024px) 33vw, 100vw" className="aspect-[4/3] w-full bg-primary-soft object-contain" />
                  </div>
                )}
                <div className="flex flex-1 flex-col p-6">
                  {!hasImg && it.icon && (
                    <span className="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-soft text-primary">
                      <CardIcon name={it.icon} />
                    </span>
                  )}
                  <h3 className="text-xl font-bold">{it.title}</h3>
                  {it.text && <p className="sb-muted mt-2 leading-relaxed">{it.text}</p>}
                  {it.href && (
                    <span className="mt-auto inline-flex items-center gap-1.5 pt-4 font-semibold text-primary">
                      Learn more <ArrowRight width={16} height={16} />
                    </span>
                  )}
                </div>
              </>
            );
            const cls = "sb-card sb-lift sb-reveal flex flex-col";
            return it.href ? (
              <Link key={i} href={it.href} className={`${cls} sb-focus`}>
                {inner}
              </Link>
            ) : (
              <div key={i} className={cls}>
                {inner}
              </div>
            );
          })}
        </div>
      </div>
    </Section>
  );
}

function CtaBlock({ b }: { b: BlockOf<"cta"> }) {
  const p = b.props;
  return (
    <section id={b.settings?.anchor} className="sb-section" data-spacing={b.settings?.spacing ?? "compact"}>
      <div className="sb-container">
        <div className="sb-reveal relative overflow-hidden rounded-[calc(var(--sb-radius)*1.8)] bg-primary px-6 py-14 text-center text-on-primary md:px-16">
          <div className="sb-blob" style={{ width: 300, height: 300, left: -80, bottom: -140, backgroundImage: "radial-gradient(circle, var(--sb-color-accent) 0%, var(--sb-color-accent) 55%, transparent 72%)" }} />
          <div className="sb-blob" style={{ width: 240, height: 240, right: -60, top: -100, backgroundImage: "radial-gradient(circle, #fff 0%, #fff 55%, transparent 72%)", opacity: 0.18 }} />
          <h2 className="sb-h2 relative">{p.heading}</h2>
          {p.text && <p className="relative mx-auto mt-4 max-w-2xl text-lg">{p.text}</p>}
          <div className="relative mt-8 flex flex-wrap justify-center gap-3">
            <Cta link={p.primary} variant="accent" />
            <Cta link={p.secondary} variant="ghost" />
          </div>
        </div>
      </div>
    </section>
  );
}

function Stats({ b }: { b: BlockOf<"stats"> }) {
  const p = b.props;
  return (
    <Section block={b} defaultBg="primary">
      <div className="sb-container">
        <SectionHeading heading={p.heading} center />
        <dl className="grid grid-cols-2 gap-6 md:grid-cols-4">
          {p.items.map((it, i) => (
            <div key={i} className="sb-reveal rounded-[var(--sb-radius)] bg-black/15 p-6 text-center backdrop-blur transition hover:bg-black/20">
              <dt className="sr-only">{it.label}</dt>
              <dd>
                <span className="font-heading block text-3xl font-extrabold whitespace-nowrap md:text-4xl lg:text-5xl">{it.value}</span>
                <span className="mt-2 block text-sm font-medium">{it.label}</span>
              </dd>
            </div>
          ))}
        </dl>
      </div>
    </Section>
  );
}

function Testimonials({ b }: { b: BlockOf<"testimonials"> }) {
  const p = b.props;
  return (
    <Section block={b} defaultBg="muted">
      <div className="sb-container">
        <SectionHeading heading={p.heading} center />
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {p.items.map((it, i) => (
            <figure key={i} className="sb-card sb-lift sb-reveal flex flex-col p-7">
              <Quote className="text-accent" width={36} height={36} />
              <blockquote className="mt-4 flex-1 text-lg leading-relaxed">“{it.quote}”</blockquote>
              <figcaption className="mt-6 border-t border-slate-100 pt-4">
                <p className="font-bold">{it.author}</p>
                {it.role && <p className="sb-muted text-sm">{it.role}</p>}
              </figcaption>
            </figure>
          ))}
        </div>
      </div>
    </Section>
  );
}

function Steps({ b }: { b: BlockOf<"steps"> }) {
  const p = b.props;
  return (
    <Section block={b}>
      <div className="sb-container">
        <SectionHeading heading={p.heading} intro={p.intro} center />
        <ol className="grid gap-6 md:grid-cols-2 lg:grid-cols-4">
          {p.items.map((it, i) => (
            <li key={i} className="sb-card sb-lift sb-reveal relative p-7">
              <span className="font-heading inline-flex h-12 w-12 items-center justify-center rounded-full bg-accent text-xl font-extrabold text-on-accent">
                {i + 1}
              </span>
              <h3 className="mt-5 text-lg font-bold">{it.title}</h3>
              {it.text && <p className="sb-muted mt-2 leading-relaxed">{it.text}</p>}
            </li>
          ))}
        </ol>
      </div>
    </Section>
  );
}

function FaqList({ heading, items }: { heading?: string; items: { question: string; answer: string }[] }) {
  if (items.length === 0) return null;
  return (
    <div className="sb-container max-w-3xl">
      <SectionHeading heading={heading} center />
      <div className="space-y-3">
        {items.map((it, i) => (
          <details key={i} className="sb-details sb-card sb-reveal group">
            <summary className="sb-focus flex items-center justify-between gap-4 px-6 py-5 text-lg font-semibold">
              {it.question}
              <ChevronDown className="sb-chevron shrink-0 text-primary" />
            </summary>
            <div className="sb-muted px-6 pb-6 leading-relaxed whitespace-pre-line">{it.answer}</div>
          </details>
        ))}
      </div>
    </div>
  );
}

function Faq({ b, ctx }: { b: BlockOf<"faq">; ctx: Ctx }) {
  return (
    <Section block={b}>
      {b.props.source === "faqs" ? (
        <FaqsFeed host={ctx.host} heading={b.props.heading} render={(items) => <FaqList heading={b.props.heading} items={items} />} />
      ) : (
        <FaqList heading={b.props.heading} items={b.props.items} />
      )}
    </Section>
  );
}

function MapBlock({ b }: { b: BlockOf<"map"> }) {
  const h = { small: "h-64", medium: "h-96", large: "h-[32rem]" }[b.props.height ?? "medium"];
  return (
    <Section block={b}>
      <div className="sb-container">
        <SectionHeading heading={b.props.heading} center />
        <div className={`sb-reveal overflow-hidden rounded-[calc(var(--sb-radius)*1.4)] shadow-lg ${h}`}>
          <iframe
            title={b.props.heading || "Map"}
            src={`https://www.google.com/maps?q=${encodeURIComponent(b.props.query)}&output=embed`}
            className="h-full w-full border-0"
            loading="lazy"
            referrerPolicy="no-referrer-when-downgrade"
          />
        </div>
      </div>
    </Section>
  );
}

function Video({ b, ctx }: { b: BlockOf<"video">; ctx: Ctx }) {
  const p = b.props;
  let el: React.ReactNode = null;
  if (p.provider === "youtube" && p.videoId) {
    el = (
      <iframe
        src={`https://www.youtube-nocookie.com/embed/${p.videoId}`}
        title={p.title}
        allow="accelerometer; encrypted-media; gyroscope; picture-in-picture"
        allowFullScreen
        loading="lazy"
        className="h-full w-full border-0"
      />
    );
  } else if (p.provider === "vimeo" && p.videoId) {
    el = (
      <iframe
        src={`https://player.vimeo.com/video/${p.videoId}?dnt=1`}
        title={p.title}
        allow="fullscreen; picture-in-picture"
        loading="lazy"
        className="h-full w-full border-0"
      />
    );
  } else if (p.provider === "upload" && p.mediaId && ctx.media[p.mediaId]?.src) {
    el = <video src={ctx.media[p.mediaId]!.src} controls preload="metadata" className="h-full w-full" title={p.title} />;
  }
  if (!el) return null;
  return (
    <Section block={b}>
      <div className="sb-container max-w-4xl">
        <SectionHeading heading={p.heading} center />
        <figure className="sb-reveal">
          <div className="aspect-video overflow-hidden rounded-[calc(var(--sb-radius)*1.4)] bg-black shadow-xl">{el}</div>
          {p.caption && <figcaption className="sb-muted mt-3 text-center text-sm">{p.caption}</figcaption>}
        </figure>
      </div>
    </Section>
  );
}

function formatSize(bytes?: number) {
  if (!bytes) return "";
  return bytes > 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

function Downloads({ b, ctx }: { b: BlockOf<"downloads">; ctx: Ctx }) {
  const items = b.props.items.filter((it) => ctx.media[it.mediaId]?.src);
  if (items.length === 0) return null;
  return (
    <Section block={b}>
      <div className="sb-container max-w-3xl">
        <SectionHeading heading={b.props.heading} center />
        <ul className="space-y-3">
          {items.map((it) => {
            const m = ctx.media[it.mediaId]!;
            return (
              <li key={it.mediaId}>
                <a href={m.src} target="_blank" rel="noopener" className="sb-card sb-lift sb-focus flex items-center gap-4 p-5">
                  <span className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary-soft text-primary">
                    <Download />
                  </span>
                  <span className="flex-1">
                    <span className="block font-semibold">{it.label}</span>
                    <span className="sb-muted text-sm">
                      {(m.contentType?.split("/")[1] ?? "file").toUpperCase()} {formatSize(m.sizeBytes)}
                    </span>
                  </span>
                </a>
              </li>
            );
          })}
        </ul>
      </div>
    </Section>
  );
}

function ContactDetails({ b, ctx }: { b: BlockOf<"contactDetails">; ctx: Ctx }) {
  const p = ctx.site.profile;
  const addr = addressLines(ctx.site);
  const rows = [
    addr.length ? { icon: <Pin />, label: "Address", value: addr.join(", ") } : null,
    p.phone ? { icon: <Phone />, label: "Phone", value: p.phone, href: `tel:${p.phone.replace(/\s/g, "")}` } : null,
    p.email ? { icon: <Mail />, label: "Email", value: p.email, href: `mailto:${p.email}` } : null,
    p.officeHours ? { icon: <Clock />, label: "Office hours", value: p.officeHours } : null,
  ].filter((r): r is NonNullable<typeof r> => !!r);
  const mapQuery = p.mapQuery || (addr.length ? `${ctx.site.name}, ${addr.join(", ")}` : "");
  return (
    <Section block={b}>
      <div className="sb-container">
        <SectionHeading heading={b.props.heading} center />
        <div className={`grid gap-8 ${b.props.showMap && mapQuery ? "lg:grid-cols-2" : "mx-auto max-w-3xl"}`}>
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
            {rows.map((r) => (
              <li key={r.label} className="sb-card sb-lift sb-reveal flex gap-4 p-6">
                <span className="inline-flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-primary-soft text-primary">{r.icon}</span>
                <span>
                  <span className="sb-muted block text-sm font-semibold tracking-wide uppercase">{r.label}</span>
                  {r.href ? (
                    <a href={r.href} className="mt-1 block text-lg font-semibold break-words hover:text-primary">
                      {r.value}
                    </a>
                  ) : (
                    <span className="mt-1 block text-lg font-semibold">{r.value}</span>
                  )}
                </span>
              </li>
            ))}
          </ul>
          {b.props.showMap && mapQuery && (
            <div className="sb-reveal min-h-80 overflow-hidden rounded-[calc(var(--sb-radius)*1.4)] shadow-lg">
              <iframe
                title={`Map to ${ctx.site.name}`}
                src={`https://www.google.com/maps?q=${encodeURIComponent(mapQuery)}&output=embed`}
                className="h-full min-h-80 w-full border-0"
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
              />
            </div>
          )}
        </div>
      </div>
    </Section>
  );
}

function CustomHtml({ b }: { b: BlockOf<"customHtml"> }) {
  return (
    <Section block={b}>
      <div className="sb-container" dangerouslySetInnerHTML={{ __html: b.props.html }} />
    </Section>
  );
}

// --------------------------------------------------------------------------- dispatcher

export function RenderBlock({ block, ctx }: { block: Block; ctx: Ctx }) {
  switch (block.type) {
    case "hero":
      return <Hero b={block} ctx={ctx} />;
    case "richText":
      return <RichText b={block} />;
    case "imageText":
      return <ImageText b={block} ctx={ctx} />;
    case "image":
      return <ImageBlock b={block} ctx={ctx} />;
    case "galleryStrip":
      return <GalleryStrip b={block} ctx={ctx} />;
    case "cards":
      return <Cards b={block} ctx={ctx} />;
    case "cta":
      return <CtaBlock b={block} />;
    case "stats":
      return <Stats b={block} />;
    case "testimonials":
      return <Testimonials b={block} />;
    case "steps":
      return <Steps b={block} />;
    case "faq":
      return <Faq b={block} ctx={ctx} />;
    case "map":
      return <MapBlock b={block} />;
    case "video":
      return <Video b={block} ctx={ctx} />;
    case "downloads":
      return <Downloads b={block} ctx={ctx} />;
    case "contactDetails":
      return <ContactDetails b={block} ctx={ctx} />;
    case "noticesFeed":
      return (
        <Section block={block}>
          <NoticesFeed host={ctx.host} heading={block.props.heading} limit={block.props.limit} />
        </Section>
      );
    case "newsFeed":
      return (
        <Section block={block}>
          <NewsFeed host={ctx.host} heading={block.props.heading} limit={block.props.limit} />
        </Section>
      );
    case "eventsFeed":
      return (
        <Section block={block}>
          <EventsFeed host={ctx.host} heading={block.props.heading} limit={block.props.limit} show={block.props.show} />
        </Section>
      );
    case "albumsFeed":
      return (
        <Section block={block}>
          <AlbumsFeed host={ctx.host} heading={block.props.heading} limit={block.props.limit} />
        </Section>
      );
    case "staffGrid":
      return (
        <Section block={block}>
          <StaffGrid host={ctx.host} heading={block.props.heading} intro={block.props.intro} department={block.props.department} />
        </Section>
      );
    case "careersFeed":
      return (
        <Section block={block}>
          <CareersFeed host={ctx.host} heading={block.props.heading} intro={block.props.intro} />
        </Section>
      );
    case "enquiryForm":
      return (
        <Section block={block} defaultBg="muted">
          <div className="sb-container max-w-3xl">
            <SectionHeading heading={block.props.heading} intro={block.props.intro} center />
            <div className="sb-card sb-reveal p-6 md:p-10">
              <EnquiryForm />
            </div>
          </div>
        </Section>
      );
    case "customHtml":
      return <CustomHtml b={block} />;
    default:
      return null;
  }
}
