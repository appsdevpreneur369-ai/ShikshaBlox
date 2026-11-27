import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser } from "@/lib/session";
import { CertificatesPage } from "./certificates-page";

export const metadata: Metadata = { title: "Certificates" };

export default async function Page() {
  const session = await currentUser();
  if (!session?.me.activeOrganization) redirect("/dashboard");
  if (!(session.me.permissions ?? []).includes("certificates:manage")) redirect("/dashboard");
  return <CertificatesPage />;
}
