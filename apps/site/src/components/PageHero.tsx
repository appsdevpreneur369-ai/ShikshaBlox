import type { ReactNode } from "react";

/** Tinted page header used by the built-in pages (apply, PTM, help, surveys). */
export function PageHero({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: ReactNode;
  children?: ReactNode;
}) {
  return (
    <section className="bg-primary-soft relative overflow-hidden">
      <div
        className="sb-blob"
        style={{
          width: 340,
          height: 340,
          right: -100,
          top: -140,
          backgroundImage:
            "radial-gradient(circle, var(--sb-color-accent) 0%, var(--sb-color-accent) 55%, transparent 72%)",
        }}
      />
      <div className="sb-container relative py-14 md:py-20">
        <p className="sb-eyebrow mb-3">{eyebrow}</p>
        <h1 className="text-4xl font-extrabold sm:text-5xl">{title}</h1>
        {children && <div className="sb-muted mt-4 max-w-2xl text-lg">{children}</div>}
      </div>
    </section>
  );
}
