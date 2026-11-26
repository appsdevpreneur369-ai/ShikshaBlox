import { describe, expect, it } from "vitest";
import loader from "./image-loader";

describe("image loader", () => {
  it("maps requested widths to the nearest larger variant", () => {
    expect(loader({ src: "/images/a.webp", width: 256 })).toBe("/images/a-320.webp");
    expect(loader({ src: "/images/a.webp", width: 390 })).toBe("/images/a-480.webp");
    expect(loader({ src: "/images/a.webp", width: 828 })).toBe("/images/a-828.webp");
    expect(loader({ src: "/images/a.webp", width: 1000 })).toBe("/images/a-1200.webp");
    expect(loader({ src: "/images/a.webp", width: 3840 })).toBe("/images/a-1600.webp");
  });
  it("leaves other sources alone", () => {
    expect(loader({ src: "/brand/logo.png", width: 100 })).toBe("/brand/logo.png");
  });
});
