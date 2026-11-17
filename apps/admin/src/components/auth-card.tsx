import type { ReactNode } from "react";

/** Centered card used by every sign-in step. */
export function AuthCard({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
}) {
  return (
    <main className="relative grid min-h-dvh place-items-center overflow-hidden px-4 py-12">
      <div
        aria-hidden
        className="pointer-events-none absolute -top-40 -left-40 size-[32rem] rounded-full bg-indigo-300/30 blur-3xl"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -right-40 -bottom-40 size-[32rem] rounded-full bg-amber-200/40 blur-3xl"
      />
      <div className="bg-surface relative w-full max-w-md rounded-3xl p-8 shadow-xl ring-1 shadow-indigo-950/5 ring-black/5 sm:p-10">
        <p className="text-primary text-sm font-semibold tracking-widest uppercase">ShikshaBlox</p>
        <h1 className="mt-2 text-2xl font-bold">{title}</h1>
        {subtitle ? <p className="text-ink-muted mt-1 text-sm">{subtitle}</p> : null}
        {children}
      </div>
      <p className="text-ink-muted absolute bottom-4 text-xs">© aurora-blossom.com</p>
    </main>
  );
}
