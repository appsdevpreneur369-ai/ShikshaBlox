import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { api, currentUser } from "@/lib/session";
import { siteUrl } from "@/lib/site-url";
import { ParentsPage } from "./parents-page";

export const metadata: Metadata = { title: "Parents" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  if (!(session.me.permissions ?? []).includes("engagement:manage")) redirect("/dashboard");
  const client = await api(session.accessToken);
  const { data } = await client.GET("/api/admin/v1/entitlements");
  const m = data?.modules?.find((x) => x.module === "ENGAGEMENT");
  return (
    <ParentsPage
      features={m ? (m.features ?? []) : null}
      siteUrl={siteUrl(session.me.activeOrganization.slug) ?? undefined}
    />
  );
}
