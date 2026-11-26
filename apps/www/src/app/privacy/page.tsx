import Link from "next/link";
import { LegalPage } from "@/components/LegalPage";
import { pageMetadata } from "@/lib/seo";

export const metadata = pageMetadata({
  title: "Privacy Policy",
  description: "How ShikshaBlox (Aurora Blossom) collects, uses and protects personal data on shikshablox.com and in the ShikshaBlox platform.",
  path: "/privacy",
});

export default function PrivacyPage() {
  return (
    <LegalPage title="Privacy policy" path="/privacy" updated="26 November 2026">
      <p>
        This policy explains how Aurora Blossom (&quot;we&quot;), the company behind ShikshaBlox, handles personal data on this website
        and in the ShikshaBlox platform. We follow India&apos;s Digital Personal Data Protection Act, 2023 (DPDP Act).
      </p>
      <h2>Data we collect on this website</h2>
      <ul>
        <li>Demo requests: your name, school, role, phone, optional email, city, student count and message.</li>
        <li>Basic server logs (IP address, browser, pages requested), kept briefly for security.</li>
      </ul>
      <p>This website uses no advertising trackers, no third-party analytics and no marketing cookies.</p>
      <h2>How we use it</h2>
      <p>
        We use demo-request details only to contact you about ShikshaBlox, with your consent. You can withdraw consent at any time
        by asking us via the <Link prefetch={false} href="/contact">contact page</Link>, and we will delete your request.
      </p>
      <h2>School data in the platform</h2>
      <p>
        Schools are the data fiduciary for student, parent and staff data they store in ShikshaBlox; we act as their data processor
        and process it only on their instructions. Each school&apos;s data is isolated, access-controlled, audited and hosted in
        India. Schools can export their data at any time.
      </p>
      <h2>Sharing</h2>
      <p>
        We never sell personal data. We use carefully chosen infrastructure providers (for example cloud hosting and messaging
        providers) under contracts that protect your data.
      </p>
      <h2>Your rights</h2>
      <p>
        You may ask to access, correct or erase your personal data, or raise a grievance, through our{" "}
        <Link prefetch={false} href="/contact">contact page</Link>. We respond within one working day and resolve requests within the time the law
        requires.
      </p>
    </LegalPage>
  );
}
