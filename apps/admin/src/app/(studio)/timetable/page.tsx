import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { TimetablePage } from "./timetable-page";

export const metadata: Metadata = { title: "Timetable" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  const p = session.me.permissions ?? [];
  if (
    !["timetable:manage", "attendance:mark", "marks:enter", "homework:post"].some((x) =>
      p.includes(x),
    )
  )
    redirect("/dashboard");
  return <TimetablePage />;
}
