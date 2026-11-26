import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { api, currentUser } from "@/lib/session";
import { CompliancePage } from "./compliance-page";

export const metadata: Metadata = { title: "Compliance" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  if (!(session.me.permissions ?? []).includes("compliance:manage")) redirect("/dashboard");
  const client = await api(session.accessToken);
  const { data } = await client.GET("/api/admin/v1/entitlements");
  const features = data?.modules?.find((m) => m.module === "COMPLIANCE")?.features ?? [];
  return (
    <CompliancePage
      canOwn={features.includes("owners")}
      canExport={features.includes("export_pack")}
    />
  );
}
