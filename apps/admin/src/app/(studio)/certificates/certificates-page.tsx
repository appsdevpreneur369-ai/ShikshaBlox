"use client";

import { useCallback, useEffect, useState } from "react";
import { ArrowLeft, Plus, Printer, Search } from "lucide-react";
import { sb, StudioError, fmtDate } from "@/lib/studio";
import {
  Badge,
  Btn,
  Card,
  Empty,
  Field,
  Loading,
  PageHeader,
  inputCls,
  useAction,
} from "@/components/kit";
import {
  STATUS,
  type Certificate,
  type Draft,
  type Settings,
  type StudentHit,
  type Template,
} from "./types";

type Tab = "register" | "new" | "setup";

export function CertificatesPage() {
  const [tab, setTab] = useState<Tab>("register");
  const [settings, setSettings] = useState<Settings | null>(null);
  const [templates, setTemplates] = useState<Template[]>([]);
  const [blocked, setBlocked] = useState(false);
  const [prefill, setPrefill] = useState<{ request?: Certificate } | null>(null);

  const load = useCallback(async () => {
    try {
      setSettings(await sb<Settings>("certificates/settings"));
      setTemplates(await sb<Template[]>("certificates/templates"));
    } catch (e) {
      if (e instanceof StudioError && e.status === 403) setBlocked(true);
    }
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  if (blocked)
    return (
      <div>
        <PageHeader title="Certificates" />
        <Card className="max-w-2xl">
          <h2 className="text-lg font-semibold">Certificates are not part of your plan yet</h2>
          <p className="text-ink-muted mt-2 text-sm">
            Upgrade to ShikshaBlox Lite or Standard to issue TCs and certificates with QR
            verification.
          </p>
        </Card>
      </div>
    );
  if (!settings) return <Loading />;
  const tabs: [Tab, string][] = [
    ["register", "Register"],
    ["new", "New certificate"],
  ];
  if (settings.canApprove) tabs.push(["setup", "Templates & settings"]);

  return (
    <div>
      <PageHeader
        title="Certificates"
        subtitle="Transfer, bonafide, character and study certificates with QR verification"
      />
      <div className="mb-5 flex flex-wrap gap-2">
        {tabs.map(([k, label]) => (
          <button
            key={k}
            type="button"
            onClick={() => {
              setPrefill(null);
              setTab(k);
            }}
            className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition ${tab === k ? "bg-primary text-on-primary" : "text-ink-muted hover:text-ink bg-white ring-1 ring-black/10"}`}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === "register" ? (
        <Register
          settings={settings}
          onPrepare={(c) => {
            setPrefill({ request: c });
            setTab("new");
          }}
        />
      ) : tab === "new" ? (
        <NewCertificate
          templates={templates.filter((t) => t.active)}
          request={prefill?.request}
          onDone={() => setTab("register")}
        />
      ) : (
        <Setup settings={settings} templates={templates} onChanged={() => void load()} />
      )}
    </div>
  );
}

// ------------------------------------------------------------------ register

function Register({
  settings,
  onPrepare,
}: {
  settings: Settings;
  onPrepare: (c: Certificate) => void;
}) {
  const [rows, setRows] = useState<Certificate[] | null>(null);
  const [status, setStatus] = useState("");
  const [q, setQ] = useState("");
  const [act, setAct] = useState<{ id: string; kind: "cancel" | "duplicate" | "reject" } | null>(
    null,
  );
  const [reason, setReason] = useState("");
  const { busy, run } = useAction();

  const load = useCallback(async () => {
    const p = new URLSearchParams();
    if (status) p.set("status", status);
    if (q) p.set("q", q);
    setRows(await sb<Certificate[]>(`certificates?${p}`));
  }, [status, q]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 250);
    return () => clearTimeout(t);
  }, [load]);

  async function doAct(c: Certificate, kind: "cancel" | "duplicate" | "reject" | "approve") {
    const path = kind === "approve" || kind === "reject" ? "decision" : kind;
    const body =
      kind === "approve"
        ? { approve: true }
        : kind === "reject"
          ? { approve: false, note: reason }
          : { reason };
    const r = await run(
      c.id,
      () => sb(`certificates/${c.id}/${path}`, { body }),
      kind === "duplicate" ? "Duplicate issued." : "Done.",
    );
    if (r) {
      setAct(null);
      setReason("");
      void load();
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="mb-1 block text-sm font-medium">Show</span>
          <select
            className={`${inputCls} w-auto!`}
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            <option value="">All</option>
            <option value="REQUESTED">Parent requests</option>
            <option value="PENDING_APPROVAL">Waiting for approval</option>
            <option value="ISSUED">Issued</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </label>
        <label className="relative block">
          <span className="mb-1 block text-sm font-medium">Search</span>
          <Search
            className="text-ink-muted pointer-events-none absolute bottom-2.5 left-3 size-4"
            aria-hidden
          />
          <input
            className={`${inputCls} pl-9`}
            placeholder="Student, admission or certificate no."
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </label>
      </div>
      {!rows ? (
        <Loading />
      ) : rows.length === 0 ? (
        <Empty>No certificates yet. Use “New certificate” to issue one.</Empty>
      ) : (
        <ul className="divide-y divide-black/5 rounded-2xl bg-white shadow-sm ring-1 ring-black/5">
          {rows.map((c) => (
            <li key={c.id} className="space-y-2 p-4">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">
                    {c.title}
                    {c.number && <span className="text-ink-muted font-normal"> · {c.number}</span>}
                  </p>
                  <p className="text-sm">
                    {c.studentName}{" "}
                    <span className="text-ink-muted">
                      · {c.admissionNo}
                      {c.classLabel ? ` · ${c.classLabel}` : ""}
                    </span>
                  </p>
                  <p className="text-ink-muted text-xs">
                    {c.purpose ? `For: ${c.purpose} · ` : ""}
                    {c.requestedBy ? `Requested by ${c.requestedBy} · ` : ""}
                    {c.issuedAt
                      ? `Issued ${fmtDate(c.issuedAt)}${c.issuedBy ? ` by ${c.issuedBy}` : ""}`
                      : `Created ${fmtDate(c.createdAt)}${c.createdBy ? ` by ${c.createdBy}` : ""}`}
                    {c.statusNote ? ` · “${c.statusNote}”` : ""}
                  </p>
                </div>
                <Badge tone={STATUS[c.status].tone}>
                  {c.duplicateOf ? "Duplicate" : STATUS[c.status].label}
                </Badge>
              </div>
              <div className="flex flex-wrap gap-2">
                {c.status === "ISSUED" && (
                  <a
                    href={`/print/certificate?id=${c.id}`}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex h-8 items-center gap-1 rounded-full bg-white px-3 text-xs font-medium ring-1 ring-black/10 hover:bg-black/[0.03]"
                  >
                    <Printer className="size-3.5" aria-hidden /> Print
                  </a>
                )}
                {c.status === "REQUESTED" && (
                  <Btn size="sm" variant="primary" onClick={() => onPrepare(c)}>
                    Prepare certificate
                  </Btn>
                )}
                {settings.canApprove && c.status === "PENDING_APPROVAL" && (
                  <Btn
                    size="sm"
                    variant="success"
                    busy={busy === c.id}
                    onClick={() => void doAct(c, "approve")}
                  >
                    Approve & issue
                  </Btn>
                )}
                {settings.canApprove &&
                  (c.status === "PENDING_APPROVAL" || c.status === "REQUESTED") && (
                    <Btn size="sm" onClick={() => setAct({ id: c.id, kind: "reject" })}>
                      Decline…
                    </Btn>
                  )}
                {settings.canApprove && c.status === "ISSUED" && !c.duplicateOf && (
                  <>
                    <Btn size="sm" onClick={() => setAct({ id: c.id, kind: "duplicate" })}>
                      Duplicate…
                    </Btn>
                    <Btn
                      size="sm"
                      variant="danger"
                      onClick={() => setAct({ id: c.id, kind: "cancel" })}
                    >
                      Cancel…
                    </Btn>
                  </>
                )}
              </div>
              {act?.id === c.id && (
                <div className="flex flex-wrap items-center gap-2">
                  <input
                    className={`${inputCls} max-w-sm`}
                    placeholder={
                      act.kind === "duplicate"
                        ? "Why a duplicate? (e.g. original lost)"
                        : act.kind === "cancel"
                          ? "Why cancel?"
                          : "Reason, shown to the parent"
                    }
                    value={reason}
                    maxLength={300}
                    onChange={(e) => setReason(e.target.value)}
                  />
                  <Btn
                    size="sm"
                    variant={act.kind === "duplicate" ? "primary" : "danger"}
                    disabled={!reason.trim()}
                    busy={busy === c.id}
                    onClick={() => void doAct(c, act.kind)}
                  >
                    {act.kind === "duplicate"
                      ? "Issue duplicate"
                      : act.kind === "cancel"
                        ? "Cancel certificate"
                        : "Decline"}
                  </Btn>
                  <Btn size="sm" variant="ghost" onClick={() => setAct(null)}>
                    Back
                  </Btn>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ new certificate

function NewCertificate({
  templates,
  request,
  onDone,
}: {
  templates: Template[];
  request?: Certificate;
  onDone: () => void;
}) {
  const [templateId, setTemplateId] = useState(request?.templateId ?? templates[0]?.id ?? "");
  const [student, setStudent] = useState<StudentHit | null>(
    request
      ? {
          id: request.studentId,
          name: request.studentName,
          admissionNo: request.admissionNo,
          classLabel: request.classLabel,
          status: "ENROLLED",
        }
      : null,
  );
  const [q, setQ] = useState("");
  const [hits, setHits] = useState<StudentHit[]>([]);
  const [purpose, setPurpose] = useState(request?.purpose ?? "");
  const [draft, setDraft] = useState<Draft | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [dues, setDues] = useState<string[]>([]);
  const { busy, run } = useAction();

  useEffect(() => {
    if (q.trim().length < 2) return;
    const t = setTimeout(
      () =>
        void sb<StudentHit[]>(`certificates/students?q=${encodeURIComponent(q)}`)
          .then(setHits)
          .catch(() => setHits([])),
      250,
    );
    return () => clearTimeout(t);
  }, [q]);

  useEffect(() => {
    if (!student || !templateId) return;
    const t = setTimeout(async () => {
      const d = await sb<Draft>(
        `certificates/draft?templateId=${templateId}&studentId=${student.id}&purpose=${encodeURIComponent(purpose)}`,
      ).catch(() => null);
      setDraft(d);
      const f: Record<string, string> = {};
      d?.fields.forEach((x) => (f[x.key] = x.value));
      setFields(f);
      setDues([]);
    }, 300);
    return () => clearTimeout(t);
  }, [student, templateId, purpose]);

  async function issue() {
    const r = await run("issue", () =>
      sb<Certificate>("certificates", {
        body: {
          templateId,
          studentId: student!.id,
          purpose: purpose || null,
          fields,
          noDues: dues,
          requestId: request?.id ?? null,
        },
      }),
    );
    if (r) {
      if (r.status === "ISSUED") window.open(`/print/certificate?id=${r.id}`, "_blank");
      onDone();
    }
  }

  const tpl = templates.find((t) => t.id === templateId);
  return (
    <div className="grid gap-6 lg:grid-cols-[2fr_3fr]">
      <Card className="space-y-4 self-start">
        {request && (
          <p className="rounded-xl bg-indigo-50 px-4 py-3 text-sm text-indigo-900">
            Request from {request.requestedBy ?? "a parent"} for {request.studentName}.
          </p>
        )}
        <Field label="Certificate">
          <select
            className={inputCls}
            value={templateId}
            onChange={(e) => setTemplateId(e.target.value)}
            disabled={!!request}
          >
            {templates.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </Field>
        {student ? (
          <div className="flex items-center justify-between gap-2 rounded-xl bg-black/[0.03] px-3 py-2 text-sm">
            <span>
              <strong>{student.name}</strong> · {student.admissionNo}
              {student.classLabel ? ` · ${student.classLabel}` : ""}
              {student.status !== "ENROLLED" && <span className="text-ink-muted"> · left</span>}
            </span>
            {!request && (
              <Btn
                size="sm"
                variant="ghost"
                onClick={() => {
                  setStudent(null);
                  setDraft(null);
                }}
              >
                Change
              </Btn>
            )}
          </div>
        ) : (
          <div>
            <Field label="Student">
              <input
                className={inputCls}
                placeholder="Type a name or admission number"
                value={q}
                onChange={(e) => setQ(e.target.value)}
              />
            </Field>
            {q.trim().length >= 2 && (
              <ul className="mt-1 max-h-60 divide-y divide-black/5 overflow-y-auto rounded-xl ring-1 ring-black/10">
                {hits.length === 0 ? (
                  <li className="text-ink-muted px-3 py-2 text-sm">No students found.</li>
                ) : (
                  hits.map((h) => (
                    <li key={h.id}>
                      <button
                        type="button"
                        className="w-full px-3 py-2 text-left text-sm hover:bg-black/[0.03]"
                        onClick={() => {
                          setStudent(h);
                          setQ("");
                        }}
                      >
                        {h.name}{" "}
                        <span className="text-ink-muted">
                          · {h.admissionNo}
                          {h.classLabel ? ` · ${h.classLabel}` : ""}
                          {h.status !== "ENROLLED" ? " · left" : ""}
                        </span>
                      </button>
                    </li>
                  ))
                )}
              </ul>
            )}
          </div>
        )}
        {tpl?.kind !== "TC" && (
          <Field label="Purpose">
            <input
              className={inputCls}
              value={purpose}
              maxLength={200}
              placeholder="e.g. passport application"
              onChange={(e) => setPurpose(e.target.value)}
            />
          </Field>
        )}
      </Card>

      {draft && (
        <Card className="space-y-4">
          <h2 className="text-lg font-semibold">{draft.title}</h2>
          {draft.problems.length > 0 && (
            <ul className="space-y-1 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900">
              {draft.problems.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
          )}
          {draft.fields.length > 0 && (
            <div className="grid gap-3 sm:grid-cols-2">
              {draft.fields.map((f) => (
                <Field key={f.key} label={f.label}>
                  <input
                    className={inputCls}
                    value={fields[f.key] ?? ""}
                    maxLength={300}
                    onChange={(e) => setFields({ ...fields, [f.key]: e.target.value })}
                  />
                </Field>
              ))}
            </div>
          )}
          {draft.kind !== "TC" && (
            <p className="rounded-xl bg-black/[0.03] p-4 text-sm whitespace-pre-line">
              {draft.body}
            </p>
          )}
          {draft.noDuesItems.length > 0 && (
            <fieldset className="space-y-1.5">
              <legend className="text-sm font-medium">No dues confirmed (needed for a TC)</legend>
              {draft.noDuesItems.map((d) => (
                <label key={d} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={dues.includes(d)}
                    onChange={(e) =>
                      setDues(e.target.checked ? [...dues, d] : dues.filter((x) => x !== d))
                    }
                  />
                  {d}
                </label>
              ))}
            </fieldset>
          )}
          <Btn
            variant="primary"
            busy={busy === "issue"}
            disabled={
              draft.problems.length > 0 ||
              (draft.noDuesItems.length > 0 && dues.length < draft.noDuesItems.length)
            }
            onClick={() => void issue()}
          >
            <Plus className="size-4" aria-hidden />{" "}
            {draft.willNeedApproval ? "Send for approval" : "Issue certificate"}
          </Btn>
        </Card>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ templates & settings

function Setup({
  settings,
  templates,
  onChanged,
}: {
  settings: Settings;
  templates: Template[];
  onChanged: () => void;
}) {
  const [s, setS] = useState(settings);
  const [dues, setDues] = useState(settings.noDuesItems.join("\n"));
  const [edit, setEdit] = useState<Template | "new" | null>(null);
  const { busy, run } = useAction();
  if (edit)
    return (
      <TemplateEditor
        tpl={edit === "new" ? null : edit}
        custom={settings.customTemplates}
        onDone={() => {
          setEdit(null);
          onChanged();
        }}
      />
    );
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card className="space-y-3 self-start">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">Templates</h2>
          {settings.customTemplates && (
            <Btn size="sm" onClick={() => setEdit("new")}>
              Add template
            </Btn>
          )}
        </div>
        <ul className="divide-y divide-black/5">
          {templates.map((t) => (
            <li key={t.id} className="flex items-center justify-between gap-2 py-2 text-sm">
              <span>
                {t.name}{" "}
                <span className="text-ink-muted">
                  · next {t.prefix}
                  {String(t.nextNumber).padStart(4, "0")}
                  {t.parentRequestable ? " · parents can request" : ""}
                  {t.active ? "" : " · off"}
                </span>
              </span>
              <Btn size="sm" variant="ghost" onClick={() => setEdit(t)}>
                Edit
              </Btn>
            </li>
          ))}
        </ul>
      </Card>
      <Card className="space-y-3 self-start">
        <h2 className="text-lg font-semibold">Settings</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Signed by (name)">
            <input
              className={inputCls}
              value={s.signatoryName ?? ""}
              maxLength={120}
              onChange={(e) => setS({ ...s, signatoryName: e.target.value })}
            />
          </Field>
          <Field label="Title">
            <input
              className={inputCls}
              value={s.signatoryTitle}
              maxLength={60}
              onChange={(e) => setS({ ...s, signatoryTitle: e.target.value })}
            />
          </Field>
          <Field label="Affiliation no.">
            <input
              className={inputCls}
              value={s.affiliationNo ?? ""}
              maxLength={40}
              onChange={(e) => setS({ ...s, affiliationNo: e.target.value })}
            />
          </Field>
          <Field label="School code">
            <input
              className={inputCls}
              value={s.schoolCode ?? ""}
              maxLength={40}
              onChange={(e) => setS({ ...s, schoolCode: e.target.value })}
            />
          </Field>
        </div>
        <Field label="School address">
          <input
            className={inputCls}
            value={s.schoolAddress ?? ""}
            maxLength={300}
            onChange={(e) => setS({ ...s, schoolAddress: e.target.value })}
          />
        </Field>
        <Field label="No-dues items for a TC" hint="One per line.">
          <textarea
            rows={4}
            className={inputCls}
            value={dues}
            onChange={(e) => setDues(e.target.value)}
          />
        </Field>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            disabled={!settings.approvalFeature}
            checked={s.requireApproval}
            onChange={(e) => setS({ ...s, requireApproval: e.target.checked })}
          />
          Certificates prepared by staff need the principal&apos;s approval{" "}
          {settings.approvalFeature ? "" : "(Standard)"}
        </label>
        <Btn
          variant="primary"
          busy={busy === "save"}
          onClick={async () => {
            const r = await run(
              "save",
              () =>
                sb("certificates/settings", {
                  method: "PUT",
                  body: {
                    signatoryName: s.signatoryName ?? "",
                    signatoryTitle: s.signatoryTitle,
                    schoolAddress: s.schoolAddress ?? "",
                    affiliationNo: s.affiliationNo ?? "",
                    schoolCode: s.schoolCode ?? "",
                    requireApproval: s.requireApproval,
                    noDuesItems: dues.split("\n"),
                  },
                }),
              "Saved.",
            );
            if (r) onChanged();
          }}
        >
          Save settings
        </Btn>
      </Card>
    </div>
  );
}

function TemplateEditor({
  tpl,
  custom,
  onDone,
}: {
  tpl: Template | null;
  custom: boolean;
  onDone: () => void;
}) {
  const [t, setT] = useState<Omit<Template, "id" | "kind">>(
    tpl ?? {
      name: "",
      title: "",
      body: "",
      prefix: "",
      nextNumber: 1,
      parentRequestable: false,
      active: true,
    },
  );
  const { busy, run } = useAction();
  const wording = custom && tpl?.kind !== "TC";
  return (
    <Card className="max-w-3xl space-y-3">
      <button
        type="button"
        onClick={onDone}
        className="text-ink-muted hover:text-ink inline-flex items-center gap-1 text-sm"
      >
        <ArrowLeft className="size-4" aria-hidden /> Templates
      </button>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Name">
          <input
            className={inputCls}
            value={t.name}
            maxLength={80}
            disabled={!custom}
            onChange={(e) => setT({ ...t, name: e.target.value })}
          />
        </Field>
        <Field label="Title on the certificate">
          <input
            className={inputCls}
            value={t.title}
            maxLength={120}
            disabled={!wording}
            onChange={(e) => setT({ ...t, title: e.target.value })}
          />
        </Field>
        <Field label="Number prefix">
          <input
            className={inputCls}
            value={t.prefix}
            maxLength={20}
            onChange={(e) => setT({ ...t, prefix: e.target.value })}
          />
        </Field>
        <Field label="Next number">
          <input
            type="number"
            min={1}
            className={inputCls}
            value={t.nextNumber}
            onChange={(e) => setT({ ...t, nextNumber: Number(e.target.value) })}
          />
        </Field>
      </div>
      <Field
        label={tpl?.kind === "TC" ? "Closing line" : "Text"}
        hint={
          wording
            ? "Merge fields: {{student.name}} {{student.relation}} {{guardian.name}} {{student.admission_no}} {{student.class}} {{student.dob}} {{student.dob_words}} {{student.admission_date}} {{student.possessive}} {{school.name}} {{year}} {{date}} {{purpose}}"
            : "Changing the wording is part of ShikshaBlox Standard."
        }
      >
        <textarea
          rows={8}
          className={inputCls}
          value={t.body}
          disabled={!wording && tpl !== null}
          maxLength={4000}
          onChange={(e) => setT({ ...t, body: e.target.value })}
        />
      </Field>
      {tpl?.kind !== "TC" && (
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={t.parentRequestable}
            onChange={(e) => setT({ ...t, parentRequestable: e.target.checked })}
          />{" "}
          Parents can request this in the portal
        </label>
      )}
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={t.active}
          onChange={(e) => setT({ ...t, active: e.target.checked })}
        />{" "}
        In use
      </label>
      <Btn
        variant="primary"
        busy={busy === "save"}
        onClick={async () => {
          const r = await run(
            "save",
            () =>
              tpl
                ? sb(`certificates/templates/${tpl.id}`, { method: "PUT", body: t })
                : sb("certificates/templates", { body: t }),
            "Template saved.",
          );
          if (r) onDone();
        }}
      >
        Save template
      </Btn>
    </Card>
  );
}
