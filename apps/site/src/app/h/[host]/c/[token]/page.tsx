import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Circular } from "@/components/Circular";

type Params = { host: string; token: string };

// Personal links: never indexed or cached, and the link preview shows no school data.
export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "Message from the school",
  robots: { index: false, follow: false },
};

export default async function CircularPage({ params }: { params: Promise<Params> }) {
  const { token } = await params;
  const t = decodeURIComponent(token);
  if (!/^[A-Za-z0-9_-]{16,64}$/.test(t)) notFound();
  return (
    <section className="sb-section">
      <div className="sb-container">
        <Circular token={t} />
      </div>
    </section>
  );
}
