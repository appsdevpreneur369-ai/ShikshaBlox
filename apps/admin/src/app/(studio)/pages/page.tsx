import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { siteUrl } from "@/lib/site-url";
import { PagesList } from "./pages-list";

export const metadata: Metadata = { title: "Pages" };

export default async function PagesPage() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  const perms = session.me.permissions ?? [];
  return (
    <PagesList
      site={siteUrl(session.me.activeOrganization.slug) ?? ""}
      canEdit={perms.includes("content:edit")}
      canPublish={perms.includes("content:publish")}
    />
  );
}
