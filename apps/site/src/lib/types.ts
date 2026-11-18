import type { Block } from "@shikshablox/blocks";

/** Localised text: `{ en: "About", te: "..." }` (menus) or a plain string. */
export type Localised = string | Record<string, string>;

export type MenuItem = { label: Localised; href?: string; children?: MenuItem[] };

export type MediaView = {
  kind: "IMAGE" | "DOCUMENT" | "VIDEO";
  alt?: string;
  caption?: string;
  width?: number;
  height?: number;
  src?: string;
  srcset?: { url: string; width: number }[];
  filename?: string;
  sizeBytes?: number;
  contentType?: string;
};
export type MediaMap = Record<string, MediaView>;

export type Brand = {
  primary?: string;
  accent?: string;
  fontHeading?: string;
  fontBody?: string;
  radius?: "soft" | "round" | "pill";
};

export type Profile = {
  tagline?: string;
  board?: string;
  boardName?: string;
  established?: string;
  phone?: string;
  email?: string;
  whatsapp?: string;
  mapQuery?: string;
  admissionsText?: string;
  admissionsOpen?: boolean;
  affiliationNo?: string;
  schoolCode?: string;
  officeHours?: string;
  address?: {
    line1?: string;
    line2?: string;
    city?: string;
    state?: string;
    pincode?: string;
    country?: string;
  };
  social?: Partial<Record<"facebook" | "instagram" | "youtube" | "x" | "linkedin", string>>;
};

export type PublicSite = {
  name: string;
  slug: string;
  theme: "classic" | "modern" | "playful";
  brand: Brand;
  profile: Profile;
  languages: string[];
  defaultLanguage: string;
  showBadge: boolean;
  header: MenuItem[];
  footer: MenuItem[];
  media: MediaMap;
  logoId?: string;
};

export type PublicPage = {
  id: string;
  slug: string;
  templateKey: string;
  language: string;
  title: string;
  seo: { metaTitle?: string; metaDescription?: string; ogImageId?: string; noindex?: boolean };
  document: { schemaVersion: number; blocks: Block[] };
  media: MediaMap;
};

/** Content-type feeds (M3): notices, news, events, albums, staff, careers. */
export type FeedItem = {
  id: string;
  title: string;
  summary?: string;
  bodyHtml?: string;
  date?: string;
  endDate?: string;
  location?: string;
  category?: string;
  pinned?: boolean;
  imageId?: string;
  attachmentId?: string;
  role?: string;
  department?: string;
  qualification?: string;
  imageIds?: string[];
  count?: number;
  slug?: string;
};
export type Feed = { items: FeedItem[]; media: MediaMap };

export function t(label: Localised | undefined, lang: string): string {
  if (!label) return "";
  if (typeof label === "string") return label;
  return label[lang] ?? label.en ?? Object.values(label)[0] ?? "";
}
