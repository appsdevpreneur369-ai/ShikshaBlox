"use client";

import { useEffect, useState } from "react";
import { ArrowRight, Plus, Trash2, Upload } from "lucide-react";
import { sb } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Field, Loading, inputCls, useAction } from "@/components/kit";
import type { Field as CustomField, Section, Settings } from "./types";

// ------------------------------------------------------------------ classes

export function ClassesView({
  year,
  sections,
  onChanged,
}: {
  year: string;
  sections: Section[];
  onChanged: () => void;
}) {
  const [text, setText] = useState("");
  const { busy, run } = useAction();
  const [teacher, setTeacher] = useState<Record<string, string>>({});

  async function add() {
    const grades = text
      .split("\n")
      .map((l) => l.trim())
      .filter(Boolean)
      .map((l) => {
        const [grade, secs] = l.split(":").map((x) => x.trim());
        return { grade: grade!, sections: secs ? secs.split(/[,\s]+/).filter(Boolean) : ["A"] };
      });
    if (
      await run(
        "add",
        () => sb("sis/sections", { body: { academicYear: year, grades } }),
        "Classes added",
      )
    ) {
      setText("");
      onChanged();
    }
  }
  async function saveTeacher(s: Section) {
    if (
      await run(
        "t" + s.id,
        () =>
          sb(`sis/sections/${s.id}`, {
            method: "PATCH",
            body: { classTeacher: teacher[s.id] ?? s.classTeacher ?? "" },
          }),
        "Saved",
      )
    )
      onChanged();
  }
  async function remove(s: Section) {
    if (!confirm(`Delete ${s.label}?`)) return;
    if (await run("d" + s.id, () => sb(`sis/sections/${s.id}`, { method: "DELETE" }), "Deleted"))
      onChanged();
  }

  return (
    <div className="grid gap-5 lg:grid-cols-[1.5fr_1fr]">
      <div>
        {sections.length === 0 ? (
          <Empty>No classes for {year} yet. Add them on the right.</Empty>
        ) : (
          <div className="overflow-x-auto rounded-2xl bg-white ring-1 ring-black/5">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-ink-muted text-left text-xs">
                  <th className="px-3 py-2 font-medium">Class</th>
                  <th className="px-3 py-2 font-medium">Students</th>
                  <th className="px-3 py-2 font-medium">Class teacher</th>
                  <th className="px-3 py-2">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {sections.map((s) => (
                  <tr key={s.id} className="border-t border-black/5">
                    <td className="px-3 py-2 font-medium whitespace-nowrap">{s.label}</td>
                    <td className="px-3 py-2 tabular-nums">{s.students}</td>
                    <td className="px-3 py-2">
                      <div className="flex gap-1.5">
                        <input
                          className={`${inputCls} min-w-[9rem]`}
                          maxLength={120}
                          value={teacher[s.id] ?? s.classTeacher ?? ""}
                          onChange={(e) => setTeacher({ ...teacher, [s.id]: e.target.value })}
                          aria-label={`Class teacher of ${s.label}`}
                        />
                        {teacher[s.id] !== undefined &&
                          teacher[s.id] !== (s.classTeacher ?? "") && (
                            <Btn
                              size="sm"
                              busy={busy === "t" + s.id}
                              onClick={() => void saveTeacher(s)}
                            >
                              Save
                            </Btn>
                          )}
                      </div>
                    </td>
                    <td className="px-3 py-2 text-right">
                      {s.students === 0 && (
                        <Btn
                          size="sm"
                          variant="ghost"
                          busy={busy === "d" + s.id}
                          onClick={() => void remove(s)}
                          aria-label={`Delete ${s.label}`}
                        >
                          <Trash2 className="size-3.5" aria-hidden />
                        </Btn>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <Card className="h-fit">
        <h2 className="text-lg font-semibold">Add classes for {year}</h2>
        <p className="text-ink-muted mt-1 text-sm">
          One class per line, lowest first, with its sections after a colon.
        </p>
        <textarea
          className={`${inputCls} mt-3 font-mono text-sm`}
          rows={8}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={"Nursery: A\nLKG: A, B\nUKG: A, B\nClass 1: A, B, C"}
          aria-label="Classes and sections"
        />
        <Btn
          variant="primary"
          className="mt-3"
          busy={busy === "add"}
          disabled={!text.trim()}
          onClick={() => void add()}
        >
          <Plus className="size-4" aria-hidden /> Add
        </Btn>
      </Card>
    </div>
  );
}

// ------------------------------------------------------------------ promotion

type PlanStudent = { id: string; name: string; admissionNo: string; rollNo?: number | null };
type PlanSection = {
  sectionId: string;
  label: string;
  grade: string;
  section: string;
  targetGrade?: string | null;
  students: PlanStudent[];
};
type Plan = { fromYear: string; toYear: string; grades: string[]; sections: PlanSection[] };
type Result = {
  newYear: string;
  promoted: number;
  retained: number;
  graduated: number;
  left: number;
};

export function PromotionView({ onDone }: { onDone: () => void }) {
  const [plan, setPlan] = useState<Plan | null>(null);
  const [targets, setTargets] = useState<Record<string, string>>({});
  const [choice, setChoice] = useState<Record<string, "PROMOTE" | "RETAIN" | "LEAVE">>({});
  const [open, setOpen] = useState<string | null>(null);
  const [res, setRes] = useState<Result | null>(null);
  const [confirmText, setConfirmText] = useState("");
  const { busy, run } = useAction();

  useEffect(() => {
    void sb<Plan>("sis/promotion").then((p) => {
      setPlan(p);
      setTargets(Object.fromEntries(p.sections.map((s) => [s.sectionId, s.targetGrade ?? ""])));
    });
  }, []);

  if (res) {
    return (
      <Card className="max-w-xl">
        <h2 className="text-lg font-semibold">Welcome to {res.newYear}</h2>
        <p className="mt-2 text-sm">
          {res.promoted} promoted · {res.retained} retained · {res.graduated} graduated · {res.left}{" "}
          left
        </p>
        <p className="text-ink-muted mt-2 text-sm">
          Next year&apos;s sections were created; set class teachers in the Classes tab.
        </p>
      </Card>
    );
  }
  if (!plan) return <Loading />;
  const total = plan.sections.reduce((n, s) => n + s.students.length, 0);
  if (total === 0) return <Empty>No enrolled students in {plan.fromYear} to promote.</Empty>;

  async function promote() {
    const retained = Object.entries(choice)
      .filter(([, v]) => v === "RETAIN")
      .map(([k]) => k);
    const leaving = Object.entries(choice)
      .filter(([, v]) => v === "LEAVE")
      .map(([k]) => k);
    const r = await run(
      "go",
      () =>
        sb<Result>("sis/promotion", {
          body: {
            fromYear: plan!.fromYear,
            targets: plan!.sections.map((s) => ({
              sectionId: s.sectionId,
              targetGrade: targets[s.sectionId] || null,
            })),
            retained,
            leaving,
          },
        }),
      "School year moved forward",
    );
    if (r) {
      setRes(r);
      onDone();
    }
  }

  return (
    <div>
      <Card className="mb-5">
        <h2 className="text-lg font-semibold">
          Year-end promotion: {plan.fromYear} <ArrowRight className="inline size-4" aria-hidden />{" "}
          {plan.toYear}
        </h2>
        <p className="text-ink-muted mt-1 text-sm">
          Each section moves to the class you choose (same section letter). Open a section to mark
          students who repeat the year or leave. Classes with no next class graduate (alumni).
        </p>
      </Card>
      <ul className="space-y-2">
        {plan.sections.map((s) => {
          const exceptions = s.students.filter(
            (p) => choice[p.id] && choice[p.id] !== "PROMOTE",
          ).length;
          return (
            <li key={s.sectionId} className="rounded-2xl bg-white p-3 ring-1 ring-black/5">
              <div className="flex flex-wrap items-center gap-2">
                <span className="w-32 font-semibold">{s.label}</span>
                <span className="text-ink-muted text-sm">{s.students.length} students</span>
                <ArrowRight className="text-ink-muted size-4" aria-hidden />
                <select
                  className={`${inputCls} w-auto!`}
                  value={targets[s.sectionId] ?? ""}
                  onChange={(e) => setTargets({ ...targets, [s.sectionId]: e.target.value })}
                  aria-label={`Next class for ${s.label}`}
                >
                  {plan.grades.map((g) => (
                    <option key={g} value={g}>
                      {g} {s.section}
                    </option>
                  ))}
                  <option value="">Graduates (leaves school)</option>
                </select>
                {exceptions > 0 && <Badge tone="amber">{exceptions} exceptions</Badge>}
                {s.students.length > 0 && (
                  <Btn
                    size="sm"
                    variant="ghost"
                    className="ml-auto"
                    onClick={() => setOpen(open === s.sectionId ? null : s.sectionId)}
                  >
                    {open === s.sectionId ? "Hide students" : "Students…"}
                  </Btn>
                )}
              </div>
              {open === s.sectionId && (
                <ul className="mt-2 divide-y divide-black/5 text-sm">
                  {s.students.map((p) => (
                    <li key={p.id} className="flex flex-wrap items-center gap-2 py-1.5">
                      <span className="min-w-[10rem] flex-1">
                        {p.name} <span className="text-ink-muted text-xs">{p.admissionNo}</span>
                      </span>
                      <select
                        className={`${inputCls} w-auto!`}
                        value={choice[p.id] ?? "PROMOTE"}
                        onChange={(e) =>
                          setChoice({
                            ...choice,
                            [p.id]: e.target.value as "PROMOTE" | "RETAIN" | "LEAVE",
                          })
                        }
                        aria-label={`What happens to ${p.name}`}
                      >
                        <option value="PROMOTE">
                          {targets[s.sectionId] ? "Moves up" : "Graduates"}
                        </option>
                        <option value="RETAIN">Repeats {s.grade}</option>
                        <option value="LEAVE">Leaves school</option>
                      </select>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          );
        })}
      </ul>
      <Card className="mt-5">
        <p className="text-sm">
          This moves all {total} students into {plan.toYear} and makes it the current year. Type{" "}
          <strong>{plan.toYear}</strong> to confirm.
        </p>
        <div className="mt-2 flex flex-wrap gap-2">
          <input
            className={`${inputCls} w-40!`}
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            aria-label="Type the new year to confirm"
          />
          <Btn
            variant="primary"
            busy={busy === "go"}
            disabled={confirmText.trim() !== plan.toYear}
            onClick={() => void promote()}
          >
            Promote to {plan.toYear}
          </Btn>
        </div>
      </Card>
    </div>
  );
}

// ------------------------------------------------------------------ import & settings

type ImportResult = { added: number; updated: number; skipped: number; errors: string[] };
type Readiness = {
  enrolled: number;
  missingDob: number;
  missingGender: number;
  missingAddress: number;
  missingGuardianPhone: number;
  apaarGenerated: number;
  apaarConsentPending: number;
  apaarNotStarted: number;
  withoutSection: number;
};

export function SetupView({ settings, onChanged }: { settings: Settings; onChanged: () => void }) {
  const [csv, setCsv] = useState("");
  const [res, setRes] = useState<ImportResult | null>(null);
  const [ready, setReady] = useState<Readiness | null>(null);
  const [fields, setFields] = useState<CustomField[]>([]);
  const [nf, setNf] = useState({ label: "", type: "TEXT", options: "" });
  const [num, setNum] = useState({
    prefix: settings.admissionPrefix,
    next: String(settings.nextAdmissionNo),
  });
  const { busy, run } = useAction();

  useEffect(() => {
    void sb<Readiness>("sis/readiness").then(setReady);
    void sb<CustomField[]>("sis/fields").then(setFields);
  }, []);

  async function doImport() {
    const r = await run("imp", () => sb<ImportResult>("sis/students/import", { body: { csv } }));
    if (r) {
      setRes(r);
      onChanged();
      void sb<Readiness>("sis/readiness").then(setReady);
    }
  }
  async function addField() {
    const r = await run(
      "f",
      () =>
        sb<CustomField[]>("sis/fields", {
          body: {
            label: nf.label,
            type: nf.type,
            options: nf.options
              .split(",")
              .map((o) => o.trim())
              .filter(Boolean),
          },
        }),
      "Field added",
    );
    if (r) {
      setFields(r);
      setNf({ label: "", type: "TEXT", options: "" });
    }
  }
  async function delField(f: CustomField) {
    if (!confirm(`Delete the field “${f.label}”? Values already entered stay hidden.`)) return;
    const r = await run(
      "fd" + f.id,
      () => sb<CustomField[]>(`sis/fields/${f.id}`, { method: "DELETE" }),
      "Deleted",
    );
    if (r) setFields(r);
  }
  async function saveNumbering() {
    if (
      await run(
        "num",
        () =>
          sb("sis/settings", {
            method: "PUT",
            body: { admissionPrefix: num.prefix, nextAdmissionNo: Number(num.next) },
          }),
        "Saved",
      )
    )
      onChanged();
  }

  const gap = (n: number) =>
    n > 0 ? (
      <span className="font-semibold text-amber-700">{n}</span>
    ) : (
      <span className="text-emerald-700">0</span>
    );
  return (
    <div className="grid gap-5 lg:grid-cols-2">
      <Card>
        <h2 className="text-lg font-semibold">UDISE+ readiness · {settings.currentYear}</h2>
        {!ready ? (
          <Loading />
        ) : (
          <ul className="mt-3 space-y-1.5 text-sm">
            <li className="flex justify-between">
              <span>Enrolled students</span>
              <strong>{ready.enrolled}</strong>
            </li>
            <li className="flex justify-between">
              <span>Missing date of birth</span>
              {gap(ready.missingDob)}
            </li>
            <li className="flex justify-between">
              <span>Missing gender</span>
              {gap(ready.missingGender)}
            </li>
            <li className="flex justify-between">
              <span>Missing address</span>
              {gap(ready.missingAddress)}
            </li>
            <li className="flex justify-between">
              <span>No guardian phone</span>
              {gap(ready.missingGuardianPhone)}
            </li>
            <li className="flex justify-between">
              <span>Not in a section this year</span>
              {gap(ready.withoutSection)}
            </li>
            <li className="flex justify-between border-t border-black/5 pt-1.5">
              <span>APAAR ID generated</span>
              <span>
                {ready.apaarGenerated} / {ready.enrolled}
              </span>
            </li>
            <li className="flex justify-between">
              <span>APAAR consent pending / given</span>
              <span>{ready.apaarConsentPending}</span>
            </li>
            <li className="flex justify-between">
              <span>APAAR not started</span>
              {gap(ready.apaarNotStarted)}
            </li>
          </ul>
        )}
        <p className="text-ink-muted mt-3 text-xs">
          Complete these before preparing the school&apos;s UDISE+ return. ShikshaBlox does not
          submit to UDISE+ directly.
        </p>
      </Card>
      <Card>
        <h2 className="text-lg font-semibold">Import students</h2>
        <p className="text-ink-muted mt-1 text-sm">
          CSV with a header row:{" "}
          <code>
            admission_no,name,dob,gender,class,section,roll_no,address,guardian_name,guardian_relation,guardian_phone,guardian_email,guardian2_name,guardian2_relation,guardian2_phone
          </code>
          . Dates as DD-MM-YYYY or YYYY-MM-DD. Existing admission numbers are updated; missing
          classes are created for {settings.currentYear}.
        </p>
        <label className="mt-3 inline-flex h-10 cursor-pointer items-center gap-1.5 rounded-full bg-white px-4 text-sm font-medium ring-1 ring-black/10">
          <Upload className="size-4" aria-hidden /> Choose CSV file
          <input
            type="file"
            accept=".csv,text/csv"
            className="sr-only"
            onChange={async (e) => setCsv((await e.target.files?.[0]?.text()) ?? "")}
          />
        </label>
        <textarea
          className={`${inputCls} mt-3 font-mono text-xs`}
          rows={4}
          value={csv}
          onChange={(e) => setCsv(e.target.value)}
          placeholder={
            "admission_no,name,dob,gender,class,section,guardian_name,guardian_relation,guardian_phone\nA101,Aarav Rao,15-06-2019,M,Class 1,A,Lakshmi Rao,mother,+91 98480 00000"
          }
          aria-label="CSV contents"
        />
        <Btn
          variant="primary"
          className="mt-3"
          busy={busy === "imp"}
          disabled={!csv.trim()}
          onClick={() => void doImport()}
        >
          Import
        </Btn>
        {res && (
          <div className="mt-3 text-sm">
            Added {res.added}, updated {res.updated}, skipped {res.skipped}.
            {res.errors.length > 0 && (
              <ul className="mt-1 list-disc pl-5 text-xs text-red-700">
                {res.errors.map((e, i) => (
                  <li key={i}>{e}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </Card>
      <Card>
        <h2 className="text-lg font-semibold">Admission numbers</h2>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <Field label="Prefix">
            <input
              className={inputCls}
              maxLength={10}
              value={num.prefix}
              onChange={(e) => setNum({ ...num, prefix: e.target.value })}
              placeholder="e.g. GDS/"
            />
          </Field>
          <Field label="Next number">
            <input
              className={inputCls}
              type="number"
              min={1}
              value={num.next}
              onChange={(e) => setNum({ ...num, next: e.target.value })}
            />
          </Field>
        </div>
        <p className="text-ink-muted mt-2 text-sm">
          Next student gets{" "}
          <strong>
            {num.prefix}
            {String(num.next || 1).padStart(4, "0")}
          </strong>{" "}
          unless you type a number.
        </p>
        <Btn className="mt-3" busy={busy === "num"} onClick={() => void saveNumbering()}>
          Save
        </Btn>
      </Card>
      <Card>
        <h2 className="text-lg font-semibold">Extra student fields</h2>
        <ul className="mt-2 space-y-1 text-sm">
          {fields.map((f) => (
            <li key={f.id} className="flex items-center justify-between gap-2">
              <span>
                {f.label}{" "}
                <span className="text-ink-muted text-xs">
                  · {f.type.toLowerCase().replace("_", "/")}
                </span>
              </span>
              <Btn
                size="sm"
                variant="ghost"
                busy={busy === "fd" + f.id}
                onClick={() => void delField(f)}
                aria-label={`Delete ${f.label}`}
              >
                <Trash2 className="size-3.5" aria-hidden />
              </Btn>
            </li>
          ))}
        </ul>
        <div className="mt-3 grid gap-2 sm:grid-cols-3">
          <input
            className={inputCls}
            maxLength={80}
            placeholder="e.g. Mother tongue"
            value={nf.label}
            onChange={(e) => setNf({ ...nf, label: e.target.value })}
            aria-label="Field label"
          />
          <select
            className={inputCls}
            value={nf.type}
            onChange={(e) => setNf({ ...nf, type: e.target.value })}
            aria-label="Field type"
          >
            <option value="TEXT">Text</option>
            <option value="NUMBER">Number</option>
            <option value="DATE">Date</option>
            <option value="CHOICE">Choice</option>
            <option value="YES_NO">Yes / no</option>
          </select>
          {nf.type === "CHOICE" && (
            <input
              className={inputCls}
              placeholder="Options, comma-separated"
              value={nf.options}
              onChange={(e) => setNf({ ...nf, options: e.target.value })}
              aria-label="Options"
            />
          )}
        </div>
        <Btn
          className="mt-2"
          busy={busy === "f"}
          disabled={!nf.label.trim()}
          onClick={() => void addField()}
        >
          <Plus className="size-4" aria-hidden /> Add field
        </Btn>
      </Card>
    </div>
  );
}
