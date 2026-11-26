import type { ReactNode } from "react";
import { notFound } from "next/navigation";
import { getApplicationForm, getSite } from "@/lib/api";
import { normaliseHost } from "@/lib/host";
import { brandStyle } from "@/lib/theme";
import { schoolJsonLd } from "@/lib/seo";
import { t } from "@/lib/types";
import { Img } from "@/components/Img";
import { SiteHeader, type NavItem } from "@/components/SiteHeader";
import { SiteFooter } from "@/components/SiteFooter";

export default async function SiteLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: Promise<{ host: string }>;
}) {
  const { host: raw } = await params;
  const host = normaliseHost(decodeURIComponent(raw));
  if (!host) notFound();
  const [site, form] = await Promise.all([getSite(host), getApplicationForm(host)]);
  if (!site) notFound();
  const lang = site.defaultLanguage;

  const items: NavItem[] = site.header.map((m) => ({
    label: t(m.label, lang),
    href: m.href,
    children: m.children
      ?.filter((c) => c.href)
      .map((c) => ({ label: t(c.label, lang), href: c.href! })),
  }));
  const hasAdmissions = site.header.some(
    (m) => m.href === "/admissions" || m.children?.some((c) => c.href === "/admissions"),
  );

  const logo =
    site.logoId && site.media[site.logoId]?.src ? (
      <Img
        id={site.logoId}
        media={site.media}
        priority
        sizes="240px"
        alt={site.name}
        className="h-11 w-auto max-w-[220px] object-contain"
      />
    ) : (
      <>
        <span className="font-heading bg-primary text-on-primary flex h-11 w-11 items-center justify-center rounded-2xl text-xl font-extrabold">
          {site.name.charAt(0)}
        </span>
        <span className="font-heading text-lg leading-tight font-bold">{site.name}</span>
      </>
    );

  return (
    <div
      data-theme={site.theme}
      style={brandStyle(site.brand)}
      className="bg-surface text-ink flex min-h-dvh flex-col overflow-x-clip"
    >
      <a
        href="#main"
        className="bg-primary text-on-primary sr-only z-50 rounded-full px-4 py-2 focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Skip to content
      </a>
      <SiteHeader
        name={site.name}
        logo={logo}
        items={items}
        cta={
          form
            ? { label: "Apply now", href: "/apply" }
            : hasAdmissions && site.profile.admissionsOpen !== false
              ? { label: "Apply now", href: "/admissions#enquire" }
              : undefined
        }
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(schoolJsonLd(site, host)).replace(/</g, "\\u003c"),
        }}
      />
      <main id="main" className="flex-1">
        {children}
      </main>
      <SiteFooter site={site} lang={lang} />
    </div>
  );
}
