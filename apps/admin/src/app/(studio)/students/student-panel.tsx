"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, Download, ShieldAlert, Trash2, Upload, UserPlus, X } from "lucide-react";
import { sb } from "@/lib/studio";
import { Badge, Btn, Card, Field, inputCls, useAction } from "@/components/kit";
import { PortalAccess } from "./portal-access";
import {
  APAAR,
  GENDER,
  OUTCOME,
  RELATION,
  STATUS_LABEL,
  fmtDay,
  today,
  type Detail,
  type GuardianLink,
  type Section,
} from "./types";

async function downloadDoc(id: string) {
  const r = await sb<{ url: string }>(`sis/documents/${id}/download`);
  const a = document.createElement("a");
  a.href = r.url;
  a.rel = "noopener noreferrer";
  document.body.appendChild(a);
  a.click();
  a.remove();
}

type GForm = { name: string; phone: string; email: string; relation: string; canPickup: boolean };
const emptyGuardian: GForm = {
  name: "",
  phone: "",
  email: "",
  relation: "MOTHER",
  canPickup: true,
};

export function NewStudent({
  sections,
  onClose,
  onCreated,
}: {
  sections: Section[];
  onClose: () => void;
  onCreated: (id: string) => void;
}) {
  const [f, setF] = useState({
    name: "",
    admissionNo: "",
    dateOfBirth: "",
    gender: "",
    sectionId: sections[0]?.id ?? "",
    rollNo: "",
    address: "",
  });
  const [g, setG] = useState<GForm>(emptyGuardian);
  const { busy, run } = useAction();
  async function save(e: React.FormEvent) {
    e.preventDefault();
    const r = await run(
      "add",
      () =>
        sb<Detail>("sis/students", {
          body: {
            name: f.name,
            admissionNo: f.admissionNo || null,
            dateOfBirth: f.dateOfBirth || null,
            gender: f.gender || null,
            sectionId: f.sectionId,
            rollNo: f.rollNo ? Number(f.rollNo) : null,
            address: f.address || null,
            guardians: g.name
              ? [
                  {
                    name: g.name,
                    phone: g.phone || null,
                    email: g.email || null,
                    relation: g.relation,
                    canPickup: g.canPickup,
                    primary: true,
                  },
                ]
              : [],
          },
        }),
      "Student added",
    );
    if (r) onCreated(r.student.id);
  }
  return (
    <Card className="mb-5">
      <form onSubmit={(e) => void save(e)}>
        <h2 className="text-lg font-semibold">Add a student</h2>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Full name *">
            <input
              className={inputCls}
              required
              maxLength={120}
              value={f.name}
              onChange={(e) => setF({ ...f, name: e.target.value })}
            />
          </Field>
          <Field label="Class & section *">
            <select
              className={inputCls}
              required
              value={f.sectionId}
              onChange={(e) => setF({ ...f, sectionId: e.target.value })}
            >
              {sections.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Date of birth">
            <input
              className={inputCls}
              type="date"
              max={today()}
              value={f.dateOfBirth}
              onChange={(e) => setF({ ...f, dateOfBirth: e.target.value })}
            />
          </Field>
          <Field label="Gender">
            <select
              className={inputCls}
              value={f.gender}
              onChange={(e) => setF({ ...f, gender: e.target.value })}
            >
              <option value="">—</option>
              {Object.entries(GENDER).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Admission no." hint="Leave empty to number automatically">
            <input
              className={inputCls}
              maxLength={30}
              value={f.admissionNo}
              onChange={(e) => setF({ ...f, admissionNo: e.target.value })}
            />
          </Field>
          <Field label="Roll no.">
            <input
              className={inputCls}
              type="number"
              min={1}
              max={999}
              value={f.rollNo}
              onChange={(e) => setF({ ...f, rollNo: e.target.value })}
            />
          </Field>
          <div className="sm:col-span-2">
            <Field label="Address">
              <input
                className={inputCls}
                maxLength={500}
                value={f.address}
                onChange={(e) => setF({ ...f, address: e.target.value })}
              />
            </Field>
          </div>
        </div>
        <h3 className="mt-5 mb-2 text-sm font-semibold">Parent / guardian</h3>
        <GuardianFields g={g} setG={setG} />
        <div className="mt-4 flex gap-2">
          <Btn type="submit" variant="primary" busy={busy === "add"}>
            Add student
          </Btn>
          <Btn variant="ghost" onClick={onClose}>
            Cancel
          </Btn>
        </div>
      </form>
    </Card>
  );
}

function GuardianFields({ g, setG }: { g: GForm; setG: (g: GForm) => void }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <Field label="Name">
        <input
          className={inputCls}
          maxLength={120}
          value={g.name}
          onChange={(e) => setG({ ...g, name: e.target.value })}
        />
      </Field>
      <Field label="Relation">
        <select
          className={inputCls}
          value={g.relation}
          onChange={(e) => setG({ ...g, relation: e.target.value })}
        >
          {Object.entries(RELATION).map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Mobile" hint="Existing parent with this number is linked (siblings)">
        <input
          className={inputCls}
          type="tel"
          maxLength={20}
          value={g.phone}
          onChange={(e) => setG({ ...g, phone: e.target.value })}
        />
      </Field>
      <Field label="Email">
        <input
          className={inputCls}
          type="email"
          maxLength={200}
          value={g.email}
          onChange={(e) => setG({ ...g, email: e.target.value })}
        />
      </Field>
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          className="size-4"
          checked={g.canPickup}
          onChange={(e) => setG({ ...g, canPickup: e.target.checked })}
        />{" "}
        May collect the child
      </label>
    </div>
  );
}

export function StudentPanel({
  id,
  sections,
  onClose,
  onChanged,
}: {
  id: string;
  sections: Section[];
  onClose: () => void;
  onChanged: () => void;
}) {
  const [d, setD] = useState<Detail | null>(null);
  const [tab, setTab] = useState<"profile" | "guardians" | "records">("profile");
  const { busy, run } = useAction();

  useEffect(() => {
    let live = true;
    void sb<Detail>(`sis/students/${id}`).then((r) => live && setD(r));
    return () => {
      live = false;
    };
  }, [id]);

  if (!d) {
    return (
      <Card className="h-fit">
        <p className="text-ink-muted text-sm">Loading…</p>
      </Card>
    );
  }
  const s = d.student;
  const current = d.enrolments[0];
  const done = (r: Detail | null | undefined) => {
    if (r) {
      setD(r);
      onChanged();
    }
  };

  return (
    <Card className="h-fit xl:sticky xl:top-24">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-lg font-semibold">{s.name}</h2>
          <p className="text-ink-muted text-xs">
            {[
              s.admissionNo,
              current?.sectionLabel,
              current?.rollNo != null ? `Roll ${current.rollNo}` : null,
            ]
              .filter(Boolean)
              .join(" · ")}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="text-ink-muted hover:text-ink rounded-full p-1.5 hover:bg-black/5"
          aria-label="Close student"
        >
          <X className="size-4" />
        </button>
      </div>
      <div className="mt-2 flex flex-wrap gap-1.5">
        <Badge tone={s.status === "ENROLLED" ? "green" : "gray"}>{STATUS_LABEL[s.status]}</Badge>
        <Badge
          tone={
            s.apaarStatus === "GENERATED"
              ? "green"
              : s.apaarStatus === "EXCEPTION" || s.apaarStatus === "CONSENT_REFUSED"
                ? "red"
                : "amber"
          }
        >
          APAAR: {APAAR[s.apaarStatus]}
        </Badge>
      </div>
      {s.medicalAlerts && (
        <p className="mt-3 flex gap-2 rounded-xl bg-amber-50 p-2.5 text-sm text-amber-900">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden /> {s.medicalAlerts}
        </p>
      )}
      {s.custodyNote && (
        <p className="mt-2 flex gap-2 rounded-xl bg-red-50 p-2.5 text-sm text-red-900">
          <ShieldAlert className="mt-0.5 size-4 shrink-0" aria-hidden /> {s.custodyNote}
        </p>
      )}
      <div className="mt-4 flex gap-1.5">
        {(
          [
            ["profile", "Profile"],
            ["guardians", `Guardians (${d.guardians.length})`],
            ["records", "Class & records"],
          ] as const
        ).map(([k, label]) => (
          <Btn
            key={k}
            size="sm"
            variant={tab === k ? "primary" : "secondary"}
            onClick={() => setTab(k)}
          >
            {label}
          </Btn>
        ))}
      </div>
      <div className="mt-4">
        {tab === "profile" ? (
          <Profile d={d} busy={busy} run={run} onSaved={done} />
        ) : tab === "guardians" ? (
          <Guardians d={d} busy={busy} run={run} onSaved={done} />
        ) : (
          <Records
            d={d}
            sections={sections}
            busy={busy}
            run={run}
            onSaved={done}
            onDeleted={() => {
              onChanged();
              onClose();
            }}
          />
        )}
      </div>
    </Card>
  );
}

type RunFn = <T>(key: string, fn: () => Promise<T>, ok?: string) => Promise<T | undefined>;

function Profile({
  d,
  busy,
  run,
  onSaved,
}: {
  d: Detail;
  busy: string | null;
  run: RunFn;
  onSaved: (r: Detail | undefined) => void;
}) {
  const s = d.student;
  const [f, setF] = useState({
    name: s.name,
    admissionNo: s.admissionNo,
    dateOfBirth: s.dateOfBirth ?? "",
    gender: s.gender ?? "",
    bloodGroup: s.bloodGroup ?? "",
    admissionDate: s.admissionDate ?? "",
    previousSchool: s.previousSchool ?? "",
    address: s.address ?? "",
    house: s.house ?? "",
    medicalAlerts: s.medicalAlerts ?? "",
    custodyNote: s.custodyNote ?? "",
    apaarId: s.apaarId ?? "",
    apaarStatus: s.apaarStatus,
    apaarNote: s.apaarNote ?? "",
  });
  const [custom, setCustom] = useState<Record<string, string>>(
    Object.fromEntries(Object.entries(s.custom ?? {}).map(([k, v]) => [k, String(v)])),
  );
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF({ ...f, [k]: e.target.value });
  async function save() {
    const body = {
      ...Object.fromEntries(Object.entries(f).map(([k, v]) => [k, v === "" ? null : v])),
      name: f.name,
      custom,
    };
    onSaved(
      await run("save", () => sb<Detail>(`sis/students/${s.id}`, { method: "PUT", body }), "Saved"),
    );
  }
  return (
    <div>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Full name *">
          <input className={inputCls} maxLength={120} value={f.name} onChange={set("name")} />
        </Field>
        <Field label="Admission no.">
          <input
            className={inputCls}
            maxLength={30}
            value={f.admissionNo}
            onChange={set("admissionNo")}
          />
        </Field>
        <Field label="Date of birth">
          <input
            className={inputCls}
            type="date"
            max={today()}
            value={f.dateOfBirth}
            onChange={set("dateOfBirth")}
          />
        </Field>
        <Field label="Gender">
          <select className={inputCls} value={f.gender} onChange={set("gender")}>
            <option value="">—</option>
            {Object.entries(GENDER).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Blood group">
          <select className={inputCls} value={f.bloodGroup} onChange={set("bloodGroup")}>
            <option value="">—</option>
            {["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"].map((b) => (
              <option key={b}>{b}</option>
            ))}
          </select>
        </Field>
        <Field label="House">
          <input className={inputCls} maxLength={40} value={f.house} onChange={set("house")} />
        </Field>
        <Field label="Admitted on">
          <input
            className={inputCls}
            type="date"
            value={f.admissionDate}
            onChange={set("admissionDate")}
          />
        </Field>
        <Field label="Previous school">
          <input
            className={inputCls}
            maxLength={200}
            value={f.previousSchool}
            onChange={set("previousSchool")}
          />
        </Field>
        <div className="sm:col-span-2">
          <Field label="Address">
            <textarea
              className={inputCls}
              rows={2}
              maxLength={500}
              value={f.address}
              onChange={set("address")}
            />
          </Field>
        </div>
        <div className="sm:col-span-2">
          <Field
            label="Medical alerts"
            hint="Allergies, conditions, medicines: shown prominently to staff"
          >
            <textarea
              className={inputCls}
              rows={2}
              maxLength={1000}
              value={f.medicalAlerts}
              onChange={set("medicalAlerts")}
            />
          </Field>
        </div>
        <div className="sm:col-span-2">
          <Field
            label="Custody / pickup note"
            hint="e.g. court order, who may NOT collect the child"
          >
            <textarea
              className={inputCls}
              rows={2}
              maxLength={1000}
              value={f.custodyNote}
              onChange={set("custodyNote")}
            />
          </Field>
        </div>
      </div>
      <fieldset className="mt-4 rounded-xl bg-black/[0.03] p-3">
        <legend className="px-1 text-sm font-medium">APAAR ID</legend>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Status">
            <select className={inputCls} value={f.apaarStatus} onChange={set("apaarStatus")}>
              {Object.entries(APAAR).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </Field>
          <Field label="APAAR ID (12 digits)">
            <input
              className={inputCls}
              inputMode="numeric"
              maxLength={12}
              value={f.apaarId}
              onChange={(e) => setF({ ...f, apaarId: e.target.value.replace(/\D/g, "") })}
            />
          </Field>
          <div className="sm:col-span-2">
            <Field label="Note (consent date, exception reason)">
              <input
                className={inputCls}
                maxLength={300}
                value={f.apaarNote}
                onChange={set("apaarNote")}
              />
            </Field>
          </div>
        </div>
      </fieldset>
      {d.fields.length > 0 && (
        <fieldset className="mt-4 grid gap-3 sm:grid-cols-2">
          <legend className="mb-2 text-sm font-medium">More details</legend>
          {d.fields.map((fl) => (
            <Field key={fl.id} label={fl.label}>
              {fl.type === "CHOICE" ? (
                <select
                  className={inputCls}
                  value={custom[fl.key] ?? ""}
                  onChange={(e) => setCustom({ ...custom, [fl.key]: e.target.value })}
                >
                  <option value="">—</option>
                  {fl.options.map((o) => (
                    <option key={o}>{o}</option>
                  ))}
                </select>
              ) : fl.type === "YES_NO" ? (
                <select
                  className={inputCls}
                  value={custom[fl.key] ?? ""}
                  onChange={(e) => setCustom({ ...custom, [fl.key]: e.target.value })}
                >
                  <option value="">—</option>
                  <option value="true">Yes</option>
                  <option value="false">No</option>
                </select>
              ) : (
                <input
                  className={inputCls}
                  type={fl.type === "NUMBER" ? "number" : fl.type === "DATE" ? "date" : "text"}
                  value={custom[fl.key] ?? ""}
                  onChange={(e) => setCustom({ ...custom, [fl.key]: e.target.value })}
                />
              )}
            </Field>
          ))}
        </fieldset>
      )}
      <Btn variant="primary" className="mt-4" busy={busy === "save"} onClick={() => void save()}>
        Save profile
      </Btn>
    </div>
  );
}

function Guardians({
  d,
  busy,
  run,
  onSaved,
}: {
  d: Detail;
  busy: string | null;
  run: RunFn;
  onSaved: (r: Detail | undefined) => void;
}) {
  const [adding, setAdding] = useState(false);
  const [g, setG] = useState<GForm>(emptyGuardian);
  const sid = d.student.id;
  async function add() {
    const r = await run(
      "g-add",
      () =>
        sb<Detail>(`sis/students/${sid}/guardians`, {
          body: {
            name: g.name,
            phone: g.phone || null,
            email: g.email || null,
            relation: g.relation,
            canPickup: g.canPickup,
          },
        }),
      "Guardian added",
    );
    if (r) {
      setAdding(false);
      setG(emptyGuardian);
      onSaved(r);
    }
  }
  async function patch(x: GuardianLink, body: Record<string, unknown>, ok: string) {
    onSaved(
      await run(
        "g" + x.guardianId,
        () =>
          sb<Detail>(`sis/students/${sid}/guardians/${x.guardianId}`, { method: "PATCH", body }),
        ok,
      ),
    );
  }
  async function remove(x: GuardianLink) {
    if (!confirm(`Remove ${x.name} from ${d.student.name}?`)) return;
    onSaved(
      await run(
        "g" + x.guardianId,
        () => sb<Detail>(`sis/students/${sid}/guardians/${x.guardianId}`, { method: "DELETE" }),
        "Removed",
      ),
    );
  }
  return (
    <div>
      <ul className="space-y-2">
        {d.guardians.map((x) => (
          <li key={x.guardianId} className="rounded-xl p-3 ring-1 ring-black/10">
            <div className="flex flex-wrap items-start gap-2">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold">
                  {x.name}{" "}
                  <span className="text-ink-muted font-normal">· {RELATION[x.relation]}</span>
                </p>
                <p className="text-ink-muted text-xs">
                  {[x.phone, x.email, x.occupation].filter(Boolean).join(" · ")}
                </p>
                {x.siblings.length > 0 && (
                  <p className="text-ink-muted text-xs">Also parent of {x.siblings.join(", ")}</p>
                )}
              </div>
              <span className="flex flex-wrap gap-1">
                {x.primary && <Badge tone="blue">Primary</Badge>}
                {!x.canPickup && <Badge tone="red">May not collect</Badge>}
                {x.emergency && <Badge>Emergency</Badge>}
              </span>
            </div>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {!x.primary && (
                <Btn
                  size="sm"
                  variant="ghost"
                  onClick={() => void patch(x, { primary: true }, "Primary contact set")}
                >
                  Make primary
                </Btn>
              )}
              <Btn
                size="sm"
                variant="ghost"
                onClick={() =>
                  void patch(
                    x,
                    { canPickup: !x.canPickup },
                    x.canPickup ? "Pickup blocked" : "Pickup allowed",
                  )
                }
              >
                {x.canPickup ? "Block pickup" : "Allow pickup"}
              </Btn>
              <Btn
                size="sm"
                variant="ghost"
                busy={busy === "g" + x.guardianId}
                onClick={() => void remove(x)}
                aria-label={`Remove ${x.name}`}
              >
                <Trash2 className="size-3.5" aria-hidden />
              </Btn>
            </div>
          </li>
        ))}
      </ul>
      {adding ? (
        <div className="mt-3 rounded-xl bg-black/[0.03] p-3">
          <GuardianFields g={g} setG={setG} />
          <div className="mt-3 flex gap-2">
            <Btn
              variant="primary"
              busy={busy === "g-add"}
              disabled={!g.name.trim()}
              onClick={() => void add()}
            >
              Add
            </Btn>
            <Btn variant="ghost" onClick={() => setAdding(false)}>
              Cancel
            </Btn>
          </div>
        </div>
      ) : (
        <Btn className="mt-3" onClick={() => setAdding(true)}>
          <UserPlus className="size-4" aria-hidden /> Add guardian
        </Btn>
      )}
      <p className="text-ink-muted mt-3 text-xs">
        Guardians are added to Messages (tagged with the class) but only receive messages once they
        have opted in there.
      </p>
      <PortalAccess key={d.guardians.map((g) => g.guardianId).join()} studentId={sid} />
    </div>
  );
}

function Records({
  d,
  sections,
  busy,
  run,
  onSaved,
  onDeleted,
}: {
  d: Detail;
  sections: Section[];
  busy: string | null;
  run: RunFn;
  onSaved: (r: Detail | undefined) => void;
  onDeleted: () => void;
}) {
  const s = d.student;
  const [move, setMove] = useState({
    sectionId: d.enrolments[0]?.sectionId ?? sections[0]?.id ?? "",
    rollNo: d.enrolments[0]?.rollNo?.toString() ?? "",
  });
  const [leave, setLeave] = useState({ reason: "", date: today(), alumni: false, open: false });
  const [title, setTitle] = useState("");
  async function upload(file: File | undefined) {
    if (!file || !title.trim()) return;
    const form = new FormData();
    form.set("file", file);
    const r = await run(
      "up",
      () =>
        sb<Detail>(`sis/students/${s.id}/documents?title=${encodeURIComponent(title)}`, { form }),
      "Uploaded",
    );
    if (r) {
      setTitle("");
      onSaved(r);
    }
  }
  async function removeDoc(id: string) {
    if (!confirm("Delete this document?")) return;
    if (
      await run(
        "doc" + id,
        () => sb(`sis/documents/${id}`, { method: "DELETE" }).then(() => true),
        "Deleted",
      )
    ) {
      onSaved(await sb<Detail>(`sis/students/${s.id}`));
    }
  }
  async function del() {
    if (
      !confirm(
        `Delete ${s.name}? Use this only for a student added by mistake; otherwise withdraw them.`,
      )
    )
      return;
    if (
      await run(
        "del",
        () => sb(`sis/students/${s.id}`, { method: "DELETE" }).then(() => true),
        "Deleted",
      )
    )
      onDeleted();
  }
  return (
    <div className="space-y-5">
      {s.status === "ENROLLED" ? (
        <div className="rounded-xl bg-black/[0.03] p-3">
          <p className="mb-2 text-sm font-medium">Class this year</p>
          <div className="flex flex-wrap items-end gap-2">
            <select
              className={`${inputCls} w-auto!`}
              value={move.sectionId}
              onChange={(e) => setMove({ ...move, sectionId: e.target.value })}
              aria-label="Section"
            >
              {sections.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.label}
                </option>
              ))}
            </select>
            <input
              className={`${inputCls} w-24!`}
              type="number"
              min={1}
              max={999}
              placeholder="Roll"
              value={move.rollNo}
              onChange={(e) => setMove({ ...move, rollNo: e.target.value })}
              aria-label="Roll number"
            />
            <Btn
              busy={busy === "move"}
              onClick={() =>
                void run(
                  "move",
                  () =>
                    sb<Detail>(`sis/students/${s.id}/section`, {
                      body: {
                        sectionId: move.sectionId,
                        rollNo: move.rollNo ? Number(move.rollNo) : null,
                      },
                    }),
                  "Saved",
                ).then(onSaved)
              }
            >
              Save
            </Btn>
          </div>
        </div>
      ) : (
        <div className="rounded-xl bg-black/[0.03] p-3 text-sm">
          {STATUS_LABEL[s.status]} on {fmtDay(s.leavingDate)}
          {s.leavingReason ? `: ${s.leavingReason}` : ""}
          <div className="mt-2 flex flex-wrap items-end gap-2">
            <select
              className={`${inputCls} w-auto!`}
              value={move.sectionId}
              onChange={(e) => setMove({ ...move, sectionId: e.target.value })}
              aria-label="Section to re-admit into"
            >
              {sections.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.label}
                </option>
              ))}
            </select>
            <Btn
              size="sm"
              busy={busy === "re"}
              onClick={() =>
                void run(
                  "re",
                  () =>
                    sb<Detail>(`sis/students/${s.id}/reinstate`, {
                      body: { sectionId: move.sectionId },
                    }),
                  "Re-admitted",
                ).then(onSaved)
              }
            >
              Re-admit
            </Btn>
          </div>
        </div>
      )}
      <div>
        <p className="mb-2 text-sm font-medium">Class history</p>
        <ul className="text-sm">
          {d.enrolments.map((e) => (
            <li
              key={e.id}
              className="flex justify-between border-b border-black/5 py-1.5 last:border-0"
            >
              <span>
                {e.academicYear} · {e.sectionLabel}
                {e.rollNo != null ? ` · Roll ${e.rollNo}` : ""}
              </span>
              <span className="text-ink-muted">{e.outcome ? OUTCOME[e.outcome] : "Current"}</span>
            </li>
          ))}
        </ul>
      </div>
      <div>
        <p className="mb-2 text-sm font-medium">Documents</p>
        <ul className="space-y-1.5">
          {d.documents.map((doc) => (
            <li key={doc.id} className="flex items-center gap-2 text-sm">
              <span className="min-w-0 flex-1 truncate">{doc.title}</span>
              <Btn
                size="sm"
                variant="ghost"
                onClick={() => void run("dl" + doc.id, () => downloadDoc(doc.id))}
                aria-label={`Download ${doc.title}`}
              >
                <Download className="size-3.5" aria-hidden />
              </Btn>
              <Btn
                size="sm"
                variant="ghost"
                busy={busy === "doc" + doc.id}
                onClick={() => void removeDoc(doc.id)}
                aria-label={`Delete ${doc.title}`}
              >
                <Trash2 className="size-3.5" aria-hidden />
              </Btn>
            </li>
          ))}
        </ul>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <input
            className={`${inputCls} min-w-0 flex-1`}
            maxLength={120}
            placeholder="Document title, e.g. Birth certificate"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            aria-label="Document title"
          />
          <label
            className={`inline-flex h-10 cursor-pointer items-center gap-1.5 rounded-full bg-white px-4 text-sm font-medium ring-1 ring-black/10 ${!title.trim() ? "opacity-50" : ""}`}
          >
            <Upload className="size-4" aria-hidden /> Upload
            <input
              type="file"
              accept="application/pdf,image/jpeg,image/png"
              className="sr-only"
              disabled={!title.trim()}
              onChange={(e) => void upload(e.target.files?.[0])}
            />
          </label>
        </div>
      </div>
      {s.status === "ENROLLED" && (
        <div>
          {leave.open ? (
            <div className="rounded-xl bg-red-50 p-3">
              <div className="grid gap-2 sm:grid-cols-2">
                <Field label="Leaving date">
                  <input
                    className={inputCls}
                    type="date"
                    value={leave.date}
                    onChange={(e) => setLeave({ ...leave, date: e.target.value })}
                  />
                </Field>
                <Field label="Reason *">
                  <input
                    className={inputCls}
                    maxLength={300}
                    value={leave.reason}
                    onChange={(e) => setLeave({ ...leave, reason: e.target.value })}
                    placeholder="e.g. Family relocating"
                  />
                </Field>
              </div>
              <label className="mt-2 flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  className="size-4"
                  checked={leave.alumni}
                  onChange={(e) => setLeave({ ...leave, alumni: e.target.checked })}
                />{" "}
                Completed school (mark as alumni)
              </label>
              <div className="mt-2 flex gap-2">
                <Btn
                  variant="danger"
                  busy={busy === "leave"}
                  disabled={!leave.reason.trim()}
                  onClick={() =>
                    void run(
                      "leave",
                      () =>
                        sb<Detail>(`sis/students/${s.id}/withdraw`, {
                          body: { date: leave.date, reason: leave.reason, alumni: leave.alumni },
                        }),
                      "Withdrawn",
                    ).then(onSaved)
                  }
                >
                  Withdraw
                </Btn>
                <Btn variant="ghost" onClick={() => setLeave({ ...leave, open: false })}>
                  Cancel
                </Btn>
              </div>
            </div>
          ) : (
            <Btn variant="ghost" onClick={() => setLeave({ ...leave, open: true })}>
              Withdraw student…
            </Btn>
          )}
        </div>
      )}
      {d.enrolments.length <= 1 && (
        <Btn size="sm" variant="ghost" busy={busy === "del"} onClick={() => void del()}>
          <Trash2 className="size-3.5" aria-hidden /> Delete (added by mistake)
        </Btn>
      )}
    </div>
  );
}
