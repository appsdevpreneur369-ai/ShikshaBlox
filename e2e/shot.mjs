// Ad-hoc screenshot helper (not a test): node shot.mjs <outDir> <path...>
import { chromium } from "@playwright/test";
const [out, ...paths] = process.argv.slice(2);
const browser = await chromium.launch({
  args: ["--host-resolver-rules=MAP *.shikshablox.localhost 127.0.0.1"],
});
for (const [w, h, tag] of [[1440, 900, "d"], [390, 844, "m"]]) {
  const page = await browser.newPage({ viewport: { width: w, height: h }, reducedMotion: 'reduce' });
  page.on("console", (m) => m.type() === "error" && console.log("console:", m.text()));
  for (const p of paths) {
    await page.goto(`http://moriah.shikshablox.localhost${p}`, { waitUntil: "networkidle" });
    // scroll through so lazy images load
    await page.evaluate(async () => { for (let y = 0; y < document.body.scrollHeight; y += 600) { window.scrollTo(0, y); await new Promise(r => setTimeout(r, 60)); } window.scrollTo(0, 0); });
    await page.waitForTimeout(400);
    const name = `${tag}-${p.replace(/\W+/g, "_") || "home"}.png`;
    await page.screenshot({ path: `${out}/${name}`, fullPage: tag === "d" });
  }
  await page.close();
}
await browser.close();
