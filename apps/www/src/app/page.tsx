import Image from "next/image";
import Link from "next/link";
import { Icon } from "@/components/Icon";
import { ErpPreview, RoadmapTimeline } from "@/components/visuals";
import { BrowserFrame, ButtonLink, CtaBand, FaqList, JsonLd, PhoneFrame, Photo, SectionHeading, StatusBadge } from "@/components/ui";
import { homeFaqs } from "@/data/faqs";
import { allModules, catalogueGroups, releases } from "@/data/modules";
import { plans } from "@/data/plans";
import { profiles } from "@/data/profiles";
import { featuredSchool } from "@/data/schools";
import { faqLd, pageMetadata, softwareLd } from "@/lib/seo";

export const metadata = pageMetadata({
  title: "ShikshaBlox: School ERP Software & School Website Platform in India",
  description:
    "Modern, modular School ERP for CBSE, ICSE, State Board and international schools. Start with a beautiful school website today; add admissions, attendance, fees, exams, parent app and WhatsApp as you grow.",
  path: "/",
});

const roles = [
  {
    role: "Principal & management",
    img: "/images/principal-tablet.webp",
    alt: "Principal reviewing school data on a tablet",
    points: ["Whole-school dashboard by 9:30 am", "Admissions funnel and fee collections live", "Approve notices from your phone"],
  },
  {
    role: "Office & accounts",
    img: "/images/office-accounts.webp",
    alt: "School accountant working on a laptop",
    points: ["Receipts and reminders sent automatically", "Certificates and TCs in seconds", "Website updates without a developer"],
  },
  {
    role: "Teachers",
    img: "/images/teacher-blackboard.webp",
    alt: "Teacher teaching at a blackboard",
    points: ["Attendance in 30 seconds on a phone", "Marks entry that builds the report card", "Homework and notices to the whole class"],
  },
  {
    role: "Parents",
    img: "/images/parent-phone.webp",
    alt: "Parent reading a school update on her phone",
    points: ["Updates on WhatsApp, the app they already use", "Pay fees by UPI in two taps", "Attendance, homework and results in one place"],
  },
];

const comparison = [
  ["Fit for your size", "Built for one segment: too heavy or too shallow", "Small, Medium and International profiles on one platform"],
  ["Website", "Separate, dated static template", "Built-in CMS, synced with notices, events and admissions"],
  ["Parent communication", "One-way SMS blasts, low app adoption", "WhatsApp-first, two-way, with acknowledgements"],
  ["Report cards", "Hard-coded formats, custom scripts per board", "Versioned curriculum packs: CBSE, ICSE, State, IB"],
  ["Onboarding", "Weeks of vendor-led setup", "Setup wizard + assisted import; small schools live in a day"],
  ["Pricing", "Opaque quotes and hidden add-ons", "Published prices, pay only for active modules"],
];

export default function Home() {
  const moriah = featuredSchool;
  const liveCount = allModules.filter((m) => releases[m.release].live || m.partial).length;
  return (
    <>
      <JsonLd
        data={[
          softwareLd([
            { name: "Web Free", price: 0 },
            { name: "Web Pro", price: 7999 },
            { name: "Lite", price: 19999 },
            { name: "Standard", price: 59999 },
            { name: "Global", price: 249999 },
          ]),
          faqLd(homeFaqs),
        ]}
      />

      {/* Hero */}
      <section className="bg-mesh relative overflow-hidden">
        <div className="bg-grid pointer-events-none absolute inset-0 opacity-70" aria-hidden="true" />
        <div className="container-x relative grid items-center gap-12 pt-12 pb-16 sm:pt-16 lg:grid-cols-[1fr_1.05fr] lg:gap-10 lg:pt-20 lg:pb-24">
          <div>
            <Link prefetch={false}
              href="/roadmap"
              className="inline-flex max-w-full items-center gap-2 rounded-full border border-line bg-card py-1.5 pr-3 pl-1.5 text-sm text-ink-2 shadow-card transition-colors hover:border-brand"
            >
              <span className="rounded-full bg-live-soft px-2.5 py-0.5 text-xs font-semibold text-live">Live</span>
              <span className="truncate">School Website &amp; CMS · ERP modules from 2027</span>
              <Icon name="arrow" className="size-4 shrink-0" />
            </Link>
            <h1 className="mt-6 font-display text-[2.6rem] leading-[1.05] font-extrabold tracking-tight text-balance text-ink sm:text-6xl lg:text-[4.1rem]">
              The School ERP that{" "}
              <span className="bg-gradient-to-r from-[#4338ca] via-[#6d28d9] to-[#c2410c] bg-clip-text text-transparent dark:from-[#a5b4fc] dark:via-[#c4b5fd] dark:to-[#fcd34d]">
                grows with your school
              </span>
            </h1>
            <p className="mt-6 max-w-xl text-lg leading-relaxed text-pretty text-ink-2 sm:text-xl">
              Start with a beautiful school website today. Snap on admissions, attendance, fees, exams, a parent app and WhatsApp
              as you grow. One platform, one login, no migrations.
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <ButtonLink href="/contact" arrow>
                Book a free demo
              </ButtonLink>
              <ButtonLink href={moriah.siteUrl} variant="secondary" external>
                See a live school website <Icon name="external" className="size-4" />
              </ButtonLink>
            </div>
            <ul className="mt-8 flex flex-wrap gap-x-5 gap-y-2 text-sm text-ink-3">
              {["CBSE", "ICSE / ISC", "State Boards", "IB · Cambridge · AP"].map((b) => (
                <li key={b} className="inline-flex items-center gap-1.5">
                  <Icon name="check" className="size-4 text-live" strokeWidth={2.4} />
                  {b}
                </li>
              ))}
            </ul>
          </div>

          <div className="relative mx-auto w-full max-w-xl lg:max-w-none">
            <BrowserFrame
              src="/product/moriah-desktop.webp"
              alt="Moriah School's website, built and managed on ShikshaBlox"
              url="moriah school website · powered by ShikshaBlox"
              sizes="(min-width: 1024px) 600px, (min-width: 640px) 576px, 92vw"
              priority
            />
            <PhoneFrame
              src="/product/moriah-mobile.webp"
              alt="Moriah School's website on a mobile phone"
              className="absolute -bottom-10 -left-3 w-[30%] max-w-[180px] sm:-left-8"
            />
            <div className="float-slow absolute -top-5 -right-2 hidden w-60 rounded-2xl border border-line bg-card p-3.5 shadow-pop sm:block lg:-right-6">
              <div className="flex items-center gap-3">
                <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-live-soft text-live">
                  <Icon name="funnel" className="size-5" />
                </span>
                <div>
                  <p className="text-sm font-semibold text-ink">New admission enquiry</p>
                  <p className="text-xs text-ink-3">From the website · Class 3</p>
                </div>
              </div>
            </div>
            <div className="absolute right-3 -bottom-6 hidden rounded-2xl border border-line bg-card px-4 py-3 shadow-pop sm:block">
              <p className="text-xs text-ink-3">Notice published</p>
              <p className="text-sm font-semibold text-ink">Annual Day: 20 Dec ✓</p>
            </div>
          </div>
        </div>
      </section>

      {/* Product facts */}
      <section aria-label="ShikshaBlox at a glance" className="border-y border-line bg-bg-soft">
        <dl className="container-x grid grid-cols-2 gap-6 py-10 lg:grid-cols-4">
          {[
            [`${allModules.length}+`, "modules on one platform"],
            ["14", "website pages ready on day one"],
            ["3", "school profiles: small, medium, international"],
            ["0%", "platform fee on online fee payments"],
          ].map(([v, l]) => (
            <div key={l} className="text-center lg:text-left">
              <dt className="sr-only">{l}</dt>
              <dd>
                <span className="block font-display text-4xl font-extrabold tracking-tight text-brand sm:text-5xl">{v}</span>
                <span className="mt-1 block text-sm text-ink-2 sm:text-base">{l}</span>
              </dd>
            </div>
          ))}
        </dl>
      </section>

      {/* Composable blocks */}
      <section className="container-x py-20 sm:py-28">
        <SectionHeading
          eyebrow="Composable by design"
          title="Start with a website. Snap on the blocks you need."
          lead="ShikshaBlox is built like LEGO: every module is a block that switches on with a click, on the same data, for the same login. Pay only for what's active."
          center
        />
        <div className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {catalogueGroups.slice(0, 4).map((g) => (
            <div key={g.title} className="card card-hover reveal p-6">
              <h3 className="font-display text-lg font-bold text-ink">{g.title}</h3>
              <p className="mt-1 text-sm text-ink-3">{g.blurb}</p>
              <ul className="mt-5 space-y-3">
                {g.modules.slice(0, 4).map((m) => (
                  <li key={m.id} className="flex items-start gap-3">
                    <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-brand-soft text-brand">
                      <Icon name={m.icon} className="size-[1.1rem]" />
                    </span>
                    <span className="min-w-0">
                      <span className="block text-sm leading-snug font-semibold text-ink">{m.name}</span>
                      <StatusBadge release={m.release} partial={m.partial} className="mt-1" />
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        <div className="mt-10 flex flex-col items-center gap-3 text-center">
          <ButtonLink href="/features" variant="secondary" arrow>
            Explore all {allModules.length} modules
          </ButtonLink>
          <p className="text-sm text-ink-3">{liveCount} live or partly live today · the rest on a published roadmap</p>
        </div>
      </section>

      {/* A day at school */}
      <section className="bg-bg-soft py-20 sm:py-28">
        <div className="container-x">
          <SectionHeading
            eyebrow="A day at your school"
            title="Built for everyone who runs a school, and the parents who trust it"
            lead="Role-adaptive screens mean a teacher sees three buttons, not three hundred."
          />
          <div className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {roles.map((r) => (
              <article key={r.role} className="card card-hover reveal overflow-hidden">
                <div className="aspect-[4/3] overflow-hidden">
                  <Photo src={r.img} alt={r.alt} sizes="(min-width: 1024px) 280px, (min-width: 640px) 45vw, 92vw" className="h-full w-full" />
                </div>
                <div className="p-6">
                  <h3 className="font-display text-lg font-bold text-ink">{r.role}</h3>
                  <ul className="mt-3 space-y-2 text-sm text-ink-2">
                    {r.points.map((p) => (
                      <li key={p} className="flex gap-2">
                        <Icon name="check" className="mt-0.5 size-4 shrink-0 text-brand" strokeWidth={2.4} />
                        {p}
                      </li>
                    ))}
                  </ul>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* Product showcase */}
      <section className="container-x py-20 sm:py-28">
        <div className="grid items-center gap-12 lg:grid-cols-2">
          <div>
            <StatusBadge release="R1" />
            <SectionHeading
              title="A school website your office can run, live today"
              lead="Pick a theme, add your photos, publish. Notices, events, gallery, admissions enquiries and mandatory disclosures, all edited in ShikshaBlox Studio with approvals and one-click rollback."
            />
            <ul className="mt-8 grid gap-3 sm:grid-cols-2">
              {["14 ready-made pages", "24 ready content blocks", "Enquiry inbox with status", "Draft · review · publish", "Photo-consent checks", "Fast, SEO-ready pages"].map((f) => (
                <li key={f} className="flex items-center gap-2.5 text-ink-2">
                  <span className="grid size-6 shrink-0 place-items-center rounded-full bg-live-soft text-live">
                    <Icon name="check" className="size-3.5" strokeWidth={3} />
                  </span>
                  {f}
                </li>
              ))}
            </ul>
            <div className="mt-8">
              <ButtonLink href="/features/website-cms" arrow>
                Website & CMS features
              </ButtonLink>
            </div>
          </div>
          <BrowserFrame
            src="/product/moriah-gallery-desktop.webp"
            alt="Moriah School gallery page on ShikshaBlox"
            url="Gallery · Moriah School"
            sizes="(min-width: 1024px) 560px, 92vw"
            className="reveal"
          />
        </div>

        <div className="mt-24 grid items-center gap-12 lg:grid-cols-2">
          <div className="order-2 lg:order-1">
            <ErpPreview />
          </div>
          <div className="order-1 lg:order-2">
            <StatusBadge release="R2" />
            <SectionHeading
              title="The ERP that runs the rest of the day"
              lead="Students, attendance, fees, report cards, WhatsApp and a parent app arrive with ShikshaBlox Lite. Founding schools can pre-book today and shape what we build."
            />
            <div className="mt-8 flex flex-wrap gap-3">
              <ButtonLink href="/roadmap" variant="secondary" arrow>
                See the roadmap
              </ButtonLink>
              <ButtonLink href="/contact?plan=lite" variant="ghost">
                Become a pilot school
              </ButtonLink>
            </div>
          </div>
        </div>
      </section>

      {/* Profiles */}
      <section className="bg-night py-20 text-white sm:py-28">
        <div className="container-x">
          <div className="max-w-3xl">
            <p className="eyebrow !text-amber-300">One platform, every kind of school</p>
            <h2 className="mt-3 font-display text-3xl leading-tight font-extrabold tracking-tight text-balance sm:text-4xl lg:text-[2.75rem]">
              Simple for a 150-student school. Deep enough for an IB campus.
            </h2>
            <p className="mt-4 text-lg text-indigo-100">
              Choose a profile and ShikshaBlox switches on the right modules, at the right depth, in the right UI mode.
            </p>
          </div>
          <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {profiles.map((p) => (
              <Link prefetch={false}
                key={p.slug}
                href={`/solutions/${p.slug}`}
                className="group reveal overflow-hidden rounded-2xl border border-white/10 bg-night-2 transition-all duration-300 hover:-translate-y-1 hover:border-white/30"
              >
                <div className="aspect-[16/10] overflow-hidden">
                  <Photo
                    src={p.image.src}
                    alt={p.image.alt}
                    sizes="(min-width: 1024px) 280px, (min-width: 640px) 45vw, 92vw"
                    className="h-full w-full transition-transform duration-500 group-hover:scale-105"
                  />
                </div>
                <div className="p-6">
                  <p className="text-xs font-semibold tracking-wide text-amber-300 uppercase">{p.plan} plan</p>
                  <h3 className="mt-1 font-display text-xl font-bold">{p.name}</h3>
                  <p className="mt-1 text-sm text-indigo-200">{p.short}</p>
                  <span className="mt-4 inline-flex items-center gap-1.5 text-sm font-semibold text-white">
                    Explore <Icon name="arrow" className="size-4 transition-transform group-hover:translate-x-1" />
                  </span>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* Schools */}
      <section className="container-x py-20 sm:py-28">
        <SectionHeading eyebrow="Schools on ShikshaBlox" title="Trusted by schools that put children first" center />
        <article className="card reveal mt-12 grid overflow-hidden lg:grid-cols-[1fr_1.1fr]">
          <div className="flex flex-col justify-center p-7 sm:p-10">
            <Image src={moriah.logo.src} alt={`${moriah.name} logo`} width={moriah.logo.w} height={moriah.logo.h} className="h-14 w-auto self-start" />
            <h3 className="mt-6 font-display text-2xl font-extrabold text-ink">{moriah.name}</h3>
            <p className="mt-1 inline-flex items-center gap-1.5 text-ink-3">
              <Icon name="pin" className="size-4" /> {moriah.location} · on ShikshaBlox since {moriah.since}
            </p>
            <p className="mt-4 leading-relaxed text-ink-2">{moriah.summary}</p>
            <div className="mt-5 flex flex-wrap gap-2">
              {moriah.liveModules.map((m) => (
                <span key={m} className="rounded-full bg-brand-soft px-3 py-1 text-xs font-semibold text-brand">
                  {m}
                </span>
              ))}
            </div>
            <div className="mt-7 flex flex-wrap gap-3">
              <ButtonLink href={moriah.siteUrl} external>
                Visit the live website <Icon name="external" className="size-4" />
              </ButtonLink>
              <ButtonLink href="/schools" variant="ghost" arrow>
                Read the story
              </ButtonLink>
            </div>
          </div>
          <div className="relative bg-bg-tint p-6 sm:p-10">
            <BrowserFrame
              src="/product/moriah-about-desktop.webp"
              alt="Moriah School about page"
              url="About · Moriah School"
              sizes="(min-width: 1024px) 560px, 92vw"
            />
          </div>
        </article>
        <p className="mt-6 text-center text-sm text-ink-3">
          Your school could be next.{" "}
          <Link prefetch={false} href="/contact" className="font-semibold text-brand underline-offset-4 hover:underline">
            Join as a founding school
          </Link>
          .
        </p>
      </section>

      {/* Comparison */}
      <section className="bg-bg-soft py-20 sm:py-28">
        <div className="container-x">
          <SectionHeading eyebrow="Why ShikshaBlox" title="Not another legacy ERP with a fresh coat of paint" center />
          <div className="reveal mt-12 overflow-hidden rounded-2xl border border-line bg-card">
            <div className="hidden grid-cols-[1fr_1.2fr_1.2fr] border-b border-line bg-bg-soft text-sm font-semibold text-ink-3 md:grid">
              <div className="p-5">&nbsp;</div>
              <div className="p-5">Typical legacy ERP</div>
              <div className="p-5 text-brand">ShikshaBlox</div>
            </div>
            {comparison.map(([k, a, b]) => (
              <div key={k} className="grid border-b border-line last:border-0 md:grid-cols-[1fr_1.2fr_1.2fr]">
                <div className="p-5 pb-1 font-semibold text-ink md:pb-5">{k}</div>
                <div className="flex gap-2 px-5 py-1 text-ink-3 md:py-5">
                  <Icon name="close" className="mt-0.5 size-4 shrink-0 text-red-500" strokeWidth={2.4} />
                  {a}
                </div>
                <div className="flex gap-2 px-5 pt-1 pb-5 text-ink md:pt-5">
                  <Icon name="check" className="mt-0.5 size-4 shrink-0 text-live" strokeWidth={2.6} />
                  {b}
                </div>
              </div>
            ))}
          </div>
          <div className="mt-8 text-center">
            <ButtonLink href="/why-shikshablox" variant="secondary" arrow>
              Why schools switch
            </ButtonLink>
          </div>
        </div>
      </section>

      {/* Pricing teaser */}
      <section className="container-x py-20 sm:py-28">
        <SectionHeading
          eyebrow="Transparent pricing"
          title="Published prices. Pay only for what's live."
          lead="Prices in INR, excluding GST. ERP plans are pre-booked: you pay only when your modules go live."
          center
        />
        <div className="mt-12 grid gap-5 md:grid-cols-3">
          {plans.slice(0, 3).map((p) => (
            <div key={p.id} className={`card card-hover reveal flex flex-col p-7 ${p.highlight ? "ring-2 ring-brand" : ""}`}>
              <div className="flex items-center justify-between gap-2">
                <h3 className="font-display text-xl font-bold text-ink">{p.name}</h3>
                <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${p.status === "live" ? "bg-live-soft text-live" : "bg-accent-soft text-accent-ink"}`}>
                  {p.statusNote}
                </span>
              </div>
              <p className="mt-1 text-sm text-ink-3">{p.for}</p>
              <p className="mt-5">
                <span className="font-display text-4xl font-extrabold text-ink">{p.price}</span>{" "}
                <span className="text-ink-3">{p.period}</span>
              </p>
              {p.priceNote && <p className="mt-1 text-sm text-ink-3">{p.priceNote}</p>}
              <ul className="mt-6 flex-1 space-y-2 text-sm text-ink-2">
                {p.features.slice(0, 4).map((f) => (
                  <li key={f} className="flex gap-2">
                    <Icon name="check" className="mt-0.5 size-4 shrink-0 text-brand" strokeWidth={2.4} />
                    {f}
                  </li>
                ))}
              </ul>
              <ButtonLink href={p.cta.href} variant={p.highlight ? "primary" : "secondary"} className="mt-7 w-full">
                {p.cta.label}
              </ButtonLink>
            </div>
          ))}
        </div>
        <p className="mt-8 text-center">
          <Link prefetch={false} href="/pricing" className="inline-flex min-h-11 items-center gap-1.5 font-semibold text-brand hover:underline">
            Compare all plans, including Standard, Global and Seva <Icon name="arrow" className="size-4" />
          </Link>
        </p>
      </section>

      {/* Roadmap */}
      <section className="bg-bg-soft py-20 sm:py-28">
        <div className="container-x">
          <SectionHeading
            eyebrow="Published roadmap"
            title="Know exactly what ships, and when"
            lead="Target dates, refined with our pilot schools. Every release passes security, isolation and backup checks before it reaches a school."
          />
          <div className="mt-12">
            <RoadmapTimeline compact />
          </div>
          <div className="mt-8">
            <ButtonLink href="/roadmap" variant="secondary" arrow>
              Full roadmap
            </ButtonLink>
          </div>
        </div>
      </section>

      {/* FAQ */}
      <section className="container-x grid gap-10 py-20 sm:py-28 lg:grid-cols-[0.8fr_1.2fr]">
        <SectionHeading eyebrow="FAQ" title="Questions school owners ask us" lead={<>Can&apos;t find your answer? <Link prefetch={false} href="/contact" className="font-semibold text-brand underline-offset-4 hover:underline">Talk to us</Link>.</>} />
        <FaqList items={homeFaqs} />
      </section>

      <CtaBand />
    </>
  );
}
