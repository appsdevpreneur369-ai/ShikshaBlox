import type { MediaMap } from "@/lib/types";

/**
 * Responsive image from the media map: a plain <img srcset> pointing at the pre-generated
 * variants (the media pipeline already produced WebP/JPEG sizes, so no Next optimizer is needed).
 * Renders nothing when the media is missing or was withheld (e.g. no consent).
 */
export function Img({
  id,
  media,
  sizes = "100vw",
  className,
  priority = false,
  alt,
}: {
  id: string | undefined;
  media: MediaMap;
  sizes?: string;
  className?: string;
  priority?: boolean;
  alt?: string;
}) {
  const m = id ? media[id] : undefined;
  if (!m?.src) return null;
  const srcSet = m.srcset?.length
    ? [...m.srcset]
        .sort((a, b) => a.width - b.width)
        .map((s) => `${s.url} ${s.width}w`)
        .join(", ")
    : undefined;
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={m.src}
      srcSet={srcSet}
      sizes={srcSet ? sizes : undefined}
      width={m.width}
      height={m.height}
      alt={alt ?? m.alt ?? ""}
      loading={priority ? "eager" : "lazy"}
      fetchPriority={priority ? "high" : undefined}
      decoding="async"
      className={className}
    />
  );
}
