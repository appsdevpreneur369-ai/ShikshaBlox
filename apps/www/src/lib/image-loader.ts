/**
 * next/image loader for pre-generated variants (scripts/gen-images.py): picks the smallest
 * <name>-<w>.webp that is at least as wide as requested. No image is resized at request time,
 * so a cold Cloud Run instance serves images as fast as any static file.
 */
export const VARIANT_WIDTHS = [320, 480, 828, 1200, 1600] as const;

export default function imageLoader({ src, width }: { src: string; width: number; quality?: number }) {
  if (!src.endsWith(".webp") || src.startsWith("http")) return src;
  const w = VARIANT_WIDTHS.find((v) => v >= width) ?? VARIANT_WIDTHS[VARIANT_WIDTHS.length - 1];
  return `${src.slice(0, -5)}-${w}.webp`;
}
