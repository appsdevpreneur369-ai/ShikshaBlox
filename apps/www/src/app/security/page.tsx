import { Icon } from "@/components/Icon";
import { ArchitectureDiagram } from "@/components/visuals";
import { CtaBand, JsonLd, PageHero, SectionHeading } from "@/components/ui";
import { breadcrumbLd, pageMetadata } from "@/lib/seo";

export const metadata = pageMetadata({
  title: "Security & Privacy: DPDP-Aware School Data Protection",
  description:
    "How ShikshaBlox protects school and child data: tenant isolation with row-level security, encryption, audit trails, consent records, role-based access, India hosting and DPDP-aware design.",
  path: "/security",
});

const crumbs = [
  { name: "Home", path: "/" },
  { name: "Security & privacy", path: "/security" },
];

const controls = [
  { icon: "building", title: "Tenant isolation", text: "Every school's records carry its organisation ID and are protected by PostgreSQL row-level security, checked by automated cross-tenant tests." },
  { icon: "lock", title: "Encryption", text: "HTTPS everywhere (TLS). Sensitive fields are additionally encrypted at rest with a platform data key." },
  { icon: "id", title: "Role-based access", text: "Granular roles (admin, editor, approver, viewer and more), multi-factor authentication for platform administrators." },
  { icon: "shield", title: "Audit trail", text: "Who changed what, and when: publishing, enquiries, settings and permissions are all recorded." },
  { icon: "check", title: "Consent ledger", text: "Identifiable student photos need a consent declaration before they go public; enquiry consent is recorded." },
  { icon: "cloud", title: "India hosting & backups", text: "Hosted on Google Cloud in Mumbai (asia-south1) with automated backups and private media storage." },
  { icon: "file", title: "Data portability", text: "Your data is yours. Export it in open formats at any time, including when you leave." },
  { icon: "bell", title: "Abuse protection", text: "Rate limits, spam honeypots and signed, expiring links for previews and private files." },
] as const;

export default function SecurityPage() {
  return (
    <>
      <JsonLd data={breadcrumbLd(crumbs)} />
      <PageHero
        crumbs={crumbs}
        eyebrow={<p className="eyebrow">Security &amp; privacy</p>}
        title="Children's data deserves better than an afterthought"
        lead="ShikshaBlox is designed around India's Digital Personal Data Protection (DPDP) Act from day one: consent, purpose limitation, access control and audit are part of the platform, in every plan."
      />
      <section className="container-x py-16 sm:py-24">
        <ul className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {controls.map((c) => (
            <li key={c.title} className="card card-hover reveal p-6">
              <span className="grid size-11 place-items-center rounded-xl bg-brand-soft text-brand">
                <Icon name={c.icon} />
              </span>
              <h2 className="mt-4 font-display text-lg font-bold text-ink">{c.title}</h2>
              <p className="mt-2 text-sm leading-relaxed text-ink-2">{c.text}</p>
            </li>
          ))}
        </ul>
      </section>
      <section className="bg-bg-soft py-16 sm:py-24">
        <div className="container-x">
          <SectionHeading eyebrow="Architecture" title="One platform, every school isolated" />
          <div className="mt-10">
            <ArchitectureDiagram />
          </div>
        </div>
      </section>
      <section className="container-x py-16 sm:py-24">
        <SectionHeading title="Our commitments" />
        <ul className="mt-8 grid gap-4 md:grid-cols-2">
          {[
            "We never sell or share school or student data for advertising.",
            "Schools are the data fiduciary; we process data only on their instructions.",
            "AI features are opt-in, permission-aware and logged. Nothing goes to a parent without human approval.",
            "Report a security concern to our team via the contact page; we respond within one working day.",
          ].map((t) => (
            <li key={t} className="card flex gap-3 p-5 text-ink-2">
              <Icon name="check" className="mt-0.5 size-5 shrink-0 text-live" strokeWidth={2.6} />
              {t}
            </li>
          ))}
        </ul>
      </section>
      <CtaBand title="Questions from your management or IT team?" text="We're happy to walk through our security model in detail during a demo." />
    </>
  );
}
