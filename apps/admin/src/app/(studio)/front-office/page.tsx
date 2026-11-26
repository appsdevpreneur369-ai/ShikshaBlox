import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { api, currentUser } from "@/lib/session";
import { FrontOfficePage } from "./front-office-page";

export const metadata: Metadata = { title: "Front office" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  if (!(session.me.permissions ?? []).includes("frontoffice:manage")) redirect("/dashboard");
  const client = await api(session.accessToken);
  const { data } = await client.GET("/api/admin/v1/entitlements");
  return (
    <FrontOfficePage
      hasAdmissions={Boolean(data?.modules?.some((m) => m.module === "ADMISSIONS_CRM"))}
    />
  );
}
