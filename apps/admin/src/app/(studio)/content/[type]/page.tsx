import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { siteUrl } from "@/lib/site-url";
import { ContentManager } from "./content-manager";

export const metadata: Metadata = { title: "Content" };

export default async function ContentPage({ params }: { params: Promise<{ type: string }> }) {
  const { type } = await params;
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  const perms = session.me.permissions ?? [];
  return (
    <ContentManager
      type={type}
      site={siteUrl(session.me.activeOrganization.slug) ?? ""}
      canEdit={perms.includes("content:edit")}
      canPublish={perms.includes("content:publish")}
      isAdmin={perms.includes("school:manage")}
    />
  );
}
