import Link from "next/link";
import type { PublicSite } from "@/lib/types";
import { t } from "@/lib/types";
import { Mail, Phone, Pin } from "./Icons";

const SOCIAL_LABELS: Record<string, string> = {
  facebook: "Facebook",
  instagram: "Instagram",
  youtube: "YouTube",
  x: "X (Twitter)",
  linkedin: "LinkedIn",
};

export function addressLines(site: PublicSite): string[] {
  const a = site.profile.address ?? {};
  return [
    a.line1,
    a.line2,
    [a.city, a.pincode].filter(Boolean).join(" – "),
    [a.state, a.country].filter(Boolean).join(", "),
  ].filter((l): l is string => !!l && l.trim().length > 0);
}

export function SiteFooter({ site, lang }: { site: PublicSite; lang: string }) {
  const p = site.profile;
  const year = new Date().getFullYear();
  const social = Object.entries(p.social ?? {}).filter(([, v]) => !!v);
  return (
    <footer className="relative mt-auto overflow-hidden bg-[color-mix(in_srgb,var(--sb-color-primary)_92%,#000)] text-white">
      <div
        className="sb-blob"
        style={{ width: 420, height: 420, right: -120, top: -160, background: "var(--sb-color-accent)" }}
      />
      <div className="sb-container relative grid gap-10 py-16 md:grid-cols-12">
        <div className="md:col-span-5">
          <p className="font-heading text-2xl font-bold">{site.name}</p>
          {p.tagline && <p className="mt-2 text-white/90">{p.tagline}</p>}
          {(p.boardName || p.board || p.established) && (
            <p className="mt-4 text-sm text-white/90">
              {[p.boardName || p.board, p.established ? `Established ${p.established}` : null]
                .filter(Boolean)
                .join(" · ")}
            </p>
          )}
          {social.length > 0 && (
            <ul className="mt-6 flex flex-wrap gap-2">
              {social.map(([k, v]) => (
                <li key={k}>
                  <a
                    href={v}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="sb-focus inline-flex rounded-full bg-white/10 px-4 py-2 text-sm font-medium transition hover:bg-white/20"
                  >
                    {SOCIAL_LABELS[k] ?? k}
                  </a>
                </li>
              ))}
            </ul>
          )}
        </div>

        <nav aria-label="Footer" className="md:col-span-3">
          <p className="text-sm font-bold tracking-widest text-white/90 uppercase">Quick links</p>
          <ul className="mt-4 space-y-2.5">
            {site.footer.map((m) =>
              m.href ? (
                <li key={m.href}>
                  <Link href={m.href} className="sb-focus text-white/95 transition hover:text-white hover:underline">
                    {t(m.label, lang)}
                  </Link>
                </li>
              ) : null,
            )}
          </ul>
        </nav>

        <div className="md:col-span-4">
          <p className="text-sm font-bold tracking-widest text-white/90 uppercase">Visit us</p>
          <ul className="mt-4 space-y-3 text-white/95">
            {addressLines(site).length > 0 && (
              <li className="flex gap-3">
                <Pin className="mt-0.5 shrink-0" />
                <span>
                  {addressLines(site).map((l) => (
                    <span key={l} className="block">
                      {l}
                    </span>
                  ))}
                </span>
              </li>
            )}
            {p.phone && (
              <li className="flex gap-3">
                <Phone className="mt-0.5 shrink-0" />
                <a href={`tel:${p.phone.replace(/\s/g, "")}`} className="hover:underline">
                  {p.phone}
                </a>
              </li>
            )}
            {p.email && (
              <li className="flex gap-3">
                <Mail className="mt-0.5 shrink-0" />
                <a href={`mailto:${p.email}`} className="break-all hover:underline">
                  {p.email}
                </a>
              </li>
            )}
          </ul>
        </div>
      </div>
      <div className="relative border-t border-white/10">
        <div className="sb-container flex flex-col gap-2 py-5 text-sm text-white/90 sm:flex-row sm:items-center sm:justify-between">
          <p>
            © {year} {site.name}. All rights reserved.
          </p>
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span>
              Website by{" "}
              <a href="https://aurora-blossom.com" target="_blank" rel="noopener" className="font-semibold text-white hover:underline">
                aurora-blossom.com
              </a>
            </span>
            {site.showBadge && (
              <span className="rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-white">
                Powered by ShikshaBlox
              </span>
            )}
          </p>
        </div>
      </div>
    </footer>
  );
}
