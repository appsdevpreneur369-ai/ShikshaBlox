import type { Metadata } from "next";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { AuthCard } from "@/components/auth-card";
import { COOKIE } from "@/lib/session";
import { MfaForm } from "./mfa-form";

export const metadata: Metadata = { title: "Two-step verification" };

export default async function MfaPage() {
  if (!(await cookies()).get(COOKIE.mfa)) redirect("/login?error=expired");
  return (
    <AuthCard
      title="Two-step verification"
      subtitle="Open your authenticator app and enter the current code for ShikshaBlox."
    >
      <MfaForm />
    </AuthCard>
  );
}
