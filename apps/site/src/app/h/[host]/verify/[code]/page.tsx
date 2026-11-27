import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { normaliseHost } from "@/lib/host";

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

type Params = { host: string; code: string };
type Verification = {
  school: string;
  title: string;
  number?: string | null;
  studentName: string;
  classLabel?: string | null;
  issuedAt?: string | null;
  status: "ISSUED" | "CANCELLED" | string;
  duplicateIssued: boolean;
};

// Each check is looked up live and never indexed.
export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "Verify a certificate",
  robots: { index: false, follow: false },
};

export default async function VerifyPage({ params }: { params: Promise<Params> }) {
  const p = await params;
  const host = normaliseHost(decodeURIComponent(p.host));
  const code = decodeURIComponent(p.code).toUpperCase();
  if (!host || !/^[A-Z0-9]{8,16}$/.test(code)) notFound();
  const res = await fetch(
    `${API}/api/public/v1/certificates/verify?host=${encodeURIComponent(host)}&code=${code}`,
    { cache: "no-store" },
  );
  const v = res.ok ? ((await res.json()) as Verification) : null;
  const valid = v?.status === "ISSUED";
  const day = (iso?: string | null) =>
    iso
      ? new Intl.DateTimeFormat("en-IN", {
          day: "numeric",
          month: "long",
          year: "numeric",
          timeZone: "Asia/Kolkata",
        }).format(new Date(iso))
      : "—";
  return (
    <section className="sb-section">
      <div className="sb-container max-w-xl">
        <h1 className="text-2xl font-bold">Certificate check</h1>
        {!v ? (
          <p
            role="alert"
            className="mt-4 rounded-xl bg-red-50 px-4 py-3 font-semibold text-red-800"
          >
            {res.status === 429
              ? "Too many checks. Please try again in a few minutes."
              : "No certificate with this code was issued by this school."}
          </p>
        ) : (
          <div className="sb-card mt-4 p-6">
            <p
              className={`text-lg font-bold ${valid ? "text-emerald-700" : "text-red-700"}`}
              role="status"
            >
              {valid ? "✓ Genuine certificate" : "✗ This certificate has been cancelled"}
            </p>
            <dl className="mt-4 grid gap-2 text-sm sm:grid-cols-[10rem_1fr]">
              <dt className="sb-muted">Issued by</dt>
              <dd className="font-semibold">{v.school}</dd>
              <dt className="sb-muted">Certificate</dt>
              <dd>
                {v.title}
                {v.number ? ` · ${v.number}` : ""}
              </dd>
              <dt className="sb-muted">Student</dt>
              <dd>
                {v.studentName}
                {v.classLabel ? ` · ${v.classLabel}` : ""}
              </dd>
              <dt className="sb-muted">Date of issue</dt>
              <dd>{day(v.issuedAt)}</dd>
            </dl>
            {v.duplicateIssued && (
              <p className="mt-4 rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900">
                A duplicate of this certificate has since been issued.
              </p>
            )}
            <p className="sb-muted mt-4 text-xs">
              Check that the details match the paper certificate. For questions, contact the school
              office.
            </p>
          </div>
        )}
      </div>
    </section>
  );
}
