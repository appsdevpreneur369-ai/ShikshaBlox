"use client";

import { useEffect, useState } from "react";
import { Card, PageHeader } from "@/components/kit";
import { HelpdeskView } from "./helpdesk";
import { PtmView } from "./ptm";
import { SurveysView } from "./surveys";

type Tab = "helpdesk" | "ptm" | "surveys";

export function ParentsPage({
  features,
  siteUrl,
}: {
  features: string[] | null;
  siteUrl?: string;
}) {
  const [tab, setTab] = useState<Tab>("helpdesk");
  // Deep links (?tab=ptm) are read after mount, so server and browser render the same first.
  useEffect(() => {
    const t = new URLSearchParams(window.location.search).get("tab");
    if (t !== "ptm" && t !== "surveys") return;
    const id = setTimeout(() => setTab(t), 0);
    return () => clearTimeout(id);
  }, []);

  if (!features) {
    return (
      <div>
        <PageHeader title="Parents" />
        <Card className="max-w-2xl">
          <h2 className="text-lg font-semibold">
            PTM booking and the parent helpdesk are not part of your plan yet
          </h2>
          <p className="text-ink-muted mt-2 text-sm">
            Upgrade to ShikshaBlox Lite or Standard to let parents book PTM slots and raise requests
            on your website, and to track every request to resolution.
          </p>
        </Card>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Parents"
        subtitle="Helpdesk requests, PTM bookings and feedback surveys."
      />
      <div className="mb-5 flex flex-wrap gap-2">
        {(
          [
            ["helpdesk", "Helpdesk"],
            ["ptm", "PTM"],
            ["surveys", "Surveys"],
          ] as [Tab, string][]
        ).map(([k, label]) => (
          <button
            key={k}
            type="button"
            onClick={() => setTab(k)}
            className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition ${tab === k ? "bg-primary text-on-primary" : "text-ink-muted hover:text-ink bg-white ring-1 ring-black/10"}`}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === "helpdesk" ? (
        <HelpdeskView canAssign={features.includes("assignment")} siteUrl={siteUrl} />
      ) : tab === "ptm" ? (
        <PtmView siteUrl={siteUrl} />
      ) : (
        <SurveysView siteUrl={siteUrl} available={features.includes("surveys")} />
      )}
    </div>
  );
}
