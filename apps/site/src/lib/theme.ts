import type { CSSProperties } from "react";
import type { Brand } from "./types";

/** Font family name → the CSS variable next/font registers for it (see app/fonts.ts). */
const FONT_VARS: Record<string, string> = {
  Inter: "--font-inter",
  Poppins: "--font-poppins",
  Merriweather: "--font-merriweather",
  "Playfair Display": "--font-playfair",
  Nunito: "--font-nunito",
  Lora: "--font-lora",
  "Baloo 2": "--font-baloo",
};

const HEX = /^#[0-9a-f]{6}$/i;

function luminance(hex: string): number {
  const c = [1, 3, 5].map((i) => {
    const v = parseInt(hex.slice(i, i + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * c[0]! + 0.7152 * c[1]! + 0.0722 * c[2]!;
}

/** Readable text colour on a background (WCAG relative luminance). */
export function onColor(hex: string): string {
  return luminance(hex) > 0.4 ? "#0f172a" : "#ffffff";
}

/** Mixes a colour with white (amount 0..1) — used for soft tinted surfaces. */
export function tint(hex: string, amount: number): string {
  const ch = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  const out = ch.map((v) => Math.round(v + (255 - v) * amount));
  return `#${out.map((v) => v.toString(16).padStart(2, "0")).join("")}`;
}

function font(name: string | undefined, fallback: string): string {
  const v = name ? FONT_VARS[name] : undefined;
  return v ? `var(${v}), ${fallback}` : fallback;
}

const RADII = { soft: "0.5rem", round: "1rem", pill: "1.5rem" } as const;

/** CSS custom properties for a school's brand; every component reads only these tokens. */
export function brandStyle(brand: Brand): CSSProperties {
  const primary = brand.primary && HEX.test(brand.primary) ? brand.primary : "#1e3a8a";
  const accent = brand.accent && HEX.test(brand.accent) ? brand.accent : "#f59e0b";
  return {
    "--sb-color-primary": primary,
    "--sb-color-on-primary": onColor(primary),
    "--sb-color-primary-soft": tint(primary, 0.9),
    "--sb-color-accent": accent,
    "--sb-color-on-accent": onColor(accent),
    "--sb-color-accent-soft": tint(accent, 0.85),
    "--sb-radius": RADII[brand.radius ?? "round"] ?? RADII.round,
    "--sb-font-heading": font(brand.fontHeading, "ui-sans-serif, system-ui, sans-serif"),
    "--sb-font-body": font(brand.fontBody, "ui-sans-serif, system-ui, sans-serif"),
  } as CSSProperties;
}
