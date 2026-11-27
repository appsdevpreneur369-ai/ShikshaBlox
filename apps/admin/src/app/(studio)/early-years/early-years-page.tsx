"use client";

import { useEffect, useState } from "react";
import { Printer, Trash2 } from "lucide-react";
import { sb, StudioError } from "@/lib/studio";
import {
  Btn,
  Card,
  Empty,
  Field,
  Loading,
  PageHeader,
  inputCls,
  useAction,
} from "@/components/kit";

type Section = { id: string; label: string };
type Milestone = { id: string; domain: string; statement: string };
type Overview = {
  sections: Section[];
  milestones: Milestone[];
  meals: string[];
  photos: boolean;
  stories: boolean;
  pickup: boolean;
};
type DiaryRow = {
  studentId: string;
  name: string;
  arrival?: string | null;
  meals: Record<string, string>;
  napFrom?: string | null;
  napTo?: string | null;
  toilet?: number | null;
  mood?: string | null;
  activities?: string | null;
  note?: string | null;
  saved: boolean;
};
type Board = {
  milestones: Milestone[];
  children: { studentId: string; name: string; status: Record<string, string> }[];
};
type Obs = {
  id: string;
  studentId: string;
  studentName: string;
  observedOn: string;
  domain?: string | null;
  note: string;
  hasPhoto: boolean;
  photoUrl?: string | null;
  shareWithFamily: boolean;
  by?: string | null;
};
type Pickup = {
  id: string;
  name: string;
  relation?: string | null;
  phone?: string | null;
  hasPhoto: boolean;
  photoUrl?: string | null;
};
type Story = {
  school: string;
  child: string;
  classLabel: string;
  from: string;
  to: string;
  observations: Obs[];
  achieved: Record<string, string[]>;
  emerging: Record<string, string[]>;
};

const todayIst = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
const day = (d: string) =>
  new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" }).format(
    new Date(`${d}T00:00:00`),
  );
const MOODS: [string, string][] = [
  ["HAPPY", "😊 Happy"],
  ["CALM", "🙂 Calm"],
  ["TIRED", "😴 Tired"],
  ["UPSET", "😢 Upset"],
];
const MEALS: [string, string][] = [
  ["ALL", "All"],
  ["MOST", "Most"],
  ["SOME", "Some"],
  ["NONE", "None"],
];
const STATUS: [string, string, string][] = [
  ["NOT_YET", "Not yet", "bg-gray-200"],
  ["EMERGING", "Emerging", "bg-amber-300"],
  ["ACHIEVED", "Achieved", "bg-emerald-500 text-white"],
];

type Tab = "diary" | "milestones" | "obs" | "child";

export function EarlyYearsPage() {
  const [ov, setOv] = useState<Overview | null>(null);
  const [sectionId, setSectionId] = useState("");
  const [tab, setTab] = useState<Tab>("diary");
  const [blocked, setBlocked] = useState(false);
  useEffect(() => {
    const t = setTimeout(async () => {
      try {
        const o = await sb<Overview>("early-years/overview");
        setOv(o);
        setSectionId(o.sections[0]?.id ?? "");
      } catch (e) {
        if (e instanceof StudioError && e.status === 403) setBlocked(true);
      }
    }, 0);
    return () => clearTimeout(t);
  }, []);
  if (blocked)
    return (
      <div>
        <PageHeader title="Early years" />
        <Card className="max-w-2xl">
          <p className="text-sm">Early years is not part of your plan yet.</p>
        </Card>
      </div>
    );
  if (!ov) return <Loading />;
  const tabs: [Tab, string][] = [
    ["diary", "Daily diary"],
    ["milestones", "Milestones"],
    ["obs", "Observations"],
    ["child", "Child: story & pickup"],
  ];
  return (
    <div>
      <PageHeader
        title="Early years"
        subtitle="Diary, milestones and observations for pre-primary classes. Families see them in the parent portal."
      />
      {ov.sections.length === 0 ? (
        <Empty>You are not the class teacher of a pre-primary class (Nursery, LKG, UKG).</Empty>
      ) : (
        <>
          <div className="mb-5 flex flex-wrap items-center gap-2 print:hidden">
            <select
              aria-label="Class"
              className={`${inputCls} w-auto!`}
              value={sectionId}
              onChange={(e) => setSectionId(e.target.value)}
            >
              {ov.sections.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.label}
                </option>
              ))}
            </select>
            {tabs.map(([k, label]) => (
              <button
                key={k}
                type="button"
                onClick={() => setTab(k)}
                className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition ${tab === k ? "bg-primary text-on-primary" : "text-ink-muted hover:text-ink bg-white ring-1 ring-black/10"}`}
              >
                {label}
              </button>
            ))}
          </div>
          {tab === "diary" ? (
            <DiaryView key={sectionId} ov={ov} sectionId={sectionId} />
          ) : tab === "milestones" ? (
            <MilestonesView key={sectionId} sectionId={sectionId} />
          ) : tab === "obs" ? (
            <ObsView key={sectionId} ov={ov} sectionId={sectionId} />
          ) : (
            <ChildView key={sectionId} ov={ov} sectionId={sectionId} />
          )}
        </>
      )}
    </div>
  );
}

function DiaryView({ ov, sectionId }: { ov: Overview; sectionId: string }) {
  const [date, setDate] = useState(todayIst());
  const [rows, setRows] = useState<DiaryRow[] | null>(null);
  const { busy, run } = useAction();
  useEffect(() => {
    const t = setTimeout(
      async () =>
        setRows(await sb<DiaryRow[]>(`early-years/diary?sectionId=${sectionId}&date=${date}`)),
      0,
    );
    return () => clearTimeout(t);
  }, [sectionId, date]);
  if (!rows) return <Loading />;
  const set = (i: number, p: Partial<DiaryRow>) =>
    setRows(rows.map((r, j) => (j === i ? { ...r, ...p } : r)));
  return (
    <div className="space-y-3 pb-20">
      <Field label="Date">
        <input
          type="date"
          className={`${inputCls} w-auto!`}
          max={todayIst()}
          value={date}
          onChange={(e) => e.target.value && setDate(e.target.value)}
        />
      </Field>
      {rows.length === 0 && <Empty>No children in this class.</Empty>}
      {rows.map((r, i) => (
        <Card key={r.studentId} className="space-y-2">
          <p className="font-semibold">{r.name}</p>
          <div className="flex flex-wrap gap-3 text-sm">
            <label>
              Arrived{" "}
              <input
                type="time"
                className={`${inputCls} inline-block w-auto! py-1`}
                value={r.arrival?.slice(0, 5) ?? ""}
                onChange={(e) => set(i, { arrival: e.target.value || null })}
              />
            </label>
            {ov.meals.map((m) => (
              <label key={m}>
                {m}{" "}
                <select
                  aria-label={`${m} for ${r.name}`}
                  className={`${inputCls} inline-block w-auto! py-1`}
                  value={r.meals[m] ?? ""}
                  onChange={(e) => set(i, { meals: { ...r.meals, [m]: e.target.value } })}
                >
                  <option value="">–</option>
                  {MEALS.map(([k, l]) => (
                    <option key={k} value={k}>
                      {l}
                    </option>
                  ))}
                </select>
              </label>
            ))}
            <label>
              Nap{" "}
              <input
                type="time"
                aria-label={`Nap from for ${r.name}`}
                className={`${inputCls} inline-block w-auto! py-1`}
                value={r.napFrom?.slice(0, 5) ?? ""}
                onChange={(e) => set(i, { napFrom: e.target.value || null })}
              />{" "}
              –{" "}
              <input
                type="time"
                aria-label={`Nap to for ${r.name}`}
                className={`${inputCls} inline-block w-auto! py-1`}
                value={r.napTo?.slice(0, 5) ?? ""}
                onChange={(e) => set(i, { napTo: e.target.value || null })}
              />
            </label>
            <label>
              Toilet{" "}
              <input
                type="number"
                min={0}
                max={20}
                className={`${inputCls} inline-block w-16! py-1`}
                value={r.toilet ?? ""}
                onChange={(e) =>
                  set(i, { toilet: e.target.value === "" ? null : Number(e.target.value) })
                }
              />
            </label>
          </div>
          <div
            role="radiogroup"
            aria-label={`Mood of ${r.name}`}
            className="flex flex-wrap gap-1.5"
          >
            {MOODS.map(([k, l]) => (
              <button
                key={k}
                type="button"
                role="radio"
                aria-checked={r.mood === k}
                onClick={() => set(i, { mood: r.mood === k ? null : k })}
                className={`rounded-full px-3 py-1 text-sm ${r.mood === k ? "bg-primary text-on-primary" : "bg-black/[0.05]"}`}
              >
                {l}
              </button>
            ))}
          </div>
          <input
            aria-label={`Activities for ${r.name}`}
            placeholder="Activities"
            className={inputCls}
            maxLength={500}
            value={r.activities ?? ""}
            onChange={(e) => set(i, { activities: e.target.value })}
          />
          <input
            aria-label={`Note for ${r.name}`}
            placeholder="Note for the family (optional)"
            className={inputCls}
            maxLength={500}
            value={r.note ?? ""}
            onChange={(e) => set(i, { note: e.target.value })}
          />
        </Card>
      ))}
      {rows.length > 0 && (
        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-black/5 bg-white/95 backdrop-blur">
          <div className="mx-auto flex max-w-6xl justify-end px-4 py-3">
            <Btn
              variant="primary"
              busy={busy === "save"}
              onClick={async () => {
                const r = await run(
                  "save",
                  () =>
                    sb<DiaryRow[]>(`early-years/diary?sectionId=${sectionId}&date=${date}`, {
                      method: "PUT",
                      body: rows.map((x) => ({
                        studentId: x.studentId,
                        arrival: x.arrival || null,
                        meals: x.meals,
                        napFrom: x.napFrom || null,
                        napTo: x.napTo || null,
                        toilet: x.toilet ?? null,
                        mood: x.mood || null,
                        activities: x.activities || null,
                        note: x.note || null,
                      })),
                    }),
                  "Diary saved.",
                );
                if (r) setRows(r);
              }}
            >
              Save diary
            </Btn>
          </div>
        </div>
      )}
    </div>
  );
}

function MilestonesView({ sectionId }: { sectionId: string }) {
  const [b, setB] = useState<Board | null>(null);
  const { run } = useAction();
  useEffect(() => {
    const t = setTimeout(
      async () => setB(await sb<Board>(`early-years/milestones?sectionId=${sectionId}`)),
      0,
    );
    return () => clearTimeout(t);
  }, [sectionId]);
  if (!b) return <Loading />;
  const domains = [...new Set(b.milestones.map((m) => m.domain))];
  async function cycle(studentId: string, milestoneId: string, current?: string) {
    const order = ["", "NOT_YET", "EMERGING", "ACHIEVED"];
    const next = order[(order.indexOf(current ?? "") + 1) % order.length]!;
    const r = await run(studentId + milestoneId, () =>
      sb("early-years/milestones", {
        method: "PUT",
        body: { studentId, milestoneId, status: next || null },
      }).then(() => true),
    );
    if (r)
      setB({
        ...b!,
        children: b!.children.map((c) =>
          c.studentId === studentId ? { ...c, status: { ...c.status, [milestoneId]: next } } : c,
        ),
      });
  }
  return (
    <div className="space-y-4">
      <p className="text-ink-muted text-sm">
        Tap a cell to move it: blank → not yet → emerging → achieved.
      </p>
      {domains.map((d) => (
        <Card key={d}>
          <h3 className="mb-2 font-semibold">{d}</h3>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[36rem] text-xs">
              <thead>
                <tr className="text-ink-muted text-left">
                  <th scope="col" className="w-40 py-1 font-medium">
                    Child
                  </th>
                  {b.milestones
                    .filter((m) => m.domain === d)
                    .map((m) => (
                      <th key={m.id} scope="col" className="px-1 py-1 font-medium">
                        {m.statement}
                      </th>
                    ))}
                </tr>
              </thead>
              <tbody>
                {b.children.map((c) => (
                  <tr key={c.studentId} className="border-t border-black/5">
                    <th scope="row" className="py-1 text-left font-medium">
                      {c.name}
                    </th>
                    {b.milestones
                      .filter((m) => m.domain === d)
                      .map((m) => {
                        const s = STATUS.find(([k]) => k === c.status[m.id]);
                        return (
                          <td key={m.id} className="px-1 py-1">
                            <button
                              type="button"
                              aria-label={`${c.name}: ${m.statement}: ${s?.[1] ?? "not assessed"}`}
                              onClick={() => void cycle(c.studentId, m.id, c.status[m.id])}
                              className={`w-full rounded-md px-1 py-1.5 ${s?.[2] ?? "bg-black/[0.03]"}`}
                            >
                              {s?.[1] ?? "–"}
                            </button>
                          </td>
                        );
                      })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      ))}
    </div>
  );
}

function ObsView({ ov, sectionId }: { ov: Overview; sectionId: string }) {
  const [rows, setRows] = useState<Obs[] | null>(null);
  const [kids, setKids] = useState<{ studentId: string; name: string }[]>([]);
  const [form, setForm] = useState({
    studentId: "",
    domain: "",
    note: "",
    share: true,
    date: todayIst(),
  });
  const [photo, setPhoto] = useState<File | null>(null);
  const { busy, run } = useAction();
  const [v, setV] = useState(0);
  useEffect(() => {
    const t = setTimeout(async () => {
      setRows(await sb<Obs[]>(`early-years/observations?sectionId=${sectionId}`));
      const d = await sb<DiaryRow[]>(`early-years/diary?sectionId=${sectionId}`);
      setKids(d.map((x) => ({ studentId: x.studentId, name: x.name })));
    }, 0);
    return () => clearTimeout(t);
  }, [sectionId, v]);
  if (!rows) return <Loading />;
  const domains = [...new Set(ov.milestones.map((m) => m.domain))];
  return (
    <div className="space-y-4">
      <Card className="space-y-3">
        <h2 className="font-semibold">New observation</h2>
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="Child">
            <select
              className={inputCls}
              value={form.studentId}
              onChange={(e) => setForm({ ...form, studentId: e.target.value })}
            >
              <option value="">Choose…</option>
              {kids.map((k) => (
                <option key={k.studentId} value={k.studentId}>
                  {k.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Area">
            <select
              className={inputCls}
              value={form.domain}
              onChange={(e) => setForm({ ...form, domain: e.target.value })}
            >
              <option value="">—</option>
              {domains.map((d) => (
                <option key={d}>{d}</option>
              ))}
            </select>
          </Field>
          <Field label="Date">
            <input
              type="date"
              className={inputCls}
              max={todayIst()}
              value={form.date}
              onChange={(e) => setForm({ ...form, date: e.target.value })}
            />
          </Field>
        </div>
        <Field label="What you saw">
          <textarea
            rows={3}
            className={inputCls}
            maxLength={2000}
            value={form.note}
            onChange={(e) => setForm({ ...form, note: e.target.value })}
          />
        </Field>
        {ov.photos && (
          <Field label="Photo (optional, JPG or PNG)">
            <input
              type="file"
              accept="image/jpeg,image/png"
              className="text-sm"
              onChange={(e) => setPhoto(e.target.files?.[0] ?? null)}
            />
          </Field>
        )}
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={form.share}
            onChange={(e) => setForm({ ...form, share: e.target.checked })}
          />{" "}
          Share with the family
        </label>
        <Btn
          variant="primary"
          busy={busy === "add"}
          disabled={!form.studentId || !form.note.trim()}
          onClick={async () => {
            const fd = new FormData();
            fd.append("studentId", form.studentId);
            fd.append("note", form.note);
            fd.append("date", form.date);
            fd.append("share", String(form.share));
            if (form.domain) fd.append("domain", form.domain);
            if (photo) fd.append("photo", photo);
            const r = await run(
              "add",
              () => sb("early-years/observations", { form: fd }),
              "Observation saved.",
            );
            if (r) {
              setForm({ ...form, note: "" });
              setPhoto(null);
              setV(v + 1);
            }
          }}
        >
          Save observation
        </Btn>
      </Card>
      {rows.length === 0 ? (
        <Empty>No observations yet.</Empty>
      ) : (
        <ul className="space-y-3">
          {rows.map((o) => (
            <li key={o.id}>
              <Card className="flex gap-3">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                {o.photoUrl && (
                  <img
                    src={o.photoUrl}
                    alt={`Observation of ${o.studentName}`}
                    className="size-20 shrink-0 rounded-lg object-cover"
                  />
                )}
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold">
                    {o.studentName}{" "}
                    <span className="text-ink-muted font-normal">
                      · {day(o.observedOn)}
                      {o.domain ? ` · ${o.domain}` : ""}
                      {o.shareWithFamily ? "" : " · staff only"}
                    </span>
                  </p>
                  <p className="text-sm">{o.note}</p>
                </div>
                <Btn
                  size="sm"
                  variant="ghost"
                  aria-label="Delete observation"
                  onClick={async () => {
                    const r = await run(o.id, () =>
                      sb(`early-years/observations/${o.id}`, { method: "DELETE" }).then(() => true),
                    );
                    if (r) setV(v + 1);
                  }}
                >
                  <Trash2 className="size-4" aria-hidden />
                </Btn>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ChildView({ ov, sectionId }: { ov: Overview; sectionId: string }) {
  const [kids, setKids] = useState<{ studentId: string; name: string }[]>([]);
  const [studentId, setStudentId] = useState("");
  const [story, setStory] = useState<Story | null>(null);
  const [pickups, setPickups] = useState<Pickup[]>([]);
  const [p, setP] = useState({ name: "", relation: "", phone: "" });
  const [photo, setPhoto] = useState<File | null>(null);
  const { busy, run } = useAction();
  useEffect(() => {
    const t = setTimeout(async () => {
      const d = await sb<DiaryRow[]>(`early-years/diary?sectionId=${sectionId}`);
      setKids(d.map((x) => ({ studentId: x.studentId, name: x.name })));
      setStudentId(d[0]?.studentId ?? "");
    }, 0);
    return () => clearTimeout(t);
  }, [sectionId]);
  useEffect(() => {
    if (!studentId) return;
    const t = setTimeout(async () => {
      setStory(
        ov.stories
          ? await sb<Story>(`early-years/story?studentId=${studentId}`).catch(() => null)
          : null,
      );
      setPickups(
        ov.pickup
          ? await sb<Pickup[]>(`early-years/pickups?studentId=${studentId}`).catch(() => [])
          : [],
      );
    }, 0);
    return () => clearTimeout(t);
  }, [studentId, ov.stories, ov.pickup]);
  return (
    <div className="space-y-4">
      <select
        aria-label="Child"
        className={`${inputCls} w-auto! print:hidden`}
        value={studentId}
        onChange={(e) => setStudentId(e.target.value)}
      >
        {kids.map((k) => (
          <option key={k.studentId} value={k.studentId}>
            {k.name}
          </option>
        ))}
      </select>
      {!ov.stories && !ov.pickup && (
        <Empty>Learning stories and pickup authorisation are part of ShikshaBlox Standard.</Empty>
      )}
      {story && (
        <Card className="space-y-3">
          <div className="flex items-start justify-between gap-2">
            <div>
              <p className="text-ink-muted text-xs">{story.school}</p>
              <h2 className="text-xl font-bold">{story.child}&apos;s learning story</h2>
              <p className="text-ink-muted text-sm">
                {story.classLabel} · {day(story.from)} – {day(story.to)}
              </p>
            </div>
            <Btn size="sm" onClick={() => window.print()} className="print:hidden">
              <Printer className="size-4" aria-hidden /> Print
            </Btn>
          </div>
          {story.observations.map((o) => (
            <div key={o.id} className="flex gap-3">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              {o.photoUrl && (
                <img
                  src={o.photoUrl}
                  alt={`Moment from ${day(o.observedOn)}`}
                  className="size-24 shrink-0 rounded-lg object-cover"
                />
              )}
              <p className="text-sm">
                <span className="font-semibold">
                  {day(o.observedOn)}
                  {o.domain ? ` · ${o.domain}` : ""}:{" "}
                </span>
                {o.note}
              </p>
            </div>
          ))}
          {Object.keys(story.achieved).length > 0 && (
            <div>
              <h3 className="font-semibold">What {story.child.split(" ")[0]} can do now</h3>
              <ul className="list-disc pl-5 text-sm">
                {Object.entries(story.achieved).flatMap(([d, list]) =>
                  list.map((s) => <li key={d + s}>{s}</li>),
                )}
              </ul>
            </div>
          )}
          {Object.keys(story.emerging).length > 0 && (
            <div>
              <h3 className="font-semibold">Growing towards</h3>
              <ul className="list-disc pl-5 text-sm">
                {Object.entries(story.emerging).flatMap(([d, list]) =>
                  list.map((s) => <li key={d + s}>{s}</li>),
                )}
              </ul>
            </div>
          )}
        </Card>
      )}
      {ov.pickup && (
        <Card className="space-y-3 print:hidden">
          <h2 className="font-semibold">Adults allowed to collect</h2>
          {pickups.length === 0 && (
            <p className="text-ink-muted text-sm">Only the guardians on record.</p>
          )}
          <ul className="grid gap-2 sm:grid-cols-2">
            {pickups.map((x) => (
              <li key={x.id} className="flex items-center gap-3 rounded-xl bg-black/[0.03] p-2">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                {x.photoUrl ? (
                  <img src={x.photoUrl} alt={x.name} className="size-14 rounded-lg object-cover" />
                ) : (
                  <span className="grid size-14 place-items-center rounded-lg bg-black/10 text-xs">
                    No photo
                  </span>
                )}
                <span className="flex-1 text-sm">
                  <span className="block font-medium">{x.name}</span>
                  {x.relation}
                  {x.phone ? ` · ${x.phone}` : ""}
                </span>
                <Btn
                  size="sm"
                  variant="ghost"
                  aria-label={`Remove ${x.name}`}
                  onClick={async () => {
                    const r = await run(x.id, () =>
                      sb<Pickup[]>(`early-years/pickups/${x.id}`, { method: "DELETE" }),
                    );
                    if (r) setPickups(r);
                  }}
                >
                  <Trash2 className="size-4" aria-hidden />
                </Btn>
              </li>
            ))}
          </ul>
          <div className="grid gap-2 sm:grid-cols-4 sm:items-end">
            <Field label="Name">
              <input
                className={inputCls}
                value={p.name}
                maxLength={120}
                onChange={(e) => setP({ ...p, name: e.target.value })}
              />
            </Field>
            <Field label="Relation">
              <input
                className={inputCls}
                value={p.relation}
                maxLength={60}
                onChange={(e) => setP({ ...p, relation: e.target.value })}
              />
            </Field>
            <Field label="Phone">
              <input
                className={inputCls}
                value={p.phone}
                maxLength={20}
                onChange={(e) => setP({ ...p, phone: e.target.value })}
              />
            </Field>
            <Field label="Photo">
              <input
                type="file"
                accept="image/jpeg,image/png"
                className="text-sm"
                onChange={(e) => setPhoto(e.target.files?.[0] ?? null)}
              />
            </Field>
          </div>
          <Btn
            variant="primary"
            busy={busy === "pick"}
            disabled={!p.name.trim()}
            onClick={async () => {
              const fd = new FormData();
              fd.append("studentId", studentId);
              fd.append("name", p.name);
              if (p.relation) fd.append("relation", p.relation);
              if (p.phone) fd.append("phone", p.phone);
              if (photo) fd.append("photo", photo);
              const r = await run(
                "pick",
                () => sb<Pickup[]>("early-years/pickups", { form: fd }),
                "Added.",
              );
              if (r) {
                setPickups(r);
                setP({ name: "", relation: "", phone: "" });
                setPhoto(null);
              }
            }}
          >
            Add adult
          </Btn>
        </Card>
      )}
    </div>
  );
}
