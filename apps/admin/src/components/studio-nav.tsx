"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@shikshablox/ui";

export type NavItem = { href: string; label: string };

/** Section navigation for the studio; the current page is marked for assistive technology. */
export function StudioNav({ items }: { items: NavItem[] }) {
  const pathname = usePathname();
  return (
    <nav aria-label="Studio" className="border-b border-black/5 bg-white/60">
      <ul className="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-4">
        {items.map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "inline-flex h-11 items-center border-b-2 px-3 text-sm font-medium whitespace-nowrap transition",
                  active
                    ? "border-primary text-primary"
                    : "text-ink-muted hover:text-ink border-transparent hover:border-black/10",
                )}
              >
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
