import type { Metadata } from "next";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import QRCode from "qrcode";
import { AuthCard } from "@/components/auth-card";
import { COOKIE, api } from "@/lib/session";
import { MfaForm } from "../mfa/mfa-form";

export const metadata: Metadata = { title: "Set up two-step verification" };
export const dynamic = "force-dynamic";

export default async function MfaSetupPage() {
  const mfaToken = (await cookies()).get(COOKIE.mfa)?.value;
  if (!mfaToken) redirect("/login?error=expired");

  const client = await api();
  const { data } = await client.POST("/api/auth/v1/mfa/enroll/start", { body: { mfaToken } });
  if (!data?.otpauthUri || !data.secret) redirect("/login?error=expired");

  const svg = await QRCode.toString(data.otpauthUri, { type: "svg", margin: 1, width: 200 });
  const grouped = data.secret.match(/.{1,4}/g)?.join(" ");

  return (
    <AuthCard
      title="Protect your account"
      subtitle="Platform administrators must use an authenticator app (Google Authenticator, Microsoft Authenticator, 1Password…)."
    >
      <ol className="text-ink-muted mt-6 list-decimal space-y-2 pl-5 text-sm">
        <li>Scan this QR code with your authenticator app.</li>
        <li>Enter the 6-digit code it shows.</li>
      </ol>
      <div
        role="img"
        aria-label="QR code for your authenticator app"
        className="mx-auto mt-5 w-fit rounded-2xl bg-white p-3 ring-1 ring-black/10 [&_svg]:size-48"
        dangerouslySetInnerHTML={{ __html: svg }}
      />
      <p className="text-ink-muted mt-3 text-center text-xs">
        Can&apos;t scan? Enter this key:{" "}
        <code className="text-ink font-mono font-semibold">{grouped}</code>
      </p>
      <MfaForm setup />
    </AuthCard>
  );
}
