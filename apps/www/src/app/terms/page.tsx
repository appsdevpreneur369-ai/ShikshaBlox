import Link from "next/link";
import { LegalPage } from "@/components/LegalPage";
import { pageMetadata } from "@/lib/seo";

export const metadata = pageMetadata({
  title: "Terms of Service",
  description: "The terms that apply to using shikshablox.com and the ShikshaBlox School ERP platform, a product of Aurora Blossom.",
  path: "/terms",
});

export default function TermsPage() {
  return (
    <LegalPage title="Terms of service" path="/terms" updated="26 November 2026">
      <p>
        ShikshaBlox is a School ERP Software product of Aurora Blossom (aurora-blossom.com). These terms cover use of this website.
        Schools that subscribe sign an order form and subscription agreement, which take precedence over these terms.
      </p>
      <h2>Use of this website</h2>
      <p>
        You may browse this website and request a demo. Please don&apos;t misuse it, for example by sending spam through forms or
        trying to access systems you&apos;re not authorised to use.
      </p>
      <h2>Product information</h2>
      <p>
        Module descriptions on this website are a summary. The modules, scope and go-live date for your school are confirmed in your
        order form, and billing for a module starts only when it goes live for your school.
      </p>
      <h2>Prices</h2>
      <p>Prices are in Indian Rupees and exclude GST unless stated otherwise. Usage-based charges (such as WhatsApp messages) are billed separately at the published rates.</p>
      <h2>Intellectual property</h2>
      <p>
        © Aurora Blossom. All rights reserved. ShikshaBlox and its logo belong to Aurora Blossom. Photographs are used under their
        respective free licences. Greenfield Demo School is a fictional school used to demonstrate the product.
      </p>
      <h2>Contact</h2>
      <p>
        Questions about these terms? Reach us via the <Link prefetch={false} href="/contact">contact page</Link>.
      </p>
    </LegalPage>
  );
}
