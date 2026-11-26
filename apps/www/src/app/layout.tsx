import type { Metadata, Viewport } from "next";
import { Plus_Jakarta_Sans } from "next/font/google";
import type { ReactNode } from "react";
import { SiteFooter } from "@/components/SiteFooter";
import { SiteHeader } from "@/components/SiteHeader";
import { JsonLd } from "@/components/ui";
import { site } from "@/data/site";
import { organizationLd } from "@/lib/seo";
import "./globals.css";

// Headings use Plus Jakarta Sans (self-hosted at build time, no request to Google from visitors).
// display "optional": on a slow first visit headings stay in the fallback font instead of re-painting late.
// Body text uses the device's system UI font: zero bytes to download, so text paints immediately.
// One variable font file covers every heading weight, so a single preloaded request serves them all.
const jakarta = Plus_Jakarta_Sans({ subsets: ["latin"], variable: "--font-jakarta", display: "optional" });

export const metadata: Metadata = {
  metadataBase: new URL(site.url),
  title: { default: `${site.name}: School ERP Software & School Website Platform`, template: `%s | ${site.name}` },
  description: site.description,
  applicationName: site.name,
  authors: [{ name: site.company.name, url: site.company.url }],
  publisher: site.company.name,
  robots: site.noindex ? { index: false, follow: false } : { index: true, follow: true },
  formatDetection: { telephone: false },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#ffffff" },
    { media: "(prefers-color-scheme: dark)", color: "#0a0d24" },
  ],
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en-IN" className={jakarta.variable}>
      <body className="min-h-dvh antialiased">
        <a
          href="#main"
          className="sr-only z-[60] rounded-full bg-brand px-5 py-3 font-semibold text-brand-ink focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
        >
          Skip to content
        </a>
        <SiteHeader />
        <main id="main">{children}</main>
        <SiteFooter />
        <JsonLd data={organizationLd()} />
      </body>
    </html>
  );
}
