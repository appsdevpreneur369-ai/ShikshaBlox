"use client";

import { useEffect, useState } from "react";
import { sb } from "@/lib/studio";
import { Btn, Card, Field, Loading, PageHeader, inputCls, useAction } from "@/components/kit";
import { MediaField } from "@/components/media-picker";

type Brand = { primary?: string; accent?: string; fontHeading?: string; fontBody?: string; radius?: string };
type Profile = Record<string, unknown> & {
  address?: Record<string, string>;
  social?: Record<string, string>;
};
type SiteView = {
  theme: string;
  brand: Brand;
  profile: Profile;
  logoMediaId?: string;
  themes: string[];
  fonts: string[];
  radii: string[];
};

const THEME_INFO: Record<string, string> = {
  modern: "Clean and contemporary",
  classic: "Traditional and formal",
  playful: "Bright and friendly for younger children",
};
const PRESETS = [
  { name: "Teal & rose", primary: "#0f766e", accent: "#db2777" },
  { name: "Royal blue", primary: "#1e3a8a", accent: "#f59e0b" },
  { name: "Maroon", primary: "#7f1d1d", accent: "#eab308" },
  { name: "Forest", primary: "#166534", accent: "#f97316" },
  { name: "Indigo", primary: "#4338ca", accent: "#06b6d4" },
  { name: "Charcoal", primary: "#1f2937", accent: "#ef4444" },
];

export default function DesignPage() {
  const [site, setSite] = useState<SiteView | null>(null);
  const [theme, setTheme] = useState("modern");
  const [brand, setBrand] = useState<Brand>({});
  const [logo, setLogo] = useState<string | undefined>();
  const [profile, setProfile] = useState<Profile>({});
  const { busy, run } = useAction();

  useEffect(() => {
    void sb<SiteView>("site").then((s) => {
      setSite(s);
      setTheme(s.theme);
      setBrand(s.brand);
      setLogo(s.logoMediaId);
      setProfile(s.profile);
    });
  }, []);

  if (!site) return <Loading />;

  const p = (k: string) => (profile[k] as string) ?? "";
  const setP = (k: string, v: unknown) => setProfile((x) => ({ ...x, [k]: v }));
  const addr = (k: string) => profile.address?.[k] ?? "";
  const setAddr = (k: string, v: string) => setProfile((x) => ({ ...x, address: { ...x.address, [k]: v } }));
  const soc = (k: string) => profile.social?.[k] ?? "";
  const setSoc = (k: string, v: string) => setProfile((x) => ({ ...x, social: { ...x.social, [k]: v } }));

  return (
    <div className="space-y-6">
      <PageHeader title="Design & profile" subtitle="Your school's look and details. Changes go live on your website when you save." />

      <Card>
        <h2 className="mb-4 font-semibold">Theme</h2>
        <div className="grid gap-3 sm:grid-cols-3">
          {site.themes
            .slice()
            .sort()
            .map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setTheme(t)}
                className={`rounded-2xl p-4 text-left ring-2 transition ${theme === t ? "ring-primary bg-primary/5" : "ring-black/10 hover:ring-black/20"}`}
              >
                <span className="block font-semibold capitalize">{t}</span>
                <span className="text-ink-muted text-xs">{THEME_INFO[t]}</span>
              </button>
            ))}
        </div>

        <h2 className="mt-6 mb-3 font-semibold">Colours</h2>
        <div className="mb-4 flex flex-wrap gap-2">
          {PRESETS.map((c) => (
            <button
              key={c.name}
              type="button"
              onClick={() => setBrand((b) => ({ ...b, primary: c.primary, accent: c.accent }))}
              className="flex items-center gap-2 rounded-full bg-white py-1 pr-3 pl-1 text-xs font-medium ring-1 ring-black/10 hover:ring-black/25"
            >
              <span className="flex">
                <span className="size-6 rounded-full" style={{ background: c.primary }} />
                <span className="-ml-2 size-6 rounded-full ring-2 ring-white" style={{ background: c.accent }} />
              </span>
              {c.name}
            </button>
          ))}
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Main colour">
            <span className="flex items-center gap-2">
              <input type="color" value={brand.primary ?? "#1e3a8a"} onChange={(e) => setBrand({ ...brand, primary: e.target.value })} className="h-10 w-12 cursor-pointer rounded-lg border border-black/10" />
              <input className={inputCls} value={brand.primary ?? ""} onChange={(e) => setBrand({ ...brand, primary: e.target.value })} />
            </span>
          </Field>
          <Field label="Accent colour">
            <span className="flex items-center gap-2">
              <input type="color" value={brand.accent ?? "#f59e0b"} onChange={(e) => setBrand({ ...brand, accent: e.target.value })} className="h-10 w-12 cursor-pointer rounded-lg border border-black/10" />
              <input className={inputCls} value={brand.accent ?? ""} onChange={(e) => setBrand({ ...brand, accent: e.target.value })} />
            </span>
          </Field>
          <Field label="Heading font">
            <select className={inputCls} value={brand.fontHeading} onChange={(e) => setBrand({ ...brand, fontHeading: e.target.value })}>
              {site.fonts.slice().sort().map((f) => (
                <option key={f}>{f}</option>
              ))}
            </select>
          </Field>
          <Field label="Body font">
            <select className={inputCls} value={brand.fontBody} onChange={(e) => setBrand({ ...brand, fontBody: e.target.value })}>
              {site.fonts.slice().sort().map((f) => (
                <option key={f}>{f}</option>
              ))}
            </select>
          </Field>
          <Field label="Corners">
            <select className={inputCls} value={brand.radius} onChange={(e) => setBrand({ ...brand, radius: e.target.value })}>
              {site.radii.map((r) => (
                <option key={r} value={r}>
                  {{ soft: "Slightly rounded", round: "Rounded", pill: "Very rounded" }[r] ?? r}
                </option>
              ))}
            </select>
          </Field>
          <div className="sm:col-span-2 lg:col-span-3">
            <Field label="Logo">
              <MediaField value={logo} onChange={setLogo} />
            </Field>
          </div>
        </div>
        <div className="mt-6 flex justify-end">
          <Btn variant="primary" busy={busy === "design"} onClick={() => run("design", () => sb("site/design", { method: "PUT", body: { theme, brand, logoMediaId: logo ?? null } }), "Design saved — website updated")}>
            Save design
          </Btn>
        </div>
      </Card>

      <Card>
        <h2 className="mb-4 font-semibold">School details</h2>
        <div className="grid gap-4 md:grid-cols-2">
          <Field label="Tagline">
            <input className={inputCls} value={p("tagline")} onChange={(e) => setP("tagline", e.target.value)} />
          </Field>
          <Field label="Established (year)">
            <input className={inputCls} value={p("established")} onChange={(e) => setP("established", e.target.value)} />
          </Field>
          <Field label="Board">
            <input className={inputCls} value={p("board")} onChange={(e) => setP("board", e.target.value)} />
          </Field>
          <Field label="Recognition / affiliation line">
            <input className={inputCls} value={p("boardName")} onChange={(e) => setP("boardName", e.target.value)} />
          </Field>
          <Field label="Phone">
            <input className={inputCls} value={p("phone")} onChange={(e) => setP("phone", e.target.value)} />
          </Field>
          <Field label="Email">
            <input type="email" className={inputCls} value={p("email")} onChange={(e) => setP("email", e.target.value)} />
          </Field>
          <Field label="Office hours">
            <input className={inputCls} value={p("officeHours")} onChange={(e) => setP("officeHours", e.target.value)} />
          </Field>
          <label className="mt-7 flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" className="accent-primary size-4" checked={profile.admissionsOpen !== false} onChange={(e) => setP("admissionsOpen", e.target.checked)} />
            Admissions are open (shows “Apply now” in the header)
          </label>
        </div>
        <h3 className="mt-6 mb-3 text-sm font-semibold">Address</h3>
        <div className="grid gap-4 md:grid-cols-3">
          <div className="md:col-span-2">
            <Field label="Street">
              <input className={inputCls} value={addr("line1")} onChange={(e) => setAddr("line1", e.target.value)} />
            </Field>
          </div>
          <Field label="City">
            <input className={inputCls} value={addr("city")} onChange={(e) => setAddr("city", e.target.value)} />
          </Field>
          <Field label="PIN code">
            <input className={inputCls} value={addr("pincode")} onChange={(e) => setAddr("pincode", e.target.value)} />
          </Field>
          <Field label="State">
            <input className={inputCls} value={addr("state")} onChange={(e) => setAddr("state", e.target.value)} />
          </Field>
          <Field label="Map search (optional)">
            <input className={inputCls} value={p("mapQuery")} onChange={(e) => setP("mapQuery", e.target.value)} />
          </Field>
        </div>
        <h3 className="mt-6 mb-3 text-sm font-semibold">Social media</h3>
        <div className="grid gap-4 md:grid-cols-3">
          {["facebook", "instagram", "youtube", "x", "linkedin"].map((k) => (
            <Field key={k} label={k === "x" ? "X (Twitter)" : k[0]!.toUpperCase() + k.slice(1)}>
              <input className={inputCls} placeholder="https://" value={soc(k)} onChange={(e) => setSoc(k, e.target.value)} />
            </Field>
          ))}
        </div>
        <div className="mt-6 flex justify-end">
          <Btn variant="primary" busy={busy === "profile"} onClick={() => run("profile", () => sb("site/profile", { method: "PUT", body: { profile } }), "Details saved — website updated")}>
            Save details
          </Btn>
        </div>
      </Card>
    </div>
  );
}
