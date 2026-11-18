"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { ChevronDown, Close, Menu } from "./Icons";

export type NavItem = { label: string; href?: string; children?: { label: string; href: string }[] };

export function SiteHeader({
  name,
  logo,
  items,
  cta,
}: {
  name: string;
  logo: React.ReactNode;
  items: NavItem[];
  cta?: { label: string; href: string };
}) {
  const pathname = usePathname() || "/";
  // The menu belongs to the page it was opened on, so navigating closes it.
  const [openOn, setOpenOn] = useState<string | null>(null);
  const open = openOn === pathname;
  useEffect(() => {
    document.body.style.overflow = open ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  const active = (href?: string) =>
    !!href && (href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(href + "/"));

  return (
    <header className="sb-header">
      <div className="sb-container flex h-[4.5rem] items-center justify-between gap-6">
        <Link href="/" className="sb-focus flex shrink-0 items-center gap-3 rounded-lg" aria-label={`${name} home`}>
          {logo}
        </Link>

        <nav aria-label="Main" className="hidden lg:block">
          <ul className="flex items-center gap-1">
            {items.map((it) =>
              it.children?.length ? (
                <li key={it.label} className="sb-has-dropdown relative">
                  <button
                    type="button"
                    className="sb-nav-link sb-focus inline-flex items-center gap-1"
                    aria-haspopup="true"
                    aria-current={it.children.some((c) => active(c.href)) ? "page" : undefined}
                  >
                    {it.label}
                    <ChevronDown width={16} height={16} />
                  </button>
                  <div className="sb-dropdown">
                    {it.children.map((c) => (
                      <Link key={c.href + c.label} href={c.href} aria-current={active(c.href) ? "page" : undefined}>
                        {c.label}
                      </Link>
                    ))}
                  </div>
                </li>
              ) : (
                <li key={it.label}>
                  <Link
                    href={it.href ?? "/"}
                    className="sb-nav-link sb-focus"
                    aria-current={active(it.href) ? "page" : undefined}
                  >
                    {it.label}
                  </Link>
                </li>
              ),
            )}
          </ul>
        </nav>

        <div className="flex items-center gap-2">
          {cta && (
            <Link href={cta.href} className="sb-btn sb-btn-primary sb-focus hidden sm:inline-flex">
              {cta.label}
            </Link>
          )}
          <button
            type="button"
            className="sb-focus inline-flex h-11 w-11 items-center justify-center rounded-full bg-primary-soft text-primary lg:hidden"
            aria-label={open ? "Close menu" : "Open menu"}
            aria-expanded={open}
            aria-controls="sb-mobile-nav"
            onClick={() => setOpenOn(open ? null : pathname)}
          >
            {open ? <Close /> : <Menu />}
          </button>
        </div>
      </div>

      {open && (
        <nav
          id="sb-mobile-nav"
          aria-label="Mobile"
          className="fixed inset-x-0 top-[4.5rem] bottom-0 z-40 overflow-y-auto bg-white px-5 pt-4 pb-10 lg:hidden"
        >
          <ul className="flex flex-col gap-1">
            {items.map((it) => (
              <li key={it.label}>
                {it.href && !it.children?.length ? (
                  <Link
                    href={it.href}
                    className="block rounded-xl px-4 py-3 text-lg font-semibold hover:bg-primary-soft"
                    aria-current={active(it.href) ? "page" : undefined}
                  >
                    {it.label}
                  </Link>
                ) : (
                  <>
                    <p className="px-4 pt-4 pb-1 text-xs font-bold tracking-widest text-ink-muted uppercase">
                      {it.label}
                    </p>
                    {it.children?.map((c) => (
                      <Link
                        key={c.href + c.label}
                        href={c.href}
                        className="block rounded-xl px-4 py-2.5 text-base font-medium hover:bg-primary-soft"
                      >
                        {c.label}
                      </Link>
                    ))}
                  </>
                )}
              </li>
            ))}
          </ul>
          {cta && (
            <Link href={cta.href} className="sb-btn sb-btn-primary mt-6 w-full justify-center">
              {cta.label}
            </Link>
          )}
        </nav>
      )}
    </header>
  );
}
