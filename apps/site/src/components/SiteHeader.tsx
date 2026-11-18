"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { ChevronDown, Close, Menu } from "./Icons";

export type NavItem = {
  label: string;
  href?: string;
  children?: { label: string; href: string }[];
};

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
  // Desktop dropdown opened by click/tap (hover and keyboard focus also open it via CSS).
  const [drop, setDrop] = useState<string | null>(null);
  const toggleDrop = (label: string) => {
    const key = label + "|" + pathname;
    setDrop((d) => (d === key ? null : key));
  };
  useEffect(() => {
    if (!open) return;
    // Lock page scroll only while the mobile menu is open; Escape closes it.
    document.body.style.overflow = "hidden";
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpenOn(null);
    window.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = "";
      window.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const active = (href?: string) =>
    !!href &&
    (href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(href + "/"));

  return (
    <>
      <header className="sb-header">
        <div className="sb-container flex h-[4.5rem] items-center justify-between gap-6">
          <Link
            href="/"
            className="sb-focus flex shrink-0 items-center gap-3 rounded-lg"
            aria-label={`${name} home`}
          >
            {logo}
          </Link>

          <nav aria-label="Main" className="hidden lg:block">
            <ul className="flex items-center gap-1">
              {items.map((it) =>
                it.children?.length ? (
                  <li
                    key={it.label}
                    className="sb-has-dropdown relative"
                    data-open={drop === it.label + "|" + pathname ? "" : undefined}
                    onMouseLeave={() => setDrop(null)}
                  >
                    {it.href ? (
                      <span className="inline-flex items-center">
                        <Link
                          href={it.href}
                          className="sb-nav-link sb-focus inline-flex items-center pr-1"
                          aria-current={
                            active(it.href) || it.children.some((c) => active(c.href))
                              ? "page"
                              : undefined
                          }
                        >
                          {it.label}
                        </Link>
                        <button
                          type="button"
                          className="sb-focus -ml-1 inline-flex h-8 w-7 items-center justify-center rounded-full"
                          aria-label={`${it.label} menu`}
                          aria-haspopup="true"
                          aria-expanded={drop === it.label + "|" + pathname}
                          onClick={() => toggleDrop(it.label)}
                        >
                          <ChevronDown width={16} height={16} />
                        </button>
                      </span>
                    ) : (
                      <button
                        type="button"
                        className="sb-nav-link sb-focus inline-flex items-center gap-1"
                        aria-haspopup="true"
                        aria-expanded={drop === it.label + "|" + pathname}
                        aria-current={it.children.some((c) => active(c.href)) ? "page" : undefined}
                        onClick={() => toggleDrop(it.label)}
                      >
                        {it.label}
                        <ChevronDown width={16} height={16} />
                      </button>
                    )}
                    <div className="sb-dropdown">
                      {it.children.map((c) => (
                        <Link
                          key={c.href + c.label}
                          href={c.href}
                          aria-current={active(c.href) ? "page" : undefined}
                        >
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
              <Link
                href={cta.href}
                className="sb-btn sb-btn-primary sb-focus hidden sm:inline-flex"
              >
                {cta.label}
              </Link>
            )}
            <button
              type="button"
              className="sb-focus bg-primary-soft text-primary inline-flex h-11 w-11 items-center justify-center rounded-full lg:hidden"
              aria-label={open ? "Close menu" : "Open menu"}
              aria-expanded={open}
              aria-controls="sb-mobile-nav"
              onClick={() => setOpenOn(open ? null : pathname)}
            >
              {open ? <Close /> : <Menu />}
            </button>
          </div>
        </div>
      </header>
      {open && (
        <nav
          id="sb-mobile-nav"
          aria-label="Mobile"
          // Outside <header>: its backdrop-filter would otherwise trap this fixed panel inside it.
          className="fixed inset-x-0 top-[4.5rem] bottom-0 z-50 overflow-y-auto overscroll-contain bg-white px-5 pt-4 pb-10 lg:hidden"
          onClick={(e) => {
            if ((e.target as HTMLElement).closest("a")) setOpenOn(null);
          }}
        >
          <ul className="flex flex-col gap-1">
            {items.map((it) => (
              <li key={it.label}>
                {it.href && !it.children?.length ? (
                  <Link
                    href={it.href}
                    className="hover:bg-primary-soft block rounded-xl px-4 py-3 text-lg font-semibold"
                    aria-current={active(it.href) ? "page" : undefined}
                  >
                    {it.label}
                  </Link>
                ) : (
                  <>
                    {it.href ? (
                      <Link
                        href={it.href}
                        className="hover:bg-primary-soft block rounded-xl px-4 py-3 text-lg font-semibold"
                        aria-current={active(it.href) ? "page" : undefined}
                      >
                        {it.label}
                      </Link>
                    ) : (
                      <p className="text-ink-muted px-4 pt-4 pb-1 text-xs font-bold tracking-widest uppercase">
                        {it.label}
                      </p>
                    )}
                    {it.children?.map((c) => (
                      <Link
                        key={c.href + c.label}
                        href={c.href}
                        className="hover:bg-primary-soft block rounded-xl px-4 py-2.5 text-base font-medium"
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
    </>
  );
}
