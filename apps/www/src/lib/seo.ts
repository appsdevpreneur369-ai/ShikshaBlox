import type { Metadata } from "next";
import { site } from "@/data/site";

export function pageMetadata(opts: { title: string; description: string; path: string; image?: string }): Metadata {
  const url = `${site.url}${opts.path === "/" ? "" : opts.path}`;
  return {
    title: opts.title,
    description: opts.description,
    alternates: { canonical: url },
    openGraph: {
      type: "website",
      siteName: site.name,
      locale: "en_IN",
      url,
      title: opts.title,
      description: opts.description,
      // Pages set their own openGraph object, which replaces the root one, so name the shared image explicitly.
      images: [{ url: opts.image ?? "/opengraph-image", width: 1200, height: 630, alt: "ShikshaBlox School ERP" }],
    },
    twitter: { card: "summary_large_image", title: opts.title, description: opts.description, images: [opts.image ?? "/opengraph-image"] },
  };
}

export function organizationLd() {
  return {
    "@context": "https://schema.org",
    "@type": "Organization",
    name: site.company.name,
    url: site.company.url,
    brand: { "@type": "Brand", name: site.name },
    logo: `${site.url}/brand/shikshablox-logo.png`,
  };
}

export function softwareLd(offers: { name: string; price: number }[]) {
  return {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: site.name,
    applicationCategory: "BusinessApplication",
    applicationSubCategory: "School ERP Software",
    operatingSystem: "Web, Android, iOS (PWA)",
    url: site.url,
    description: site.description,
    publisher: { "@type": "Organization", name: site.company.name, url: site.company.url },
    offers: offers.map((o) => ({
      "@type": "Offer",
      name: o.name,
      price: o.price,
      priceCurrency: "INR",
    })),
  };
}

export function faqLd(items: { q: string; a: string }[]) {
  return {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: items.map((i) => ({
      "@type": "Question",
      name: i.q,
      acceptedAnswer: { "@type": "Answer", text: i.a },
    })),
  };
}

export function breadcrumbLd(items: { name: string; path: string }[]) {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: items.map((i, n) => ({
      "@type": "ListItem",
      position: n + 1,
      name: i.name,
      item: `${site.url}${i.path === "/" ? "" : i.path}`,
    })),
  };
}
