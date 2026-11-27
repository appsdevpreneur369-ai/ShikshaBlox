import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { HomeworkPage } from "./homework-page";

export const metadata: Metadata = { title: "Homework" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  if (!(session.me.permissions ?? []).includes("homework:post")) redirect("/dashboard");
  return <HomeworkPage />;
}
