import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { StudentsPage } from "./students-page";

export const metadata: Metadata = { title: "Students" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  if (!(session.me.permissions ?? []).includes("students:manage")) redirect("/dashboard");
  return <StudentsPage />;
}
