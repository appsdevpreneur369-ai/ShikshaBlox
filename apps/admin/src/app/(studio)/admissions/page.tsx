import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { siteUrl } from "@/lib/site-url";
import { AdmissionsPage } from "./admissions-page";

export const metadata: Metadata = { title: "Admissions" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  return (
    <AdmissionsPage
      siteUrl={siteUrl(session.me.activeOrganization.slug) ?? undefined}
      isAdmin={(session.me.permissions ?? []).includes("school:manage")}
    />
  );
}
