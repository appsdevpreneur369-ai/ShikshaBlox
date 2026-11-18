import { Baloo_2, Inter, Lora, Merriweather, Nunito, Playfair_Display, Poppins } from "next/font/google";

// Self-hosted at build time (no request to Google from visitors). Not preloaded: each school
// uses only two of these, and the browser fetches just the ones its CSS variables reference.
const inter = Inter({ subsets: ["latin"], variable: "--font-inter", preload: false, display: "swap" });
const poppins = Poppins({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-poppins",
  preload: false,
  display: "swap",
});
const merriweather = Merriweather({
  subsets: ["latin"],
  weight: ["400", "700"],
  variable: "--font-merriweather",
  preload: false,
  display: "swap",
});
const playfair = Playfair_Display({
  subsets: ["latin"],
  variable: "--font-playfair",
  preload: false,
  display: "swap",
});
const nunito = Nunito({ subsets: ["latin"], variable: "--font-nunito", preload: false, display: "swap" });
const lora = Lora({ subsets: ["latin"], variable: "--font-lora", preload: false, display: "swap" });
const baloo = Baloo_2({ subsets: ["latin"], variable: "--font-baloo", preload: false, display: "swap" });

export const fontVariables = [inter, poppins, merriweather, playfair, nunito, lora, baloo]
  .map((f) => f.variable)
  .join(" ");
