import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { MessagesPage } from "./messages-page";

export const metadata: Metadata = { title: "Messages" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  if (!(session.me.permissions ?? []).includes("comms:manage")) redirect("/dashboard");
  return <MessagesPage />;
}
