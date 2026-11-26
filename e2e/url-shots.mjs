// Ad-hoc screenshot helper (not a test): node url-shots.mjs <outDir> <baseUrl> <path...>
// Takes a viewport shot at 1440x900 ("d") and 390x844 ("m") plus a full-page shot of each path.
import { chromium } from "@playwright/test";
const [out, base, ...paths] = process.argv.slice(2);
const browser = await chromium.launch();
for (const [w, h, tag] of [[1440, 900, "d"], [390, 844, "m"]]) {
  const page = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: tag === "m" ? 2 : 1, reducedMotion: "reduce" });
  page.on("console", (m) => m.type() === "error" && console.log("console:", m.text()));
  for (const p of paths) {
    await page.goto(new URL(p, base).toString(), { waitUntil: "networkidle" });
    await page.evaluate(async () => { for (let y = 0; y < document.body.scrollHeight; y += 500) { window.scrollTo(0, y); await new Promise((r) => setTimeout(r, 150)); } });
    // Wait until every lazy image has finished loading before capturing.
    await page.waitForFunction(() => [...document.images].every((i) => i.complete), null, { timeout: 15000 }).catch(() => {});
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.waitForTimeout(500);
    const name = `${tag}-${p.replace(/\W+/g, "_").replace(/^_|_$/g, "") || "home"}`;
    await page.screenshot({ path: `${out}/${name}.png` });
    await page.screenshot({ path: `${out}/${name}-full.png`, fullPage: true });
  }
  await page.close();
}
await browser.close();
