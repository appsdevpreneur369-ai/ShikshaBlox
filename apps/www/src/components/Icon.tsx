import type { IconName } from "@/data/modules";

type Extra = "arrow" | "menu" | "close" | "chevron" | "plus" | "external" | "whatsapp" | "mail" | "pin" | "quote";

// Stroke icons on a 24×24 grid (Lucide-style, drawn inline so no icon font or extra request).
const paths: Record<IconName | Extra, string> = {
  globe: "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Zm-9 9h18M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3Z",
  shield: "M12 3 4.5 6v6c0 4.4 3.1 7.9 7.5 9 4.4-1.1 7.5-4.6 7.5-9V6L12 3Zm-3 9 2 2 4-4",
  funnel: "M3.5 5h17l-6.5 8v5.5l-4 2V13L3.5 5Z",
  phone: "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2Z",
  chat: "M4 5h16v11H9l-5 4V5Zm4 5h8M8 13h5",
  users: "M16 20v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 18.5V20M10 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm10 9v-1.5a3.5 3.5 0 0 0-2.6-3.4M15.5 4.2a3.5 3.5 0 0 1 0 6.6",
  home: "M3.5 11 12 4l8.5 7M6 9.5V20h12V9.5M10 20v-5h4v5",
  check: "M4 12.5 9 17.5 20 6.5",
  calendar: "M4 6h16v14H4V6Zm0 4h16M8 3.5V7m8-3.5V7",
  book: "M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5v-15ZM4 20.5A2.5 2.5 0 0 1 6.5 18H20v3H6.5",
  pencil: "M4 20h4L19.5 8.5a2.1 2.1 0 0 0-3-3L5 17v3Zm10-13 3 3",
  award: "M12 14a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm-3.5-1.5L7 21l5-3 5 3-1.5-8.5",
  sprout: "M12 21v-9m0 0C12 8 9 5 4 5c0 4 3 7 8 7Zm0 0c0-3 2.5-6 7-6 0 3.5-2.5 6-7 6Z",
  file: "M6 3h8l4 4v14H6V3Zm8 0v4h4M9 12h6M9 16h6",
  world: "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM4 9h4l2 3-2 3H5m11-11-1 3 3 2 3-1m-6 14 1-4 3-1",
  ticket: "M3.5 8V6h17v2a2 2 0 0 0 0 4v2a2 2 0 0 0 0 4v0H3.5v-2a2 2 0 0 0 0-4V10a2 2 0 0 0 0-2Zm10-2v12",
  lock: "M6 11h12v10H6V11Zm2.5 0V8a3.5 3.5 0 0 1 7 0v3m-3.5 4v2",
  heart: "M12 20s-7.5-4.6-7.5-10A4.5 4.5 0 0 1 12 7a4.5 4.5 0 0 1 7.5 3c0 5.4-7.5 10-7.5 10Z",
  compass: "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Zm3.5 5.5-2 5-5 2 2-5 5-2Z",
  star: "m12 3.5 2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9L12 3.5Z",
  wallet: "M4 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a1 1 0 0 1-1-1V7Zm0 0 11-3v3m1 6.5h.01",
  box: "M12 3 4 7v10l8 4 8-4V7l-8-4Zm-8 4 8 4 8-4m-8 4v10",
  calculator: "M6 3h12v18H6V3Zm3 4h6m-6 5h.01M12 12h.01M15 12h.01M9 15.5h.01M12 15.5h.01M15 15.5h.01",
  bag: "M5 8h14l-1 13H6L5 8Zm4 0V6a3 3 0 0 1 6 0v2",
  id: "M3.5 6h17v12h-17V6Zm4.5 7.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm5-3h4m-4 3h3m-11 2h6",
  briefcase: "M4 8h16v11H4V8Zm5 0V5h6v3m-11 5h16",
  trend: "M3.5 17 9 11.5l4 4 7.5-7.5M15 8h5.5v5.5",
  bus: "M5 5.5A2.5 2.5 0 0 1 7.5 3h9A2.5 2.5 0 0 1 19 5.5V18H5V5.5Zm0 6.5h14M8 18v2.5m8-2.5v2.5M8 15h.01M16 15h.01",
  utensils: "M7 3v8m-2.5-8v5a2.5 2.5 0 0 0 5 0V3M7 11v10m10-18c-2 1.5-3 4-3 7h3v11",
  library: "M5 4v16m4-16v16m4-14 4 14M3.5 20h17",
  trophy: "M8 4h8v5a4 4 0 0 1-8 0V4Zm0 2H4.5a3 3 0 0 0 3.5 4m8-4h3.5a3 3 0 0 1-3.5 4M12 13v4m-4 3.5h8L15 17H9l-1 3.5Z",
  bed: "M3.5 6v14m0-5h17v5m0-5v-3a3 3 0 0 0-3-3H11v6M7.5 11.5a1.5 1.5 0 1 0 0-.01",
  map: "m3.5 6 5.5-2.5 6 2.5 5.5-2.5V18L15 20.5 9 18l-5.5 2.5V6ZM9 3.5V18m6-12v14.5",
  clock: "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Zm0 4.5V12l3 2",
  wrench: "M14.5 5.5a4 4 0 0 0 4.9 4.9L21 12l-9 9-3.5-3.5 9-9-1.6-1.6a4 4 0 0 0-1.4-1.4ZM3 21l4.5-4.5",
  laptop: "M5 5h14v10H5V5Zm-2 13h18",
  card: "M3.5 6h17v12h-17V6Zm0 4h17M7 15h3",
  video: "M3.5 7h12v10h-12V7Zm12 3.5 5-3v9l-5-3",
  chart: "M4 20V4m0 16h16M8 16v-5m4 5V8m4 8v-3",
  sparkles: "M12 3.5 13.8 9l5.7 1.8-5.7 1.9L12 18.5l-1.8-5.8L4.5 10.8 10.2 9 12 3.5ZM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8L19 16Z",
  plug: "M9 3v5m6-5v5M7 8h10v3a5 5 0 0 1-10 0V8Zm5 8v5",
  building: "M5 21V4h9v17m0-11h5v11M3.5 21h17M8 8h3m-3 4h3m-3 4h3",
  graduation: "m12 5 10 5-10 5L2 10l10-5Zm-6 7.5V17c2 2 10 2 12 0v-4.5M22 10v5",
  megaphone: "M4 10v4h3l7 4V6L7 10H4Zm14-2a5 5 0 0 1 0 8",
  bell: "M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15L6 16Zm4 4a2 2 0 0 0 4 0",
  layers: "m12 3 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5M3 16l9 5 9-5",
  zap: "M13 3 4.5 13.5H12L11 21l8.5-10.5H12L13 3Z",
  smartphone: "M7 3h10v18H7V3Zm4 15h2",
  cloud: "M7 18a4.5 4.5 0 0 1-.5-9A6 6 0 0 1 18 9.5a4 4 0 0 1-.5 8.5H7Z",
  arrow: "M5 12h14m-5-5 5 5-5 5",
  menu: "M4 7h16M4 12h16M4 17h16",
  close: "M6 6l12 12M18 6 6 18",
  chevron: "m6 9 6 6 6-6",
  plus: "M12 5v14M5 12h14",
  external: "M14 4h6v6m0-6-9 9M18 14v6H4V6h6",
  whatsapp: "M4 20l1.3-4A8 8 0 1 1 8 18.7L4 20Zm5-11c0 3.5 2.5 6 6 6l1-1.5-2-1-1 .8c-1-.4-1.9-1.3-2.3-2.3l.8-1-1-2L9 9Z",
  mail: "M3.5 6h17v12h-17V6Zm0 0 8.5 7 8.5-7",
  pin: "M12 21s-6.5-5.5-6.5-11a6.5 6.5 0 0 1 13 0c0 5.5-6.5 11-6.5 11Zm0-8.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z",
  quote: "M9 7H5v6h4v4H6m13-10h-4v6h4v4h-3",
};

export function Icon({
  name,
  className = "size-5",
  strokeWidth = 1.8,
}: {
  name: IconName | Extra;
  className?: string;
  strokeWidth?: number;
}) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      <path d={paths[name]} />
    </svg>
  );
}
