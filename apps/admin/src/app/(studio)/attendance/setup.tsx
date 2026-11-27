"use client";

import { useCallback, useEffect, useState } from "react";
import { Copy, UserPlus } from "lucide-react";
import { sb } from "@/lib/studio";
import { Btn, Card, Empty, Field, Loading, inputCls, useAction, useToast } from "@/components/kit";
import type { Settings, Teacher, Teachers } from "./types";

type NewTeacher = { teacher: Teacher; temporaryPassword?: string | null; existingAccount: boolean };

export function SetupView({ settings, onChanged }: { settings: Settings; onChanged: () => void }) {
  return (
    <div className="grid gap-6 lg:grid-cols-[3fr_2fr]">
      <TeachersCard />
      <SettingsCard settings={settings} onChanged={onChanged} />
    </div>
  );
}

function TeachersCard() {
  const toast = useToast();
  const [data, setData] = useState<Teachers | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [created, setCreated] = useState<NewTeacher | null>(null);
  const { busy, run } = useAction();

  const load = useCallback(async () => {
    try {
      setData(await sb<Teachers>("attendance/teachers"));
    } catch (e) {
      setErr((e as Error).message);
    }
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  async function add() {
    const r = await run("add", () =>
      sb<NewTeacher>("attendance/teachers", { body: { name, email } }),
    );
    if (r) {
      setCreated(r);
      setName("");
      setEmail("");
      void load();
    }
  }

  async function assign(sectionId: string, ids: string[]) {
    const r = await run(
      sectionId,
      () =>
        sb<Teachers>(`attendance/sections/${sectionId}/teachers`, {
          method: "PUT",
          body: { userIds: ids },
        }),
      "Saved.",
    );
    if (r) setData(r);
  }

  if (err) return <Empty>{err}</Empty>;
  if (!data) return <Loading />;
  return (
    <Card>
      <h2 className="text-lg font-semibold">Teachers and their classes</h2>
      <p className="text-ink-muted mt-1 text-sm">
        A class teacher takes attendance and approves leave for their class. Subject teachers can be
        added for period-wise attendance.
      </p>
      {data.sections.length === 0 ? (
        <p className="text-ink-muted mt-4 text-sm">Add classes under Students first.</p>
      ) : (
        <ul className="mt-4 divide-y divide-black/5">
          {data.sections.map((s) => (
            <li key={s.sectionId} className="flex flex-wrap items-center gap-3 py-2.5">
              <span className="w-28 shrink-0 font-medium">{s.label}</span>
              <div className="flex min-w-[12rem] flex-1 flex-wrap items-center gap-1.5">
                {s.teachers.map((t) => (
                  <span
                    key={t.id}
                    className="inline-flex items-center gap-1 rounded-full bg-black/[0.05] py-1 pr-1 pl-2.5 text-xs"
                  >
                    {t.name}
                    <button
                      type="button"
                      aria-label={`Remove ${t.name} from ${s.label}`}
                      className="text-ink-muted hover:text-ink grid size-5 place-items-center rounded-full hover:bg-black/10"
                      onClick={() =>
                        void assign(
                          s.sectionId,
                          s.teachers.filter((x) => x.id !== t.id).map((x) => x.id),
                        )
                      }
                    >
                      ×
                    </button>
                  </span>
                ))}
                <select
                  aria-label={`Add a teacher to ${s.label}`}
                  className={`${inputCls} w-auto! py-1 text-xs`}
                  value=""
                  disabled={busy === s.sectionId}
                  onChange={(e) =>
                    e.target.value &&
                    void assign(s.sectionId, [...s.teachers.map((x) => x.id), e.target.value])
                  }
                >
                  <option value="">+ Add</option>
                  {data.staff
                    .filter((t) => !s.teachers.some((x) => x.id === t.id))
                    .map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                      </option>
                    ))}
                </select>
              </div>
            </li>
          ))}
        </ul>
      )}

      <div className="mt-6 border-t border-black/5 pt-4">
        <h3 className="font-semibold">Add a teacher</h3>
        <div className="mt-3 grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
          <Field label="Name">
            <input
              className={inputCls}
              value={name}
              maxLength={120}
              onChange={(e) => setName(e.target.value)}
            />
          </Field>
          <Field label="Email (used to sign in)">
            <input
              type="email"
              className={inputCls}
              value={email}
              maxLength={200}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>
          <Btn
            variant="primary"
            busy={busy === "add"}
            disabled={!name.trim() || !email.trim()}
            onClick={() => void add()}
          >
            <UserPlus className="size-4" aria-hidden /> Add
          </Btn>
        </div>
        {created && (
          <div
            role="status"
            className="mt-4 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-900"
          >
            {created.existingAccount ? (
              <p>
                {created.teacher.name} already has a ShikshaBlox account and can now take
                attendance. They sign in with their usual password.
              </p>
            ) : (
              <>
                <p>
                  Account created for {created.teacher.name}. Share these sign-in details privately;
                  the password is shown only once.
                </p>
                <p className="mt-2 flex flex-wrap items-center gap-2 font-mono">
                  {created.teacher.email} · {created.temporaryPassword}
                  <button
                    type="button"
                    className="inline-flex items-center gap-1 rounded-full bg-white px-2 py-0.5 font-sans text-xs ring-1 ring-emerald-600/30"
                    onClick={() => {
                      void navigator.clipboard?.writeText(
                        `${created.teacher.email} / ${created.temporaryPassword}`,
                      );
                      toast("Copied.");
                    }}
                  >
                    <Copy className="size-3" aria-hidden /> Copy
                  </button>
                </p>
              </>
            )}
          </div>
        )}
      </div>
    </Card>
  );
}

function SettingsCard({ settings, onChanged }: { settings: Settings; onChanged: () => void }) {
  const [s, setS] = useState(settings);
  const [codes, setCodes] = useState(settings.reasonCodes.join("\n"));
  const { busy, run } = useAction();

  async function save() {
    const r = await run(
      "save",
      () =>
        sb<Settings>("attendance/settings", {
          method: "PUT",
          body: {
            mode: s.mode,
            periodsPerDay: s.periodsPerDay,
            alertAbsent: s.alertAbsent,
            alertLate: s.alertLate,
            minPercent: s.minPercent,
            concernPercent: s.concernPercent,
            concernStreak: s.concernStreak,
            reasonCodes: codes.split("\n"),
          },
        }),
      "Settings saved.",
    );
    if (r) {
      setS(r);
      setCodes(r.reasonCodes.join("\n"));
      onChanged();
    }
  }

  const num =
    (k: "periodsPerDay" | "minPercent" | "concernPercent" | "concernStreak") =>
    (e: React.ChangeEvent<HTMLInputElement>) =>
      setS({ ...s, [k]: Number(e.target.value) });

  return (
    <Card className="space-y-4 self-start">
      <h2 className="text-lg font-semibold">Settings</h2>
      <Field
        label="How attendance is taken"
        hint={
          s.periodsAllowed
            ? "In period-wise mode, period 1 counts as the day's attendance."
            : "Period-wise attendance is part of ShikshaBlox Standard."
        }
      >
        <select
          className={inputCls}
          value={s.mode}
          onChange={(e) => setS({ ...s, mode: e.target.value as Settings["mode"] })}
        >
          <option value="DAILY">Once a day</option>
          <option value="PERIOD" disabled={!s.periodsAllowed}>
            Every period
          </option>
        </select>
      </Field>
      {s.mode === "PERIOD" && (
        <Field label="Periods per day">
          <input
            type="number"
            min={1}
            max={12}
            className={`${inputCls} w-24!`}
            value={s.periodsPerDay}
            onChange={num("periodsPerDay")}
          />
        </Field>
      )}
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Alerts to parents</legend>
        {!s.messagingAvailable && (
          <p className="text-ink-muted text-xs">Needs the communication hub in your plan.</p>
        )}
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={s.alertAbsent}
            onChange={(e) => setS({ ...s, alertAbsent: e.target.checked })}
          />
          Message parents when their child is absent
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={s.alertLate}
            onChange={(e) => setS({ ...s, alertLate: e.target.checked })}
          />
          Message parents when their child is late
        </label>
        <p className="text-ink-muted text-xs">
          Sent once a day through Messages, using the “attendance_alert” template and each
          parent&apos;s opt-ins. Not sent for children on approved leave.
        </p>
      </fieldset>
      <Field label="Minimum attendance for eligibility (%)">
        <input
          type="number"
          min={0}
          max={100}
          className={`${inputCls} w-24!`}
          value={s.minPercent}
          onChange={num("minPercent")}
        />
      </Field>
      {s.concernsAllowed && (
        <div className="grid grid-cols-2 gap-3">
          <Field label="Needs attention below (%)">
            <input
              type="number"
              min={0}
              max={100}
              className={inputCls}
              value={s.concernPercent}
              onChange={num("concernPercent")}
            />
          </Field>
          <Field label="…or absent days in a row">
            <input
              type="number"
              min={2}
              max={30}
              className={inputCls}
              value={s.concernStreak}
              onChange={num("concernStreak")}
            />
          </Field>
        </div>
      )}
      {s.reasonsAllowed && (
        <Field label="Absence reasons" hint="One per line.">
          <textarea
            rows={4}
            className={inputCls}
            value={codes}
            onChange={(e) => setCodes(e.target.value)}
          />
        </Field>
      )}
      <Btn variant="primary" busy={busy === "save"} onClick={() => void save()}>
        Save settings
      </Btn>
    </Card>
  );
}
