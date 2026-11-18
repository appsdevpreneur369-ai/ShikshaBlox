import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { siteUrl } from "@/lib/site-url";
import { PageEditor } from "./page-editor";

export const metadata: Metadata = { title: "Edit page" };

export default async function EditPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  const perms = session.me.permissions ?? [];
  return (
    <PageEditor
      id={id}
      site={siteUrl(session.me.activeOrganization.slug) ?? ""}
      canEdit={perms.includes("content:edit")}
      canPublish={perms.includes("content:publish")}
      isAdmin={perms.includes("school:manage")}
    />
  );
}
