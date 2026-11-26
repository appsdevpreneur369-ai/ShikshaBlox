"use client";

import { useCallback, useEffect, useState } from "react";
import { ArrowDown, ArrowUp, Download, Plus, Trash2 } from "lucide-react";
import { sb } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Field, Loading, inputCls, useAction } from "@/components/kit";

type QType = "RATING" | "CHOICE" | "MULTI" | "TEXT";
type Question = { id?: string; type: QType; label: string; options: string[]; required: boolean };
type Survey = {
  id: string;
  slug: string;
  title: string;
  description?: string | null;
  questions: Question[];
  open: boolean;
  anonymous: boolean;
  closesOn?: string | null;
  responses: number;
  createdAt: string;
};
type QResult = {
  id: string;
  type: QType;
  label: string;
  answered: number;
  average?: number | null;
  counts?: Record<string, number> | null;
  texts?: string[] | null;
};
type Results = { survey: Survey; questions: QResult[] };

const TYPE_LABEL: Record<QType, string> = {
  RATING: "Rating 1–5",
  CHOICE: "One choice",
  MULTI: "Several choices",
  TEXT: "Text answer",
};

export function SurveysView({ siteUrl, available }: { siteUrl?: string; available: boolean }) {
  const [list, setList] = useState<Survey[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [editing, setEditing] = useState<Survey | "new" | null>(null);
  const [results, setResults] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setList(await sb<Survey[]>("engagement/surveys"));
    } catch (e) {
      setErr((e as Error).message);
    }
  }, []);
  useEffect(() => {
    if (!available) return;
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load, available]);

  if (!available)
    return (
      <Empty>
        Surveys are part of ShikshaBlox Standard: collect parent feedback after events, PTMs or the
        school year.
      </Empty>
    );
  if (err) return <Empty>{err}</Empty>;
  if (results)
    return <ResultsView id={results} siteUrl={siteUrl} onBack={() => setResults(null)} />;
  if (editing)
    return (
      <SurveyEditor
        survey={editing === "new" ? null : editing}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null);
          void load();
        }}
      />
    );

  return (
    <div>
      <Btn variant="primary" className="mb-4" onClick={() => setEditing("new")}>
        <Plus className="size-4" aria-hidden /> New survey
      </Btn>
      {!list ? (
        <Loading />
      ) : list.length === 0 ? (
        <Empty>No surveys yet. Create a short feedback form and share its link with parents.</Empty>
      ) : (
        <ul className="overflow-hidden rounded-2xl bg-white ring-1 ring-black/5">
          {list.map((s) => (
            <li
              key={s.id}
              className="flex flex-wrap items-center gap-x-3 gap-y-2 border-b border-black/5 px-4 py-3 last:border-0"
            >
              <div className="min-w-[12rem] flex-1">
                <p className="text-sm font-semibold">{s.title}</p>
                <p className="text-ink-muted text-xs">
                  {s.questions.length} questions · {s.responses} responses
                  {s.anonymous ? " · anonymous" : ""}
                  {siteUrl && s.open
                    ? ` · ${siteUrl.replace(/^https?:\/\//, "")}/survey/${s.slug}`
                    : ""}
                </p>
              </div>
              {s.open ? <Badge tone="green">Open</Badge> : <Badge>Closed</Badge>}
              <Btn size="sm" onClick={() => setResults(s.id)}>
                Results
              </Btn>
              <Btn size="sm" variant="ghost" onClick={() => setEditing(s)}>
                Edit
              </Btn>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function SurveyEditor({
  survey,
  onClose,
  onSaved,
}: {
  survey: Survey | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const locked = (survey?.responses ?? 0) > 0;
  const [title, setTitle] = useState(survey?.title ?? "");
  const [slug, setSlug] = useState(survey?.slug ?? "");
  const [description, setDescription] = useState(survey?.description ?? "");
  const [anonymous, setAnonymous] = useState(survey?.anonymous ?? true);
  const [open, setOpen] = useState(survey?.open ?? true);
  const [closesOn, setClosesOn] = useState(survey?.closesOn ?? "");
  const [qs, setQs] = useState<Question[]>(
    survey?.questions ?? [
      { type: "RATING", label: "Overall, how satisfied are you?", options: [], required: true },
    ],
  );
  const { busy, run } = useAction();

  const setQ = (i: number, p: Partial<Question>) =>
    setQs(qs.map((q, j) => (i === j ? { ...q, ...p } : q)));
  const move = (i: number, d: number) => {
    const n = [...qs];
    const [x] = n.splice(i, 1);
    n.splice(i + d, 0, x!);
    setQs(n);
  };

  async function save() {
    const body = {
      title,
      slug: survey ? undefined : slug || null,
      description,
      questions: locked
        ? undefined
        : qs.map((q) => ({
            ...q,
            options:
              q.type === "CHOICE" || q.type === "MULTI" ? q.options.filter((o) => o.trim()) : [],
          })),
      open,
      anonymous,
      closesOn: closesOn || null,
    };
    const r = await run(
      "save",
      () =>
        survey
          ? sb(`engagement/surveys/${survey.id}`, { method: "PATCH", body })
          : sb("engagement/surveys", { body }),
      survey ? "Survey saved" : "Survey created",
    );
    if (r) onSaved();
  }
  async function remove() {
    if (!survey || !confirm(`Delete “${survey.title}” and its ${survey.responses} responses?`))
      return;
    if (
      await run(
        "del",
        () => sb(`engagement/surveys/${survey.id}`, { method: "DELETE" }).then(() => true),
        "Deleted",
      )
    )
      onSaved();
  }

  return (
    <Card>
      <h2 className="text-lg font-semibold">{survey ? "Edit survey" : "New survey"}</h2>
      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <Field label="Title *">
          <input
            className={inputCls}
            maxLength={160}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </Field>
        {!survey && (
          <Field
            label="Link (optional)"
            hint="Lowercase letters, digits and dashes; made from the title if empty"
          >
            <input
              className={inputCls}
              maxLength={60}
              value={slug}
              onChange={(e) => setSlug(e.target.value.toLowerCase())}
              placeholder="annual-day-feedback"
            />
          </Field>
        )}
        <div className="sm:col-span-2">
          <Field label="Introduction">
            <textarea
              className={inputCls}
              rows={2}
              maxLength={2000}
              value={description ?? ""}
              onChange={(e) => setDescription(e.target.value)}
            />
          </Field>
        </div>
        <Field label="Closes on (optional)">
          <input
            className={inputCls}
            type="date"
            value={closesOn ?? ""}
            onChange={(e) => setClosesOn(e.target.value)}
          />
        </Field>
        <div className="flex flex-wrap items-end gap-4 pb-2 text-sm">
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              className="size-4"
              checked={open}
              onChange={(e) => setOpen(e.target.checked)}
            />{" "}
            Open for answers
          </label>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              className="size-4"
              checked={anonymous}
              onChange={(e) => setAnonymous(e.target.checked)}
            />{" "}
            Anonymous
          </label>
        </div>
      </div>
      <h3 className="mt-6 mb-2 text-sm font-semibold">Questions</h3>
      {locked && (
        <p className="mb-3 rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900">
          This survey has responses, so its questions can no longer change.
        </p>
      )}
      <ol className="space-y-3">
        {qs.map((q, i) => (
          <li key={i} className="rounded-xl p-3 ring-1 ring-black/10">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-ink-muted w-6 text-sm">{i + 1}.</span>
              <input
                className={`${inputCls} min-w-[12rem] flex-1`}
                maxLength={300}
                disabled={locked}
                value={q.label}
                onChange={(e) => setQ(i, { label: e.target.value })}
                aria-label={`Question ${i + 1}`}
              />
              <select
                className={`${inputCls} w-auto`}
                disabled={locked}
                value={q.type}
                onChange={(e) =>
                  setQ(i, {
                    type: e.target.value as QType,
                    options: q.options.length ? q.options : ["", ""],
                  })
                }
                aria-label="Answer type"
              >
                {(Object.keys(TYPE_LABEL) as QType[]).map((t) => (
                  <option key={t} value={t}>
                    {TYPE_LABEL[t]}
                  </option>
                ))}
              </select>
              <label className="flex items-center gap-1.5 text-sm">
                <input
                  type="checkbox"
                  className="size-4"
                  disabled={locked}
                  checked={q.required}
                  onChange={(e) => setQ(i, { required: e.target.checked })}
                />{" "}
                Required
              </label>
              {!locked && (
                <span className="flex gap-0.5">
                  <Btn
                    size="sm"
                    variant="ghost"
                    disabled={i === 0}
                    onClick={() => move(i, -1)}
                    aria-label="Move up"
                  >
                    <ArrowUp className="size-3.5" aria-hidden />
                  </Btn>
                  <Btn
                    size="sm"
                    variant="ghost"
                    disabled={i === qs.length - 1}
                    onClick={() => move(i, 1)}
                    aria-label="Move down"
                  >
                    <ArrowDown className="size-3.5" aria-hidden />
                  </Btn>
                  <Btn
                    size="sm"
                    variant="ghost"
                    onClick={() => setQs(qs.filter((_, j) => j !== i))}
                    aria-label="Remove question"
                  >
                    <Trash2 className="size-3.5" aria-hidden />
                  </Btn>
                </span>
              )}
            </div>
            {(q.type === "CHOICE" || q.type === "MULTI") && (
              <div className="mt-2 ml-8 space-y-1.5">
                {q.options.map((o, k) => (
                  <input
                    key={k}
                    className={inputCls}
                    maxLength={100}
                    disabled={locked}
                    value={o}
                    placeholder={`Option ${k + 1}`}
                    onChange={(e) =>
                      setQ(i, { options: q.options.map((x, j) => (j === k ? e.target.value : x)) })
                    }
                    aria-label={`Option ${k + 1}`}
                  />
                ))}
                {!locked && q.options.length < 12 && (
                  <Btn
                    size="sm"
                    variant="ghost"
                    onClick={() => setQ(i, { options: [...q.options, ""] })}
                  >
                    <Plus className="size-3.5" aria-hidden /> Option
                  </Btn>
                )}
              </div>
            )}
          </li>
        ))}
      </ol>
      {!locked && qs.length < 30 && (
        <Btn
          size="sm"
          className="mt-3"
          onClick={() => setQs([...qs, { type: "TEXT", label: "", options: [], required: false }])}
        >
          <Plus className="size-4" aria-hidden /> Add question
        </Btn>
      )}
      <div className="mt-6 flex flex-wrap gap-2">
        <Btn variant="primary" busy={busy === "save"} onClick={() => void save()}>
          {survey ? "Save" : "Create survey"}
        </Btn>
        <Btn variant="ghost" onClick={onClose}>
          Cancel
        </Btn>
        {survey && (
          <Btn
            variant="ghost"
            className="ml-auto"
            busy={busy === "del"}
            onClick={() => void remove()}
          >
            <Trash2 className="size-4" aria-hidden /> Delete
          </Btn>
        )}
      </div>
    </Card>
  );
}

function ResultsView({
  id,
  siteUrl,
  onBack,
}: {
  id: string;
  siteUrl?: string;
  onBack: () => void;
}) {
  const [r, setR] = useState<Results | null>(null);
  useEffect(() => {
    void sb<Results>(`engagement/surveys/${id}/results`).then(setR);
  }, [id]);
  async function csv() {
    const res = await fetch(`/api/backend/engagement/surveys/${id}/responses.csv`, {
      cache: "no-store",
    });
    if (!res.ok) return;
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = `${r?.survey.slug ?? "survey"}-responses.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
  if (!r) return <Loading />;
  const link = siteUrl ? `${siteUrl}/survey/${r.survey.slug}` : null;
  return (
    <div>
      <button type="button" className="text-primary mb-3 text-sm font-medium" onClick={onBack}>
        ← All surveys
      </button>
      <Card className="mb-5">
        <div className="flex flex-wrap items-start gap-3">
          <div className="min-w-0 flex-1">
            <h2 className="text-lg font-semibold">{r.survey.title}</h2>
            <p className="text-ink-muted text-sm">{r.survey.responses} responses</p>
            {link && r.survey.open && (
              <a
                className="text-primary text-sm font-medium underline"
                href={link}
                target="_blank"
                rel="noopener noreferrer"
              >
                {link.replace(/^https?:\/\//, "")}
              </a>
            )}
          </div>
          <Btn onClick={() => void csv()} disabled={r.survey.responses === 0}>
            <Download className="size-4" aria-hidden /> CSV
          </Btn>
        </div>
      </Card>
      <div className="grid gap-5 lg:grid-cols-2">
        {r.questions.map((q, i) => {
          const max = Math.max(1, ...Object.values(q.counts ?? {}));
          return (
            <Card key={q.id}>
              <p className="text-sm font-semibold">
                {i + 1}. {q.label}
              </p>
              <p className="text-ink-muted mb-3 text-xs">{q.answered} answered</p>
              {q.type === "RATING" && q.average != null && (
                <p className="mb-3 text-3xl font-bold">
                  {q.average} <span className="text-ink-muted text-base font-normal">/ 5</span>
                </p>
              )}
              {q.counts && (
                <ul className="space-y-1.5">
                  {Object.entries(q.counts).map(([k, n]) => (
                    <li
                      key={k}
                      className="grid grid-cols-[minmax(4rem,8rem)_1fr_2.5rem] items-center gap-2 text-sm"
                    >
                      <span className="truncate">{q.type === "RATING" ? `${k} ★` : k}</span>
                      <span className="h-2.5 rounded-full bg-black/5">
                        <span
                          className="bg-primary block h-full rounded-full"
                          style={{ width: `${(n / max) * 100}%` }}
                        />
                      </span>
                      <span className="text-right tabular-nums">{n}</span>
                    </li>
                  ))}
                </ul>
              )}
              {q.texts &&
                (q.texts.length === 0 ? (
                  <p className="text-ink-muted text-sm">No answers yet.</p>
                ) : (
                  <ul className="max-h-72 space-y-2 overflow-y-auto text-sm">
                    {q.texts.map((t, k) => (
                      <li
                        key={k}
                        className="rounded-lg bg-black/[0.03] px-3 py-2 whitespace-pre-line"
                      >
                        {t}
                      </li>
                    ))}
                  </ul>
                ))}
            </Card>
          );
        })}
      </div>
    </div>
  );
}
