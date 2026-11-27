import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { EarlyYearsPage } from "./early-years-page";

export const metadata: Metadata = { title: "Early years" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  const p = session.me.permissions ?? [];
  if (!p.includes("homework:post") && !p.includes("school:manage")) redirect("/dashboard");
  return <EarlyYearsPage />;
}
