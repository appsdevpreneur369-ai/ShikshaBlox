import { DemoForm } from "@/components/DemoForm";
import { Icon } from "@/components/Icon";
import { JsonLd, PageHero } from "@/components/ui";
import { site } from "@/data/site";
import { breadcrumbLd, pageMetadata } from "@/lib/seo";

export const metadata = pageMetadata({
  title: "Book a Free Demo of ShikshaBlox School ERP",
  description:
    "Book a free 30-minute demo of ShikshaBlox. See a sample school website, walk through the modules your school needs and get founding-school pricing.",
  path: "/contact",
});

const crumbs = [
  { name: "Home", path: "/" },
  { name: "Book a demo", path: "/contact" },
];

export default function ContactPage() {
  return (
    <>
      <JsonLd data={breadcrumbLd(crumbs)} />
      <PageHero
        crumbs={crumbs}
        eyebrow={<p className="eyebrow">Book a free demo</p>}
        title="See ShikshaBlox for your school"
        lead="Tell us a little about your school. We'll call you within one working day to schedule a 30-minute demo, online or at your school."
      />
      <section className="container-x grid gap-10 py-14 sm:py-20 lg:grid-cols-[1.4fr_0.9fr]">
        <DemoForm />
        <aside className="space-y-5">
          <div className="card p-6">
            <h2 className="font-display text-lg font-bold text-ink">In your demo</h2>
            <ul className="mt-4 space-y-3 text-ink-2">
              {[
                "A sample school website and Studio walkthrough",
                "The modules your school actually needs",
                "Data migration plan from your current system",
                "Founding-school pricing for your student count",
              ].map((x) => (
                <li key={x} className="flex gap-3">
                  <Icon name="check" className="mt-0.5 size-5 shrink-0 text-live" strokeWidth={2.6} />
                  {x}
                </li>
              ))}
            </ul>
          </div>
          {(site.contact.whatsapp || site.contact.phone || site.contact.email) && (
            <div className="card space-y-3 p-6">
              <h2 className="font-display text-lg font-bold text-ink">Prefer to talk now?</h2>
              {site.contact.whatsapp && (
                <a href={`https://wa.me/${site.contact.whatsapp}`} className="flex min-h-11 items-center gap-3 font-semibold text-brand" rel="noopener" target="_blank">
                  <Icon name="whatsapp" /> WhatsApp us
                </a>
              )}
              {site.contact.phone && (
                <a href={`tel:${site.contact.phone}`} className="flex min-h-11 items-center gap-3 font-semibold text-brand">
                  <Icon name="phone" /> {site.contact.phone}
                </a>
              )}
              {site.contact.email && (
                <a href={`mailto:${site.contact.email}`} className="flex min-h-11 items-center gap-3 font-semibold text-brand">
                  <Icon name="mail" /> {site.contact.email}
                </a>
              )}
            </div>
          )}
          <div className="rounded-2xl bg-bg-tint p-6">
            <p className="font-semibold text-ink">Founding-school offer</p>
            <p className="mt-1 text-sm leading-relaxed text-ink-2">
              First 25 schools: 25% off year 1, 3-year price lock and free migration. Billing for each module starts only when it goes live for your school.
            </p>
          </div>
        </aside>
      </section>
    </>
  );
}
