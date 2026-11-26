"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useState } from "react";
import type { NavItem } from "@/data/site";
import { Icon } from "./Icon";

/**
 * Mobile menu: a panel under the header that scrolls on its own. The page is never scroll-locked,
 * and every link (including sub-links) is visible without extra taps.
 */
export function MobileNav({ items }: { items: NavItem[] }) {
  const [open, setOpen] = useState(false);
  const [lastPath, setLastPath] = useState<string | null>(null);
  const pathname = usePathname();
  const id = useId();

  // Close after navigation (state is derived during render, no effect needed).
  if (pathname !== lastPath) {
    setLastPath(pathname);
    if (open) setOpen(false);
  }

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  return (
    <div className="lg:[@media(hover:hover)]:hidden">
      <button
        type="button"
        className="grid size-11 place-items-center rounded-full border border-line bg-card text-ink"
        aria-expanded={open}
        aria-controls={id}
        aria-label={open ? "Close menu" : "Open menu"}
        onClick={() => setOpen((v) => !v)}
      >
        <Icon name={open ? "close" : "menu"} className="size-5" />
      </button>
      <div
        id={id}
        hidden={!open}
        className="fixed inset-x-0 top-[4.25rem] bottom-0 z-40 overflow-y-auto overscroll-contain border-t border-line bg-bg px-4 pt-4 pb-10"
      >
        <nav aria-label="Mobile">
          <ul className="space-y-1">
            {items.map((item) => (
              <li key={item.label}>
                {item.children ? (
                  <div className="py-2">
                    <p className="px-3 pb-1 text-xs font-bold tracking-[0.08em] text-ink-3 uppercase">{item.label}</p>
                    <ul>
                      {item.children.map((c) => (
                        <li key={c.href}>
                          <Link prefetch={false}
                            href={c.href}
                            className="flex min-h-12 items-center justify-between rounded-xl px-3 text-[1.02rem] font-medium text-ink hover:bg-bg-soft"
                          >
                            {c.label}
                            {c.note && <span className="ml-3 text-xs text-ink-3">{c.note}</span>}
                          </Link>
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : (
                  <Link prefetch={false}
                    href={item.href}
                    className="flex min-h-12 items-center rounded-xl px-3 text-[1.02rem] font-semibold text-ink hover:bg-bg-soft"
                  >
                    {item.label}
                  </Link>
                )}
              </li>
            ))}
          </ul>
          <div className="mt-6 grid gap-3 border-t border-line pt-6">
            <Link prefetch={false}
              href="/contact"
              className="flex min-h-12 items-center justify-center rounded-full bg-brand font-semibold text-brand-ink"
            >
              Book a free demo
            </Link>
            <Link prefetch={false}
              href="/schools"
              className="flex min-h-12 items-center justify-center rounded-full border border-line font-semibold text-ink"
            >
              See a live school website
            </Link>
          </div>
        </nav>
      </div>
    </div>
  );
}
