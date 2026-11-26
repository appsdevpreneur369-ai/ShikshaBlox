import { ButtonLink } from "@/components/ui";

export default function NotFound() {
  return (
    <section className="bg-mesh">
      <div className="container-x flex min-h-[60vh] flex-col items-center justify-center py-20 text-center">
        <p className="font-display text-7xl font-extrabold text-brand">404</p>
        <h1 className="mt-4 font-display text-3xl font-extrabold text-ink sm:text-4xl">This page has left the classroom</h1>
        <p className="mt-3 max-w-md text-lg text-ink-2">The page you&apos;re looking for doesn&apos;t exist or has moved.</p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <ButtonLink href="/" arrow>
            Back to home
          </ButtonLink>
          <ButtonLink href="/features" variant="secondary">
            Explore features
          </ButtonLink>
        </div>
      </div>
    </section>
  );
}
