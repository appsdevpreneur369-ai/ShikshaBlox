/** ShikshaBlox mark: three snapped blocks (the composable "LEGO" platform) forming a rising step. */
export function LogoMark({ className = "size-9" }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" className={className} aria-hidden="true" focusable="false">
      <rect x="2" y="22" width="16" height="16" rx="4.5" fill="#4338ca" />
      <rect x="22" y="22" width="16" height="16" rx="4.5" fill="#6366f1" />
      <rect x="22" y="2" width="16" height="16" rx="4.5" fill="#f59e0b" />
      <circle cx="10" cy="30" r="2.6" fill="#fff" opacity=".9" />
      <circle cx="30" cy="30" r="2.6" fill="#fff" opacity=".9" />
      <circle cx="30" cy="10" r="2.6" fill="#fff" opacity=".95" />
    </svg>
  );
}

export function Logo({ className = "", inverted = false }: { className?: string; inverted?: boolean }) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <LogoMark />
      <span
        className={`font-display text-[1.35rem] leading-none font-extrabold tracking-tight ${inverted ? "text-white" : "text-ink"}`}
      >
        Shiksha<span className={inverted ? "text-amber-300" : "text-brand"}>Blox</span>
      </span>
    </span>
  );
}
