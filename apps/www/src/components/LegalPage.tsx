import type { ReactNode } from "react";
import { JsonLd, PageHero } from "./ui";
import { breadcrumbLd } from "@/lib/seo";

export function LegalPage({ title, path, updated, children }: { title: string; path: string; updated: string; children: ReactNode }) {
  const crumbs = [
    { name: "Home", path: "/" },
    { name: title, path },
  ];
  return (
    <>
      <JsonLd data={breadcrumbLd(crumbs)} />
      <PageHero crumbs={crumbs} title={title} lead={`Last updated ${updated}`} />
      <div className="container-x py-14 sm:py-20">
        <div className="max-w-3xl space-y-5 leading-relaxed text-ink-2 [&_a]:font-semibold [&_a]:text-brand [&_h2]:mt-10 [&_h2]:font-display [&_h2]:text-2xl [&_h2]:font-bold [&_h2]:text-ink [&_li]:ml-5 [&_li]:list-disc">
          {children}
        </div>
      </div>
    </>
  );
}
