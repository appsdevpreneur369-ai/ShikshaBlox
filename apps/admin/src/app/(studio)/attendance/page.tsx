import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { AttendancePage } from "./attendance-page";

export const metadata: Metadata = { title: "Attendance" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  const perms = session.me.permissions ?? [];
  if (!perms.includes("attendance:mark") && !perms.includes("attendance:manage"))
    redirect("/dashboard");
  return <AttendancePage />;
}
