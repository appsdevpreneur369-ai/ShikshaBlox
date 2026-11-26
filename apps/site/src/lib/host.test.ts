import { describe, expect, it } from "vitest";
import { isReservedSubdomain, normaliseHost, platformSubdomain } from "./host";

describe("normaliseHost", () => {
  it("lower-cases and strips port and trailing dot", () => {
    expect(normaliseHost("Moriah.ShikshaBlox.localhost:8080")).toBe("moriah.shikshablox.localhost");
    expect(normaliseHost("www.moriahschool.in.")).toBe("www.moriahschool.in");
  });

  it("rejects malformed or hostile hosts", () => {
    expect(normaliseHost(null)).toBeNull();
    expect(normaliseHost("")).toBeNull();
    expect(normaliseHost("evil.com/../x")).toBeNull();
    expect(normaliseHost("a b.com")).toBeNull();
    expect(normaliseHost("-bad.com")).toBeNull();
    expect(normaliseHost("x".repeat(300))).toBeNull();
  });
});

describe("platformSubdomain", () => {
  const base = "shikshablox.localhost";
  it("extracts a single-label subdomain", () => {
    expect(platformSubdomain("moriah.shikshablox.localhost", base)).toBe("moriah");
  });
  it("returns null for custom domains, the apex and nested labels", () => {
    expect(platformSubdomain("www.moriahschool.in", base)).toBeNull();
    expect(platformSubdomain("shikshablox.localhost", base)).toBeNull();
    expect(platformSubdomain("a.b.shikshablox.localhost", base)).toBeNull();
  });
  it("flags reserved platform subdomains", () => {
    expect(isReservedSubdomain("admin")).toBe(true);
    expect(isReservedSubdomain("www")).toBe(true);
    expect(isReservedSubdomain("studio")).toBe(true);
    expect(isReservedSubdomain("moriah")).toBe(false);
  });
});
