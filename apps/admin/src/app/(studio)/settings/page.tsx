import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { api, currentUser } from "@/lib/session";
import { SettingForm } from "./setting-form";

export const metadata: Metadata = { title: "Settings" };

const SOURCE_LABEL: Record<string, string> = {
  DEFAULT: "Platform default",
  PLATFORM: "Set by platform",
  PROFILE: "From school profile",
  ORGANIZATION: "Set by your school",
  CAMPUS: "Set for this campus",
};

export default async function SettingsPage() {
  const session = await currentUser();
  if (!session) redirect("/login");
  const canEdit = session.me.permissions?.includes("school:manage") ?? false;
  const client = await api(session.accessToken);
  const { data = [] } = await client.GET("/api/admin/v1/settings");

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Settings</h1>
        <p className="text-ink-muted mt-1">
          Values come from the platform, your school profile or your school — the most specific
          wins.
        </p>
      </div>
      <ul className="grid gap-4 md:grid-cols-2">
        {data.map((s) => (
          <li key={s.key} className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-black/5">
            <p className="font-medium">{s.description}</p>
            <p className="text-ink-muted mt-0.5 font-mono text-xs">{s.key}</p>
            <p className="mt-2 inline-block rounded-full bg-black/5 px-2.5 py-0.5 text-xs">
              {SOURCE_LABEL[s.source ?? "DEFAULT"]}
            </p>
            <div className="mt-3">
              <SettingForm
                settingKey={s.key!}
                type={s.type!}
                value={s.value as unknown}
                editable={canEdit && Boolean(s.editable)}
              />
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
