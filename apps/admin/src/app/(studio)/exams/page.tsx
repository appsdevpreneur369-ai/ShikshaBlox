import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { ExamsPage } from "./exams-page";

export const metadata: Metadata = { title: "Exams" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  const perms = session.me.permissions ?? [];
  if (!perms.includes("marks:enter") && !perms.includes("exams:manage")) redirect("/dashboard");
  return <ExamsPage />;
}
