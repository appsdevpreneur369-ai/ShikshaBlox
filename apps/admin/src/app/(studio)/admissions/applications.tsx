"use client";

import { useCallback, useEffect, useState } from "react";
import { Check, Download, ExternalLink, Plus, Trash2, Upload, X } from "lucide-react";
import { sb, fmtDate } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Field, Loading, inputCls, useAction } from "@/components/kit";

export type DocRequirement = { code: string; label: string; required: boolean };
export type FormSettings = {
  open: boolean;
  grades: string[];
  documents: DocRequirement[];
  feeAmount: number;
  feeInstructions?: string | null;
  intro?: string | null;
};
export type AppSummary = {
  id: string;
  leadId: string;
  applicationNo: string;
  academicYear: string;
  grade: string;
  studentName: string;
  guardianName: string;
  phone: string;
  status: string;
  feeStatus: string;
  feeAmount: number;
  documents: number;
  verified: number;
  rejected: number;
  createdAt: string;
};
type Doc = {
  id: string;
  docCode: string;
  label: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  status: string;
  note?: string | null;
  reviewedByName?: string | null;
  reviewedAt?: string | null;
  createdAt: string;
};
type App = Omit<AppSummary, "documents" | "verified" | "rejected"> & {
  dateOfBirth: string;
  gender?: string | null;
  previousSchool?: string | null;
  relationship?: string | null;
  email?: string | null;
  address?: string | null;
  secondGuardianName?: string | null;
  secondGuardianPhone?: string | null;
  reviewNote?: string | null;
  feeReference?: string | null;
  feePaidAt?: string | null;
  updatedAt: string;
  documents: Doc[];
  missing: DocRequirement[];
};

export const APP_STATUS_LABEL: Record<string, string> = {
  SUBMITTED: "Submitted",
  UNDER_REVIEW: "Under review",
  DOCUMENTS_PENDING: "Documents pending",
  VERIFIED: "Verified",
  REJECTED: "Not accepted",
  WITHDRAWN: "Withdrawn",
};
export const APP_STATUS_TONE: Record<
  string,
  "blue" | "amber" | "purple" | "green" | "gray" | "red"
> = {
  SUBMITTED: "blue",
  UNDER_REVIEW: "purple",
  DOCUMENTS_PENDING: "amber",
  VERIFIED: "green",
  REJECTED: "red",
  WITHDRAWN: "gray",
};
const FEE_LABEL: Record<string, string> = {
  NOT_REQUIRED: "No fee",
  DUE: "Fee due",
  PAID: "Fee paid",
  WAIVED: "Fee waived",
};
const DOC_TONE: Record<string, "amber" | "green" | "red"> = {
  PENDING: "amber",
  VERIFIED: "green",
  REJECTED: "red",
};
const kb = (n: number) =>
  n > 1024 * 1024
    ? `${(n / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(n / 1024))} KB`;

/**
 * Downloads a document through its 10-minute signed link. The link is served as an attachment, so
 * following it from a hidden anchor saves the file without leaving Studio or opening a blank tab.
 */
async function downloadDoc(id: string) {
  const r = await sb<{ url: string }>(`admissions/documents/${id}/download`);
  const a = document.createElement("a");
  a.href = r.url;
  a.rel = "noopener noreferrer";
  document.body.appendChild(a);
  a.click();
  a.remove();
}

// ------------------------------------------------------------------ list

export function ApplicationsView({
  year,
  initial,
  onOpenLead,
}: {
  year: string;
  initial?: string;
  onOpenLead: (leadId: string) => void;
}) {
  const [rows, setRows] = useState<AppSummary[] | null>(null);
  const [status, setStatus] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [sel, setSel] = useState<string | null>(initial ?? null);

  const load = useCallback(async () => {
    const p = new URLSearchParams({ year });
    if (status) p.set("status", status);
    try {
      setRows(await sb<AppSummary[]>(`admissions/applications?${p}`));
    } catch (e) {
      setErr((e as Error).message);
    }
  }, [year, status]);

  useEffect(() => {
    if (!year) return;
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load, year]);

  if (err) return <Empty>{err}</Empty>;
  if (!rows) return <Loading />;
  return (
    <div className={`grid gap-5 ${sel ? "xl:grid-cols-[1fr_460px]" : ""}`}>
      <div className="min-w-0">
        <div className="mb-3 flex flex-wrap gap-1.5">
          {["", "SUBMITTED", "UNDER_REVIEW", "DOCUMENTS_PENDING", "VERIFIED", "REJECTED"].map(
            (s) => (
              <Btn
                key={s}
                size="sm"
                variant={status === s ? "primary" : "secondary"}
                onClick={() => setStatus(s)}
              >
                {s ? APP_STATUS_LABEL[s] : "All"}
              </Btn>
            ),
          )}
        </div>
        {rows.length === 0 ? (
          <Empty>
            No online applications for {year}
            {status ? ` with status “${APP_STATUS_LABEL[status]}”` : ""}. Open the form under
            Application form, then share your website’s /apply link.
          </Empty>
        ) : (
          <div className="overflow-x-auto rounded-2xl bg-white ring-1 ring-black/5">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-ink-muted text-left text-xs">
                  <th className="px-3 py-2 font-medium">Application</th>
                  <th className="px-3 py-2 font-medium">Student</th>
                  <th className="px-3 py-2 font-medium">Class</th>
                  <th className="px-3 py-2 font-medium">Documents</th>
                  <th className="px-3 py-2 font-medium">Fee</th>
                  <th className="px-3 py-2 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr
                    key={r.id}
                    onClick={() => setSel(r.id)}
                    className={`cursor-pointer border-t border-black/5 hover:bg-black/[0.02] ${sel === r.id ? "bg-primary/5" : ""}`}
                  >
                    <td className="px-3 py-2.5">
                      <button
                        type="button"
                        className="font-semibold hover:underline"
                        onClick={() => setSel(r.id)}
                      >
                        {r.applicationNo}
                      </button>
                      <span className="text-ink-muted block text-xs">{fmtDate(r.createdAt)}</span>
                    </td>
                    <td className="px-3 py-2.5">
                      {r.studentName}
                      <span className="text-ink-muted block text-xs">{r.guardianName}</span>
                    </td>
                    <td className="px-3 py-2.5">{r.grade}</td>
                    <td className="px-3 py-2.5 tabular-nums">
                      {r.verified}/{r.documents} verified
                      {r.rejected > 0 && (
                        <span className="block text-xs text-red-600">{r.rejected} rejected</span>
                      )}
                    </td>
                    <td className="px-3 py-2.5">
                      <Badge
                        tone={
                          r.feeStatus === "DUE"
                            ? "amber"
                            : r.feeStatus === "PAID"
                              ? "green"
                              : "gray"
                        }
                      >
                        {FEE_LABEL[r.feeStatus]}
                      </Badge>
                    </td>
                    <td className="px-3 py-2.5">
                      <Badge tone={APP_STATUS_TONE[r.status]}>{APP_STATUS_LABEL[r.status]}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {sel && (
        <ApplicationPanel
          key={sel}
          id={sel}
          onClose={() => setSel(null)}
          onChanged={() => void load()}
          onDeleted={() => {
            setSel(null);
            void load();
          }}
          onOpenLead={onOpenLead}
        />
      )}
    </div>
  );
}

// ------------------------------------------------------------------ detail

export function ApplicationPanel({
  id,
  onClose,
  onChanged,
  onDeleted,
  onOpenLead,
}: {
  id: string;
  onClose: () => void;
  onChanged: () => void;
  onDeleted: () => void;
  onOpenLead?: (leadId: string) => void;
}) {
  const [a, setA] = useState<App | null>(null);
  const [note, setNote] = useState("");
  const [feeRef, setFeeRef] = useState("");
  const [rejecting, setRejecting] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [upCode, setUpCode] = useState("");
  const { busy, run } = useAction();

  useEffect(() => {
    void sb<App>(`admissions/applications/${id}`).then((r) => {
      setA(r);
      setNote(r.reviewNote ?? "");
      setFeeRef(r.feeReference ?? "");
      setUpCode(r.missing[0]?.code ?? "other");
    });
  }, [id]);

  if (!a) {
    return (
      <Card className="h-fit">
        <p className="text-ink-muted text-sm">Loading application…</p>
      </Card>
    );
  }

  const done = (r: App | undefined | null) => {
    if (r) {
      setA(r);
      onChanged();
    }
  };

  async function setStatus(status: string) {
    done(
      await run(
        "status",
        () =>
          sb<App>(`admissions/applications/${a!.id}`, { method: "PATCH", body: { status, note } }),
        "Application updated",
      ),
    );
  }
  async function fee(status: string) {
    done(
      await run(
        "fee",
        () =>
          sb<App>(`admissions/applications/${a!.id}/fee`, { body: { status, reference: feeRef } }),
        "Fee updated",
      ),
    );
  }
  async function reviewDoc(docId: string, status: string, n?: string) {
    const r = await run(
      "doc" + docId,
      () => sb<App>(`admissions/documents/${docId}/review`, { body: { status, note: n } }),
      status === "VERIFIED" ? "Document verified" : "Document rejected",
    );
    done(r);
    if (r) {
      setRejecting(null);
      setReason("");
    }
  }
  async function upload(file: File | undefined) {
    if (!file) return;
    const form = new FormData();
    form.set("file", file);
    done(
      await run(
        "upload",
        () =>
          sb<App>(
            `admissions/applications/${a!.id}/documents?docCode=${encodeURIComponent(upCode)}`,
            { form },
          ),
        "Document added",
      ),
    );
  }
  async function remove() {
    if (
      !confirm(
        `Delete application ${a!.applicationNo} and all its documents? This cannot be undone. The lead stays in the pipeline.`,
      )
    )
      return;
    const r = await run(
      "delete",
      () => sb(`admissions/applications/${a!.id}`, { method: "DELETE" }).then(() => true),
      "Application deleted",
    );
    if (r) onDeleted();
  }

  const row = (k: string, v?: string | null) =>
    v ? (
      <div className="flex gap-3 py-1 text-sm">
        <dt className="text-ink-muted w-32 shrink-0">{k}</dt>
        <dd className="min-w-0 break-words">{v}</dd>
      </div>
    ) : null;

  return (
    <Card className="h-fit xl:sticky xl:top-24">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-ink-muted text-xs font-medium">{a.applicationNo}</p>
          <h2 className="truncate text-lg font-semibold">{a.studentName}</h2>
          <p className="text-ink-muted text-xs">
            {a.grade} · {a.academicYear} · submitted {fmtDate(a.createdAt)}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="text-ink-muted hover:text-ink rounded-full p-1.5 hover:bg-black/5"
          aria-label="Close application"
        >
          <X className="size-4" />
        </button>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Badge tone={APP_STATUS_TONE[a.status]}>{APP_STATUS_LABEL[a.status]}</Badge>
        <Badge tone={a.feeStatus === "DUE" ? "amber" : a.feeStatus === "PAID" ? "green" : "gray"}>
          {FEE_LABEL[a.feeStatus]}
          {a.feeAmount > 0 ? ` · ₹${a.feeAmount.toLocaleString("en-IN")}` : ""}
        </Badge>
        {onOpenLead && (
          <button
            type="button"
            className="text-primary ml-auto inline-flex items-center gap-1 text-sm font-medium hover:underline"
            onClick={() => onOpenLead(a.leadId)}
          >
            Open lead <ExternalLink className="size-3.5" aria-hidden />
          </button>
        )}
      </div>

      <dl className="mt-4">
        {row(
          "Date of birth",
          new Intl.DateTimeFormat("en-IN", {
            day: "numeric",
            month: "short",
            year: "numeric",
          }).format(new Date(a.dateOfBirth)),
        )}
        {row("Gender", a.gender ? { FEMALE: "Girl", MALE: "Boy", OTHER: "Other" }[a.gender] : null)}
        {row("Previous school", a.previousSchool)}
        {row("Parent", `${a.guardianName}${a.relationship ? ` (${a.relationship})` : ""}`)}
        {row("Phone", a.phone)}
        {row("Email", a.email)}
        {row("Address", a.address)}
        {row(
          "Second parent",
          [a.secondGuardianName, a.secondGuardianPhone].filter(Boolean).join(" · ") || null,
        )}
      </dl>

      <div className="mt-5">
        <p className="mb-2 text-sm font-medium">Documents</p>
        <ul className="space-y-2">
          {a.documents.map((d) => (
            <li key={d.id} className="rounded-xl p-3 ring-1 ring-black/10">
              <div className="flex items-start gap-2">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">{d.label}</p>
                  <p className="text-ink-muted truncate text-xs">
                    {d.filename} · {kb(d.sizeBytes)}
                  </p>
                </div>
                <Badge tone={DOC_TONE[d.status]}>
                  {d.status === "PENDING"
                    ? "To check"
                    : d.status === "VERIFIED"
                      ? "Verified"
                      : "Rejected"}
                </Badge>
              </div>
              {d.note && <p className="text-ink-muted mt-1 text-xs">Note: {d.note}</p>}
              {d.reviewedByName && (
                <p className="text-ink-muted text-xs">
                  {d.reviewedByName} · {fmtDate(d.reviewedAt)}
                </p>
              )}
              <div className="mt-2 flex flex-wrap gap-1.5">
                <Btn
                  size="sm"
                  busy={busy === "dl" + d.id}
                  onClick={() => void run("dl" + d.id, () => downloadDoc(d.id))}
                  aria-label={`Download ${d.label}`}
                >
                  <Download className="size-3.5" aria-hidden /> Download
                </Btn>
                {d.status !== "VERIFIED" && (
                  <Btn
                    size="sm"
                    variant="primary"
                    busy={busy === "doc" + d.id}
                    onClick={() => void reviewDoc(d.id, "VERIFIED")}
                  >
                    <Check className="size-3.5" aria-hidden /> Verify
                  </Btn>
                )}
                {d.status !== "REJECTED" && (
                  <Btn size="sm" variant="ghost" onClick={() => setRejecting(d.id)}>
                    Reject…
                  </Btn>
                )}
              </div>
              {rejecting === d.id && (
                <div className="mt-2 flex gap-2">
                  <input
                    className={inputCls}
                    placeholder="Reason, e.g. photo is blurred"
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    aria-label="Reason for rejecting"
                  />
                  <Btn
                    variant="danger"
                    disabled={!reason.trim()}
                    busy={busy === "doc" + d.id}
                    onClick={() => void reviewDoc(d.id, "REJECTED", reason)}
                  >
                    Reject
                  </Btn>
                </div>
              )}
            </li>
          ))}
        </ul>
        {a.missing.length > 0 && (
          <p className="mt-2 text-sm text-amber-700">
            Missing:{" "}
            {a.missing.map((m) => `${m.label}${m.required ? " (required)" : ""}`).join(", ")}
          </p>
        )}
        <div className="mt-3 flex flex-wrap items-end gap-2 rounded-xl bg-black/[0.03] p-3">
          <Field label="Add a document for the family">
            <select className={inputCls} value={upCode} onChange={(e) => setUpCode(e.target.value)}>
              {a.missing.map((m) => (
                <option key={m.code} value={m.code}>
                  {m.label}
                </option>
              ))}
              {a.documents
                .filter((d) => !a.missing.some((m) => m.code === d.docCode))
                .filter((d, i, all) => all.findIndex((x) => x.docCode === d.docCode) === i)
                .map((d) => (
                  <option key={d.docCode} value={d.docCode}>
                    {d.label} (replace)
                  </option>
                ))}
              <option value="other">Other document</option>
            </select>
          </Field>
          <label
            className={`inline-flex h-10 cursor-pointer items-center gap-1.5 rounded-full bg-white px-4 text-sm font-medium ring-1 ring-black/10 ${busy === "upload" ? "opacity-60" : ""}`}
          >
            <Upload className="size-4" aria-hidden />{" "}
            {busy === "upload" ? "Uploading…" : "Upload PDF / photo"}
            <input
              type="file"
              accept="application/pdf,image/jpeg,image/png"
              className="sr-only"
              onChange={(e) => void upload(e.target.files?.[0])}
            />
          </label>
        </div>
      </div>

      {a.feeAmount > 0 && (
        <div className="mt-5">
          <p className="mb-2 text-sm font-medium">
            Application fee · ₹{a.feeAmount.toLocaleString("en-IN")}
          </p>
          <div className="flex flex-wrap items-end gap-2">
            <input
              className={`${inputCls} w-48!`}
              placeholder="Receipt / UPI reference"
              value={feeRef}
              onChange={(e) => setFeeRef(e.target.value)}
              aria-label="Receipt or reference"
              maxLength={120}
            />
            <Btn
              size="sm"
              variant={a.feeStatus === "PAID" ? "primary" : "secondary"}
              busy={busy === "fee"}
              onClick={() => void fee("PAID")}
            >
              Mark paid
            </Btn>
            <Btn
              size="sm"
              variant={a.feeStatus === "WAIVED" ? "primary" : "ghost"}
              onClick={() => void fee("WAIVED")}
            >
              Waive
            </Btn>
            {a.feeStatus !== "DUE" && (
              <Btn size="sm" variant="ghost" onClick={() => void fee("DUE")}>
                Mark due
              </Btn>
            )}
          </div>
          {a.feePaidAt && (
            <p className="text-ink-muted mt-1 text-xs">Paid {fmtDate(a.feePaidAt)}</p>
          )}
        </div>
      )}

      <div className="mt-5">
        <p className="mb-2 text-sm font-medium">Review</p>
        <textarea
          className={inputCls}
          rows={2}
          maxLength={1000}
          placeholder="Note for your team (optional)"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          aria-label="Review note"
        />
        <div className="mt-2 flex flex-wrap gap-1.5">
          {["UNDER_REVIEW", "DOCUMENTS_PENDING", "VERIFIED", "REJECTED", "WITHDRAWN"].map((s) => (
            <Btn
              key={s}
              size="sm"
              variant={a.status === s ? "primary" : "secondary"}
              busy={busy === "status"}
              onClick={() => void setStatus(s)}
            >
              {APP_STATUS_LABEL[s]}
            </Btn>
          ))}
        </div>
        <p className="text-ink-muted mt-2 text-xs">
          Verified needs every required document verified. Then move the lead to Offered or Admitted
          in the pipeline.
        </p>
      </div>

      <div className="mt-6 border-t border-black/5 pt-4">
        <Btn size="sm" variant="ghost" busy={busy === "delete"} onClick={() => void remove()}>
          <Trash2 className="size-3.5" aria-hidden /> Delete application and documents
        </Btn>
      </div>
    </Card>
  );
}

/** Applications attached to a lead, shown in the lead panel. */
export function LeadApplications({
  leadId,
  onOpen,
}: {
  leadId: string;
  onOpen: (id: string) => void;
}) {
  const [rows, setRows] = useState<AppSummary[] | null>(null);
  useEffect(() => {
    sb<AppSummary[]>(`admissions/applications?leadId=${leadId}`)
      .then(setRows)
      .catch(() => setRows([]));
  }, [leadId]);
  if (!rows?.length) return null;
  return (
    <div className="mt-5">
      <p className="mb-2 text-sm font-medium">Online applications</p>
      <ul className="space-y-1.5">
        {rows.map((r) => (
          <li key={r.id}>
            <button
              type="button"
              onClick={() => onOpen(r.id)}
              className="flex w-full items-center gap-2 rounded-xl p-2.5 text-left text-sm ring-1 ring-black/10 hover:ring-black/20"
            >
              <span className="font-semibold">{r.applicationNo}</span>
              <span className="text-ink-muted text-xs">
                {r.verified}/{r.documents} docs
              </span>
              <span className="ml-auto">
                <Badge tone={APP_STATUS_TONE[r.status]}>{APP_STATUS_LABEL[r.status]}</Badge>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

// ------------------------------------------------------------------ settings

export function FormSettingsView({ siteUrl }: { siteUrl?: string }) {
  const [s, setS] = useState<FormSettings | null>(null);
  const [grades, setGrades] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const { busy, run } = useAction();

  useEffect(() => {
    sb<FormSettings>("admissions/application-settings")
      .then((r) => {
        setS(r);
        setGrades(r.grades.join("\n"));
      })
      .catch((e: Error) => setErr(e.message));
  }, []);

  if (err) return <Empty>{err}</Empty>;
  if (!s) return <Loading />;

  const setDoc = (i: number, p: Partial<DocRequirement>) =>
    setS({ ...s, documents: s.documents.map((d, j) => (i === j ? { ...d, ...p } : d)) });

  async function save(open = s!.open) {
    const r = await run(
      "save",
      () =>
        sb<FormSettings>("admissions/application-settings", {
          method: "PUT",
          body: {
            ...s,
            open,
            grades: grades
              .split("\n")
              .map((g) => g.trim())
              .filter(Boolean),
            documents: s!.documents.filter((d) => d.label.trim()),
          },
        }),
      open ? "Saved. The application form is open." : "Saved. The application form is closed.",
    );
    if (r) {
      setS(r);
      setGrades(r.grades.join("\n"));
    }
  }

  return (
    <div className="grid gap-5 lg:grid-cols-[1.4fr_1fr]">
      <Card>
        <div className="flex flex-wrap items-center gap-3">
          <div className="min-w-0 flex-1">
            <h2 className="text-lg font-semibold">Online application form</h2>
            <p className="text-ink-muted text-sm">
              {s.open
                ? "Open: parents can apply on your website at /apply, and the “Apply now” button links there."
                : "Closed: your website shows the enquiry form instead."}
            </p>
          </div>
          <Btn
            variant={s.open ? "secondary" : "primary"}
            busy={busy === "save"}
            onClick={() => void save(!s.open)}
          >
            {s.open ? "Close the form" : "Open the form"}
          </Btn>
        </div>
        {s.open && siteUrl && (
          <a
            href={`${siteUrl}/apply`}
            target="_blank"
            rel="noopener noreferrer"
            className="text-primary mt-2 inline-flex items-center gap-1 text-sm font-medium hover:underline"
          >
            {siteUrl.replace(/^https?:\/\//, "")}/apply{" "}
            <ExternalLink className="size-3.5" aria-hidden />
          </a>
        )}

        <h3 className="mt-6 mb-2 text-sm font-semibold">Documents to upload</h3>
        <ul className="space-y-2">
          {s.documents.map((d, i) => (
            <li key={i} className="flex flex-wrap items-center gap-2">
              <input
                className={`${inputCls} min-w-0 flex-1`}
                value={d.label}
                maxLength={80}
                onChange={(e) => setDoc(i, { label: e.target.value })}
                aria-label="Document name"
              />
              <label className="flex items-center gap-1.5 text-sm">
                <input
                  type="checkbox"
                  checked={d.required}
                  onChange={(e) => setDoc(i, { required: e.target.checked })}
                  className="size-4"
                />{" "}
                Required
              </label>
              <Btn
                size="sm"
                variant="ghost"
                onClick={() => setS({ ...s, documents: s.documents.filter((_, j) => j !== i) })}
                aria-label={`Remove ${d.label}`}
              >
                Remove
              </Btn>
            </li>
          ))}
        </ul>
        {s.documents.length < 8 && (
          <Btn
            size="sm"
            className="mt-2"
            onClick={() =>
              setS({ ...s, documents: [...s.documents, { code: "", label: "", required: false }] })
            }
          >
            <Plus className="size-4" aria-hidden /> Add document
          </Btn>
        )}

        <div className="mt-6 grid gap-3 sm:grid-cols-2">
          <Field label="Application fee (₹, 0 for none)">
            <input
              className={inputCls}
              type="number"
              min={0}
              max={100000}
              value={s.feeAmount}
              onChange={(e) => setS({ ...s, feeAmount: Number(e.target.value) })}
            />
          </Field>
          <div className="sm:col-span-2">
            <Field label="How to pay the fee (shown to parents)">
              <textarea
                className={inputCls}
                rows={2}
                maxLength={1000}
                placeholder="e.g. Pay at the school office (cash/UPI) or by bank transfer to …, quoting your application number."
                value={s.feeInstructions ?? ""}
                onChange={(e) => setS({ ...s, feeInstructions: e.target.value })}
              />
            </Field>
          </div>
          <div className="sm:col-span-2">
            <Field label="Introduction at the top of the form">
              <textarea
                className={inputCls}
                rows={2}
                maxLength={2000}
                value={s.intro ?? ""}
                onChange={(e) => setS({ ...s, intro: e.target.value })}
              />
            </Field>
          </div>
        </div>
        <Btn variant="primary" className="mt-4" busy={busy === "save"} onClick={() => void save()}>
          Save settings
        </Btn>
      </Card>
      <Card>
        <h3 className="text-sm font-semibold">Classes on the form</h3>
        <p className="text-ink-muted mt-1 mb-2 text-sm">
          One per line. Leave empty to use the classes in Seats (or Nursery to Class 12).
        </p>
        <textarea
          className={inputCls}
          rows={12}
          value={grades}
          onChange={(e) => setGrades(e.target.value)}
          aria-label="Classes, one per line"
        />
        <p className="text-ink-muted mt-4 text-xs">
          Each application is added to the pipeline as Applied (joined to the family’s existing lead
          when the phone number matches). Documents are private: only staff with admissions access
          can open them, through 10-minute links.
        </p>
      </Card>
    </div>
  );
}
