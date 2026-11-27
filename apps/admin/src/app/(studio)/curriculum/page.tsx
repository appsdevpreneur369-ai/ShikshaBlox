import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { CurriculumPage } from "./curriculum-page";

export const metadata: Metadata = { title: "Curriculum" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  const p = session.me.permissions ?? [];
  if (!p.includes("curriculum:manage") && !p.includes("homework:post")) redirect("/dashboard");
  return <CurriculumPage />;
}
