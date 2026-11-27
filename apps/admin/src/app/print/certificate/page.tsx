"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import QRCode from "qrcode";
import { sb } from "@/lib/studio";
import type { Certificate } from "../../(studio)/certificates/types";

type PrintView = {
  certificate: Certificate;
  school: string;
  schoolAddress?: string | null;
  affiliationNo?: string | null;
  schoolCode?: string | null;
  signatoryName?: string | null;
  signatoryTitle: string;
};

const day = (iso?: string | null) =>
  iso
    ? new Intl.DateTimeFormat("en-IN", {
        day: "numeric",
        month: "long",
        year: "numeric",
        timeZone: "Asia/Kolkata",
      }).format(new Date(iso))
    : "";

function Sheet() {
  const id = useSearchParams().get("id") ?? "";
  const [v, setV] = useState<PrintView | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => {
    const t = setTimeout(async () => {
      try {
        const r = await sb<PrintView>(`certificates/${id}/print`);
        setV(r);
        if (r.certificate.verifyUrl)
          setQr(await QRCode.toDataURL(r.certificate.verifyUrl, { margin: 1, width: 240 }));
      } catch (e) {
        setErr((e as Error).message);
      }
    }, 0);
    return () => clearTimeout(t);
  }, [id]);
  if (err)
    return (
      <p role="alert" className="p-8 text-red-700">
        {err}
      </p>
    );
  if (!v) return <p className="p-8 text-gray-600">Preparing the certificate…</p>;
  const c = v.certificate;
  const tc = c.kind === "TC";
  return (
    <>
      <div className="mx-auto flex max-w-[780px] items-center justify-between py-4 print:hidden">
        <p className="text-sm text-gray-600">
          {c.title} · {c.number}
        </p>
        <button
          type="button"
          onClick={() => window.print()}
          className="rounded-full bg-indigo-700 px-4 py-2 text-sm font-medium text-white"
        >
          Print / save as PDF
        </button>
      </div>
      <article className="mx-auto max-w-[780px] rounded-lg border-4 border-double border-gray-700 bg-white p-10 text-[14px] leading-relaxed text-gray-900 print:max-w-none print:rounded-none">
        <header className="border-b border-gray-400 pb-3 text-center">
          <h1 className="text-2xl font-bold tracking-tight">{v.school}</h1>
          {v.schoolAddress && <p className="text-sm text-gray-700">{v.schoolAddress}</p>}
          {(v.affiliationNo || v.schoolCode) && (
            <p className="text-xs text-gray-600">
              {v.affiliationNo ? `Affiliation no. ${v.affiliationNo}` : ""}
              {v.affiliationNo && v.schoolCode ? " · " : ""}
              {v.schoolCode ? `School code ${v.schoolCode}` : ""}
            </p>
          )}
        </header>
        <div className="mt-4 flex justify-between text-sm">
          <span>
            No. <strong>{c.number}</strong>
            {c.duplicateOf ? " (DUPLICATE)" : ""}
          </span>
          <span>
            Admission no. <strong>{c.admissionNo}</strong>
          </span>
        </div>
        <h2 className="mt-6 text-center text-xl font-bold tracking-wide uppercase underline underline-offset-4">
          {c.title}
        </h2>
        {tc ? (
          <table className="mt-6 w-full border-collapse text-[13px]">
            <tbody>
              {c.fields.map((f, i) => (
                <tr key={f.key}>
                  <td className="w-8 border border-gray-300 px-2 py-1 align-top text-gray-600">
                    {i + 1}.
                  </td>
                  <th
                    scope="row"
                    className="w-[45%] border border-gray-300 px-2 py-1 text-left align-top font-medium"
                  >
                    {f.label}
                  </th>
                  <td className="border border-gray-300 px-2 py-1">{f.value || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}
        <p className="mt-6 whitespace-pre-line">{c.body}</p>
        <footer className="mt-14 flex items-end justify-between gap-6">
          <div className="text-xs text-gray-600">
            <p>Date of issue: {day(c.issuedAt)}</p>
            {qr && (
              <div className="mt-2 flex items-center gap-2">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={qr} alt="QR code to verify this certificate" className="size-24" />
                <p className="max-w-[16rem]">Scan to verify, or visit {c.verifyUrl}</p>
              </div>
            )}
          </div>
          <div className="text-center">
            <p className="h-12" />
            <p className="border-t border-gray-500 px-6 pt-1 font-semibold">
              {v.signatoryName ?? ""}
            </p>
            <p className="text-sm">{v.signatoryTitle}</p>
            <p className="text-xs text-gray-500">(Seal)</p>
          </div>
        </footer>
      </article>
    </>
  );
}

export default function Page() {
  return (
    <main className="min-h-dvh bg-gray-100 px-4 py-4 print:bg-white print:p-0">
      <Suspense>
        <Sheet />
      </Suspense>
    </main>
  );
}
