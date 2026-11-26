"use client";

import { useCallback, useEffect, useState } from "react";
import { sb, StudioError } from "@/lib/studio";
import { Card, PageHeader } from "@/components/kit";
import { BroadcastsView } from "./broadcasts";
import { ContactsView } from "./contacts";
import { SettingsView, TemplatesView } from "./templates-settings";
import type { TagCount, Template } from "./types";

type Tab = "broadcasts" | "contacts" | "templates" | "settings";

export function MessagesPage() {
  const [tab, setTab] = useState<Tab>("broadcasts");
  const [templates, setTemplates] = useState<Template[]>([]);
  const [tags, setTags] = useState<TagCount[]>([]);
  const [blocked, setBlocked] = useState(false);

  const load = useCallback(async () => {
    try {
      const [t, g] = await Promise.all([
        sb<Template[]>("comms/templates"),
        sb<TagCount[]>("comms/tags"),
      ]);
      setTemplates(t);
      setTags(g);
    } catch (e) {
      if (e instanceof StudioError && e.status === 403) setBlocked(true);
    }
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  if (blocked) {
    return (
      <div>
        <PageHeader title="Messages" />
        <Card className="max-w-2xl">
          <h2 className="text-lg font-semibold">
            The communication hub is not part of your plan yet
          </h2>
          <p className="text-ink-muted mt-2 text-sm">
            Upgrade to ShikshaBlox Lite or Standard to send circulars on WhatsApp, SMS and email
            with read receipts and acknowledgements.
          </p>
        </Card>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Messages"
        subtitle="Circulars on WhatsApp, SMS and email, with read receipts and acknowledgements."
      />
      <div className="mb-5 flex flex-wrap gap-2">
        {(
          [
            ["broadcasts", "Circulars"],
            ["contacts", "Contacts"],
            ["templates", "Templates"],
            ["settings", "Settings"],
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
      {tab === "broadcasts" ? (
        <BroadcastsView templates={templates} tags={tags} />
      ) : tab === "contacts" ? (
        <ContactsView tags={tags} onChanged={() => void load()} />
      ) : tab === "templates" ? (
        <TemplatesView templates={templates} onChanged={() => void load()} />
      ) : (
        <SettingsView />
      )}
    </div>
  );
}
