import Link from "next/link";
import { footerNav, site } from "@/data/site";
import { Icon } from "./Icon";
import { Logo } from "./Logo";

export function SiteFooter() {
  const year = new Date().getFullYear();
  return (
    <footer className="bg-night text-indigo-100">
      <div className="container-x grid gap-12 py-16 lg:grid-cols-[1.3fr_2fr]">
        <div>
          <Link prefetch={false} href="/" aria-label="ShikshaBlox home">
            <Logo inverted />
          </Link>
          <p className="mt-5 max-w-sm leading-relaxed text-indigo-200">
            The modular School ERP and school website platform that grows from your first website to your fifth campus.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <Link prefetch={false}
              href="/contact"
              className="inline-flex min-h-11 items-center gap-2 rounded-full bg-white px-5 text-sm font-semibold text-[#1e1b4b] hover:bg-indigo-50"
            >
              Book a free demo <Icon name="arrow" className="size-4" />
            </Link>
            {site.contact.whatsapp && (
              <a
                href={`https://wa.me/${site.contact.whatsapp}`}
                className="inline-flex min-h-11 items-center gap-2 rounded-full border border-white/25 px-5 text-sm font-semibold text-white hover:bg-white/10"
                rel="noopener"
                target="_blank"
              >
                <Icon name="whatsapp" className="size-4" /> WhatsApp us
              </a>
            )}
          </div>
        </div>
        <div className="grid grid-cols-2 gap-8 sm:grid-cols-4">
          {footerNav.map((col) => (
            <div key={col.title}>
              <h2 className="text-sm font-bold tracking-[0.08em] text-white uppercase">{col.title}</h2>
              <ul className="mt-4 space-y-1">
                {col.links.map((l) => (
                  <li key={l.href}>
                    <Link prefetch={false} href={l.href} className="inline-flex min-h-10 items-center text-indigo-200 transition-colors hover:text-white">
                      {l.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>
      <div className="border-t border-white/10">
        <div className="container-x flex flex-col gap-3 py-6 text-sm text-indigo-200 sm:flex-row sm:items-center sm:justify-between">
          <p data-testid="copyright">
            © {year}{" "}
            <a href={site.company.url} className="font-semibold text-white underline-offset-4 hover:underline" rel="noopener">
              Aurora Blossom
            </a>{" "}
            (aurora-blossom.com). All rights reserved. ShikshaBlox is a School ERP Software product of Aurora Blossom.
          </p>
          <p className="shrink-0">Made in India · Hosted in India</p>
        </div>
      </div>
    </footer>
  );
}
