import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";
import { sizeOf } from "@/lib/images";
import { Icon } from "./Icon";

type BtnVariant = "primary" | "secondary" | "ghost" | "light" | "accent";

const btn: Record<BtnVariant, string> = {
  primary:
    "bg-brand text-brand-ink shadow-[0_10px_24px_-10px_color-mix(in_oklab,var(--c-brand)_70%,transparent)] hover:bg-brand-strong",
  secondary: "bg-card text-ink border border-line hover:border-brand hover:text-brand",
  ghost: "text-brand hover:bg-brand-soft",
  light: "bg-white text-[#1e1b4b] hover:bg-indigo-50",
  accent: "bg-accent text-[#2b1700] hover:brightness-105",
};

export function ButtonLink({
  href,
  children,
  variant = "primary",
  className = "",
  arrow = false,
  external = false,
}: {
  href: string;
  children: ReactNode;
  variant?: BtnVariant;
  className?: string;
  arrow?: boolean;
  external?: boolean;
}) {
  const cls = `group inline-flex min-h-12 items-center justify-center gap-2 rounded-full px-6 text-[0.95rem] font-semibold transition-all duration-300 ${btn[variant]} ${className}`;
  const inner = (
    <>
      {children}
      {arrow && <Icon name="arrow" className="size-4 transition-transform duration-300 group-hover:translate-x-1" />}
    </>
  );
  return external ? (
    <a href={href} className={cls} target="_blank" rel="noopener">
      {inner}
    </a>
  ) : (
    <Link prefetch={false} href={href} className={cls}>
      {inner}
    </Link>
  );
}

export function SectionHeading({
  eyebrow,
  title,
  lead,
  center = false,
  as: As = "h2",
}: {
  eyebrow?: string;
  title: ReactNode;
  lead?: ReactNode;
  center?: boolean;
  as?: "h1" | "h2";
}) {
  return (
    <div className={`max-w-3xl ${center ? "mx-auto text-center" : ""}`}>
      {eyebrow && <p className="eyebrow">{eyebrow}</p>}
      <As className="mt-3 font-display text-3xl leading-[1.12] font-extrabold tracking-tight text-balance text-ink sm:text-4xl lg:text-[2.75rem]">
        {title}
      </As>
      {lead && <p className="mt-4 text-lg leading-relaxed text-pretty text-ink-2">{lead}</p>}
    </div>
  );
}

export function Breadcrumbs({ items }: { items: { name: string; path: string }[] }) {
  return (
    <nav aria-label="Breadcrumb" className="text-sm text-ink-3">
      <ol className="flex flex-wrap items-center gap-1.5">
        {items.map((i, n) => (
          <li key={i.path} className="flex items-center gap-1.5">
            {n > 0 && <span aria-hidden="true">/</span>}
            {n < items.length - 1 ? (
              <Link prefetch={false} href={i.path} className="hover:text-brand">
                {i.name}
              </Link>
            ) : (
              <span aria-current="page" className="text-ink-2">
                {i.name}
              </span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}

export function PageHero({
  crumbs,
  eyebrow,
  title,
  lead,
  children,
  aside,
}: {
  crumbs: { name: string; path: string }[];
  eyebrow?: ReactNode;
  title: ReactNode;
  lead?: ReactNode;
  children?: ReactNode;
  aside?: ReactNode;
}) {
  return (
    <section className="bg-mesh relative overflow-hidden border-b border-line">
      <div className="bg-grid pointer-events-none absolute inset-0 opacity-60" aria-hidden="true" />
      <div className={`container-x relative grid gap-10 py-14 sm:py-20 ${aside ? "lg:grid-cols-[1.05fr_0.95fr] lg:items-center" : ""}`}>
        <div>
          <Breadcrumbs items={crumbs} />
          {eyebrow && <div className="mt-6">{eyebrow}</div>}
          <h1 className="mt-4 font-display text-4xl leading-[1.08] font-extrabold tracking-tight text-balance text-ink sm:text-5xl lg:text-[3.4rem]">
            {title}
          </h1>
          {lead && <p className="mt-5 max-w-2xl text-lg leading-relaxed text-pretty text-ink-2 sm:text-xl">{lead}</p>}
          {children && <div className="mt-8 flex flex-wrap gap-3">{children}</div>}
        </div>
        {aside}
      </div>
    </section>
  );
}

export function Photo({
  src,
  alt,
  sizes,
  className = "",
  priority = false,
  fit = "cover",
}: {
  src: string;
  alt: string;
  sizes: string;
  className?: string;
  priority?: boolean;
  fit?: "cover" | "contain";
}) {
  const { w, h } = sizeOf(src);
  return (
    <Image
      src={src}
      alt={alt}
      width={w}
      height={h}
      sizes={sizes}
      priority={priority}
      quality={70}
      className={`${fit === "cover" ? "object-cover" : "object-contain"} ${className}`}
    />
  );
}

export function BrowserFrame({
  src,
  alt,
  url,
  sizes,
  priority = false,
  className = "",
}: {
  src: string;
  alt: string;
  url: string;
  sizes: string;
  priority?: boolean;
  className?: string;
}) {
  return (
    <figure className={`overflow-hidden rounded-2xl border border-line bg-card shadow-pop ${className}`}>
      <div className="flex items-center gap-2 border-b border-line bg-bg-soft px-4 py-2.5" aria-hidden="true">
        <span className="size-2.5 rounded-full bg-[#ff5f57]" />
        <span className="size-2.5 rounded-full bg-[#febc2e]" />
        <span className="size-2.5 rounded-full bg-[#28c840]" />
        <span className="ml-3 truncate rounded-md bg-card px-3 py-1 text-xs text-ink-3">{url}</span>
      </div>
      <Photo src={src} alt={alt} sizes={sizes} priority={priority} className="block h-auto w-full" />
    </figure>
  );
}

export function PhoneFrame({ src, alt, className = "" }: { src: string; alt: string; className?: string }) {
  return (
    <figure className={`rounded-[2.2rem] border-[6px] border-[#11143a] bg-[#11143a] shadow-pop ${className}`}>
      <div className="relative overflow-hidden rounded-[1.7rem] bg-white">
        <Photo src={src} alt={alt} sizes="(min-width: 1024px) 180px, 30vw" className="block h-auto w-full" />
      </div>
    </figure>
  );
}

export function FaqList({ items }: { items: { q: string; a: string }[] }) {
  return (
    <div className="divide-y divide-line overflow-hidden rounded-2xl border border-line bg-card">
      {items.map((f) => (
        <details key={f.q} className="faq group">
          <summary className="flex min-h-14 cursor-pointer list-none items-center justify-between gap-4 px-5 py-4 text-left font-semibold text-ink transition-colors hover:text-brand sm:px-6">
            <span>{f.q}</span>
            <span className="faq-icon grid size-8 shrink-0 place-items-center rounded-full bg-brand-soft text-brand transition-transform duration-300">
              <Icon name="plus" className="size-4" />
            </span>
          </summary>
          <p className="px-5 pb-5 leading-relaxed text-ink-2 sm:px-6">{f.a}</p>
        </details>
      ))}
    </div>
  );
}

export function JsonLd({ data }: { data: object | object[] }) {
  return (
    <script
      type="application/ld+json"
      // JSON.stringify output with "<" escaped cannot break out of the script element.
      dangerouslySetInnerHTML={{ __html: JSON.stringify(data).replace(/</g, "\\u003c") }}
    />
  );
}

export function CtaBand({
  title = "See ShikshaBlox running for a real school",
  text = "Book a free 30-minute demo. We'll show you a sample school website, walk through the modules your school needs, and share founding-school pricing.",
}: {
  title?: string;
  text?: string;
}) {
  return (
    <section className="container-x py-16 sm:py-24">
      <div className="relative overflow-hidden rounded-[2rem] bg-night px-6 py-14 text-center sm:px-12 sm:py-20">
        <div
          className="pointer-events-none absolute inset-0 opacity-90"
          aria-hidden="true"
          style={{
            backgroundImage:
              "radial-gradient(36rem 20rem at 15% 0%, rgb(99 102 241 / .45), transparent 70%), radial-gradient(30rem 18rem at 90% 100%, rgb(245 158 11 / .30), transparent 70%)",
          }}
        />
        <div className="relative mx-auto max-w-2xl">
          <h2 className="font-display text-3xl leading-tight font-extrabold tracking-tight text-balance text-white sm:text-4xl">
            {title}
          </h2>
          <p className="mt-4 text-lg leading-relaxed text-indigo-100">{text}</p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <ButtonLink href="/contact" variant="accent" arrow>
              Book a free demo
            </ButtonLink>
            <ButtonLink href="/pricing" variant="light">
              See pricing
            </ButtonLink>
          </div>
        </div>
      </div>
    </section>
  );
}
