#!/usr/bin/env node
/**
 * Builds the country flags for the quiz clipart picker: server/pb_public/clipart/flags/<code>.png
 * and server/pb_public/clipart/flags.json (same row shape as fluent.json).
 *
 *     node tools/make_flags.js server/pb_public/clipart
 *
 * Flags come from flag-icons (MIT, clipart/flags/LICENSE.txt) as SVG and are drawn to 320x240
 * PNGs with Playwright's Chromium, so the phone and the TV only ever see plain pictures.
 */
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const VERSION = "7.5.0";
const CDN = `https://cdn.jsdelivr.net/npm/flag-icons@${VERSION}/`;
// Not countries, but worth having; everything else in flag-icons' list must be an ISO country.
const EXTRA = ["gb-eng", "gb-sct", "gb-wls", "gb-nir", "eu", "un", "xk"];
// Names children (and parents) are likely to type.
const ALIASES = {
  gb: "uk britain great britain union jack", us: "usa america", "gb-eng": "st george",
  "gb-sct": "saltire", "gb-wls": "cymru dragon", ie: "eire", nl: "holland",
  ae: "uae", kr: "korea", kp: "korea", cz: "czech", ru: "russia", va: "vatican",
};
const FLAGS_GROUP = 8; // "Flags" in fluent.json's groups

async function get(url, type) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${url}: ${res.status}`);
  return type === "json" ? res.json() : res.text();
}

(async () => {
  const out = process.argv[2];
  if (!out) throw new Error("usage: node tools/make_flags.js server/pb_public/clipart");
  const dir = path.join(out, "flags");
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, "LICENSE.txt"), await get(CDN + "LICENSE"));

  const countries = (await get(CDN + "country.json", "json"))
    .filter((c) => c.iso || EXTRA.includes(c.code));
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 320, height: 240 } });
  const rows = [];
  for (const c of countries) {
    const svg = await get(CDN + c.flag_4x3);
    await page.setContent(`<style>*{margin:0}img{display:block;width:320px;height:240px}</style>` +
      `<img src="data:image/svg+xml;base64,${Buffer.from(svg).toString("base64")}">`);
    await page.locator("img").evaluate((img) => img.decode());
    await page.screenshot({ path: path.join(dir, c.code + ".png"), omitBackground: true });
    const keywords = ["flag", "country", c.continent, c.capital, ALIASES[c.code]].filter(Boolean).join(" ");
    rows.push([c.name, "", `/clipart/flags/${c.code}.png`, keywords.toLowerCase(), FLAGS_GROUP]);
  }
  await browser.close();
  rows.sort((a, b) => a[0].localeCompare(b[0]));
  fs.writeFileSync(path.join(out, "flags.json"), JSON.stringify({ emoji: rows }));
  console.log(`${rows.length} flags`);
})().catch((e) => { console.error(e); process.exit(1); });
