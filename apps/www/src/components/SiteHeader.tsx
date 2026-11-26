import Link from "next/link";
import { nav } from "@/data/site";
import { Icon } from "./Icon";
import { Logo } from "./Logo";
import { MobileNav } from "./MobileNav";

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-50 border-b border-line/70 bg-bg/85 backdrop-blur-xl supports-[backdrop-filter]:bg-bg/75">
      <div className="container-x flex h-[4.25rem] items-center justify-between gap-6">
        <Link prefetch={false} href="/" aria-label="ShikshaBlox home" className="shrink-0">
          <Logo />
        </Link>

        {/* Hover menus only where hovering is possible; touch tablets (e.g. iPad landscape) get the tap menu. */}
        <nav aria-label="Main" className="hidden lg:[@media(hover:hover)]:block">
          <ul className="flex items-center gap-1">
            {nav.map((item) => (
              <li key={item.label} className="group relative">
                <Link prefetch={false}
                  href={item.href}
                  className="inline-flex min-h-11 items-center gap-1 rounded-full px-4 text-[0.95rem] font-medium text-ink-2 transition-colors hover:bg-bg-soft hover:text-ink"
                >
                  {item.label}
                  {item.children && (
                    <Icon name="chevron" className="size-4 transition-transform duration-300 group-hover:rotate-180" />
                  )}
                </Link>
                {item.children && (
                  <div className="invisible absolute top-full left-1/2 w-80 -translate-x-1/2 translate-y-2 pt-3 opacity-0 transition-all duration-300 group-focus-within:visible group-focus-within:translate-y-0 group-focus-within:opacity-100 group-hover:visible group-hover:translate-y-0 group-hover:opacity-100">
                    <ul className="card p-2">
                      {item.children.map((c) => (
                        <li key={c.href}>
                          <Link prefetch={false} href={c.href} className="block rounded-xl px-4 py-3 transition-colors hover:bg-bg-soft">
                            <span className="block font-semibold text-ink">{c.label}</span>
                            {c.note && <span className="block text-sm text-ink-3">{c.note}</span>}
                          </Link>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </nav>

        <div className="flex items-center gap-2">
          <Link prefetch={false}
            href="/contact"
            className="hidden min-h-11 items-center rounded-full bg-brand px-5 text-[0.95rem] font-semibold text-brand-ink transition-colors hover:bg-brand-strong sm:inline-flex"
          >
            Book a demo
          </Link>
          <MobileNav items={nav} />
        </div>
      </div>
    </header>
  );
}
