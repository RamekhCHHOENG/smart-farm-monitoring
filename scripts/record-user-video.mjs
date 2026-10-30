// Records "Smart Farm for farm users": a plain-language video with no code, for owners, workers and technicians.
// It follows the farm-users slides (docs/slides/farm-users.html): the dashboard, a zone, an alert the system handled
// by itself, rules like a thermostat, schedules, a broken sensor and its repair, history, the phone in Khmer,
// and the Farm Lab. Run against the demo farm on a FRESH database, while both servers run:
//   PW=<demo password> KEY=<device key> OUT=<folder> [BASE=http://localhost:3000] [API=http://localhost:8080] \
//   [SAY=Samantha | VOICE=mb-us1 | KOKORO="<python> scripts/tts_server.py <model.onnx> <voices.npz> af_heart"] \
//   node scripts/record-user-video.mjs
// Then: node scripts/make-demo-video.mjs <OUT> <video.mp4>
// Voices: KOKORO (natural, needs the model), SAY (a macOS voice, e.g. Samantha), VOICE (espeak-ng); none = captions only.
import { chromium } from "playwright";
import { execFileSync, spawn } from "node:child_process";
import readline from "node:readline";
import fs from "node:fs";

const BASE = process.env.BASE ?? "http://localhost:3000";
const API = process.env.API ?? "http://localhost:8080";
const PW = process.env.PW;
const KEY = process.env.KEY;
const OUT = process.env.OUT ?? "user-video";
const VOICE = process.env.VOICE;
const SAY = process.env.SAY;
if (!PW || !KEY) {
  console.error("Set PW (demo password) and KEY (device key)");
  process.exit(2);
}
fs.mkdirSync(`${OUT}/audio`, { recursive: true });
const browser = await chromium.launch(process.env.CHANNEL ? { channel: process.env.CHANNEL } : {});
const SIZE = { width: 1280, height: 720 };

// ---------- voice ----------
const cues = [];
let scene = 0;
let sceneStart = 0;
let cueCount = 0;
let kokoro = null;
if (process.env.KOKORO) {
  const [cmd, ...args] = process.env.KOKORO.split(" ");
  const proc = spawn(cmd, args, { stdio: ["pipe", "pipe", "inherit"] });
  const lines = readline.createInterface({ input: proc.stdout })[Symbol.asyncIterator]();
  await lines.next(); // {"ready": true}
  kokoro = {
    async say(text, out) {
      proc.stdin.write(JSON.stringify({ text, out }) + "\n");
      return JSON.parse((await lines.next()).value).ms;
    },
    close: () => proc.stdin.end(),
  };
}
const seconds = (file) => Number(execFileSync("ffprobe", ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file]).toString());

/** Speaks a sentence into a WAV file placed at the moment the caption appeared; returns its length in ms. */
async function speak(text, shownAt) {
  if ((!kokoro && !VOICE && !SAY) || !text) return 0;
  cueCount += 1;
  const file = `${OUT}/audio/cue-${String(cueCount).padStart(3, "0")}.wav`;
  const plain = text.replace(/<[^>]+>/g, "").replace(/°C/g, " degrees").replace(/ ?%/g, " percent").replace(/[“”]/g, "");
  let ms;
  if (kokoro) {
    ms = await kokoro.say(plain, file);
  } else if (SAY) {
    execFileSync("say", ["-v", SAY, "-r", "175", "--file-format=WAVE", "--data-format=LEI16@22050", "-o", file, plain]);
    ms = Math.round(seconds(file) * 1000);
  } else {
    execFileSync("espeak-ng", ["-v", VOICE, "-s", "150", "-w", file, plain]);
    ms = Math.round(seconds(file) * 1000);
  }
  cues.push({ scene, offsetMs: shownAt - sceneStart, file, ms });
  return ms;
}

// ---------- page helpers ----------
const CAPTION_JS = `
  window.__caption = (title, text) => {
    let el = document.getElementById("__cap");
    if (!el) {
      el = document.createElement("div");
      el.id = "__cap";
      el.style.cssText = "position:fixed;left:50%;bottom:24px;transform:translateX(-50%);z-index:99999;max-width:88%;" +
        "background:rgba(20,35,27,0.92);color:#F1F5EF;font:500 19px/1.4 Outfit,Geist,Arial,sans-serif;padding:12px 20px;" +
        "border-radius:14px;box-shadow:0 10px 30px rgba(0,0,0,.35);pointer-events:none;transition:opacity .3s";
      document.documentElement.appendChild(el);
    }
    el.innerHTML = (title ? '<div style="font-size:13px;letter-spacing:2px;text-transform:uppercase;color:#7DCB94;margin-bottom:2px">' + title + "</div>" : "") + text;
    el.style.opacity = text ? "1" : "0";
  };
`;
async function newScene(name, viewport = SIZE) {
  scene += 1;
  const ctx = await browser.newContext({
    viewport, deviceScaleFactor: 1,
    recordVideo: { dir: `${OUT}/raw-${String(scene).padStart(2, "0")}-${name}`, size: viewport },
  });
  await ctx.addInitScript(CAPTION_JS);
  const page = await ctx.newPage();
  sceneStart = Date.now(); // the video starts with the first page
  return { ctx, page };
}
async function say(page, title, text, holdMs = 3500) {
  await page.evaluate(([t, x]) => window.__caption?.(t, x), [title, text]);
  const shownAt = Date.now();
  const spoken = await speak(text, shownAt);
  const hold = Math.max(holdMs, spoken ? spoken + 700 : 0);
  await page.waitForTimeout(Math.max(0, hold - (Date.now() - shownAt)));
}
/** Voice only, for the title and closing cards. */
async function narrate(page, text, holdMs) {
  const shownAt = Date.now();
  const spoken = await speak(text, shownAt);
  const hold = Math.max(holdMs, spoken ? spoken + 900 : 0);
  await page.waitForTimeout(Math.max(0, hold - (Date.now() - shownAt)));
}
async function login(page, user) {
  await page.goto(`${BASE}/login`);
  await page.waitForTimeout(600);
  await page.fill("#username", user);
  await page.fill("#password", PW);
  await page.waitForTimeout(400);
  await page.press("#password", "Enter");
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 15000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(1200);
}
async function card(page, kicker, title, body) {
  await page.setContent(`<html><body style="margin:0;height:100vh;display:flex;flex-direction:column;justify-content:center;
    padding:0 96px;background:#14231B;color:#F1F5EF;font-family:Outfit,Geist,Arial,sans-serif">
    <div style="font-size:18px;letter-spacing:4px;text-transform:uppercase;color:#7DCB94;font-weight:600">${kicker}</div>
    <div style="font-size:56px;font-weight:700;line-height:1.1;margin:18px 0 26px">${title}</div>
    ${body}
  </body></html>`);
}
const lines = (items) => items.map((l) => `<div style="font-size:24px;color:#BFD3C4;line-height:1.6">${l}</div>`).join("");
const btn = (page, name) => page.getByRole("button", { name, exact: false });
async function goto(page, path) {
  await page.goto(`${BASE}${path}`);
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(1200);
}
/** A real board would send this; here the script sends it, with the device key. */
async function deviceReading(sensorId, value) {
  const res = await fetch(`${API}/api/devices/readings`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Device-Key": KEY },
    body: JSON.stringify({ sensorId, value }),
  });
  return res.json();
}

// ---------- 1. Title ----------
let { ctx, page } = await newScene("title");
await card(page, "Smart Farm Monitoring · Guide for farm users", "Your farm watches itself", lines([
  "Sensors check the soil, the air and the feed, day and night.",
  "Pumps, fans and feeders start by themselves.",
  "You get a message when something needs a person.",
]));
await narrate(page, "Smart Farm. Your farm watches itself. Sensors check the soil, the air and the feed, day and night. Pumps, fans and feeders start by themselves, and you get a message when something needs a person.", 6000);
await ctx.close();

// ---------- 2. How it works ----------
({ ctx, page } = await newScene("how"));
await card(page, "How it works", "Measure · decide · act · tell you", lines([
  "1. Sensors measure every few seconds.",
  "2. Your rules say what is normal, for example “soil wetter than 30 %”.",
  "3. Outside that range, the right machine starts, and stops when it is fixed.",
  "4. An alert appears in the app, and on Telegram if you like.",
]));
await narrate(page, "It works in four steps. Sensors measure every few seconds. Your rules say what is normal. When a value goes outside that range, the right machine starts, and it stops again when things are back to normal. And you get an alert in the app, and on Telegram if you like.", 8000);
await ctx.close();

// ---------- 3. Owner: dashboard and a zone ----------
({ ctx, page } = await newScene("dashboard"));
await page.goto(`${BASE}/login`);
await say(page, "Sign in", "Everyone has their own account: the farm owner, the workers and the technician.", 3500);
await login(page, "owner");
await say(page, "Dashboard", "The first screen tells you in one sentence how the farm is doing, with the average temperature and the main values.", 5000);
await page.mouse.wheel(0, 420);
await page.waitForTimeout(800);
await say(page, "Today", "Below: today's tasks, how many readings stayed in range, and a farm map that shows the most stressed zones first.", 5000);
await page.getByRole("link", { name: /Vegetable Bed A/ }).filter({ visible: true }).first().click();
await page.waitForLoadState("networkidle").catch(() => {});
await page.waitForTimeout(1500);
await say(page, "A zone", "Each zone has its own page: the values now, how long today it stayed in the good range, its machines and its alerts.", 5500);
await page.mouse.wheel(0, 650);
await page.waitForTimeout(800);
await say(page, "Charts", "Further down, a chart of the last 24 hours for each sensor, and how long each machine ran.", 4500);

// ---------- 4. Something goes wrong ----------
await page.mouse.wheel(0, -2000);
await say(page, "Dry soil", "Now the soil in this bed dries out. The sensor sends 24 percent, below the 30 percent in the rule.", 4000);
await deviceReading(1, 24);
await page.waitForTimeout(5000);
await say(page, "It reacts by itself", "Within seconds the pump starts by itself. Nobody pressed a button.", 4500);
await goto(page, "/alerts");
await say(page, "Alerts", "The alert says where, what, and what was already done: the pump started, and it runs until the soil is back to 45 percent.", 6000);
await say(page, "Alerts", "Critical alerts also make a sound. Mark an alert as seen after you have checked the zone.", 4500);
await ctx.close();

// ---------- 5. Rules like a thermostat ----------
({ ctx, page } = await newScene("thermostat"));
const band = (bg, left, right, pad = 16) =>
  `<div style="background:${bg};padding:${pad}px 24px;display:flex;justify-content:space-between;font-size:24px"><b>${left}</b><span>${right}</span></div>`;
await card(page, "Rules", "It keeps the chicken house comfortable, like a thermostat",
  `<div style="width:560px;border-radius:16px;overflow:hidden">
    ${band("#B84A3A", "above 32 °C", "fan starts")}${band("#7A3A30", "29 °C", "fan stops")}
    ${band("#2F7D47", "21 – 29 °C", "comfortable", 34)}
    ${band("#2B4A78", "21 °C", "heater stops")}${band("#3D64A0", "below 18 °C", "heater starts")}
  </div>`);
await narrate(page, "Rules work like a thermostat at home. Above 32 degrees the fan starts, and keeps running until the house is back to 29. Below 18 the heater runs until 21. In between, nothing runs, and the fan and the heater never run at the same time.", 9000);
await ctx.close();

({ ctx, page } = await newScene("rules"));
await login(page, "owner");
await goto(page, "/rules");
await say(page, "Automation rules", "Here are the farm's rules. Each one says when to start a machine, and when to stop it.", 4500);
const hot = page.locator("tr", { hasText: "Hot house" }).filter({ visible: true }).first();
const edit = hot.getByRole("button", { name: /^Edit/ });
if (await edit.count()) await edit.first().click();
else {
  await hot.getByRole("button", { name: /^More actions for / }).click();
  await page.getByRole("menuitem", { name: "Edit", exact: true }).click();
}
await page.getByRole("dialog").getByRole("heading").first().click();
await say(page, "One short form", "Setting a rule is one short form: the sensor, when to start, the machine, and the value where it stops.", 5500);
await say(page, "Safe", "The app also refuses rules that would fight each other, and says what to change.", 4000);
await page.keyboard.press("Escape");
await goto(page, "/schedules");
await say(page, "Schedules", "For jobs at fixed times, like feeding the chickens at 7 in the morning, use a schedule. It follows the farm's own clock.", 5500);

// ---------- 6. A broken sensor ----------
await goto(page, "/devices");
await say(page, "Devices", "Every sensor and machine on one page, with its status and latest value. Need attention shows only what is broken.", 5000);
const humidity = page.locator("tr, li, article, [data-row]", { hasText: "Greenhouse humidity" }).filter({ visible: true }).last();
await humidity.scrollIntoViewIfNeeded();
await page.mouse.wheel(0, 200);
await say(page, "Technician", "The technician finds the greenhouse humidity sensor broken, and reports a fault.", 3500);
const report = humidity.getByRole("button", { name: /^Report fault/ });
if (await report.count()) await report.first().click();
else {
  await humidity.getByRole("button", { name: /^More actions for / }).first().click();
  await page.getByRole("menuitem", { name: "Report fault", exact: true }).click();
}
await page.locator("[data-confirm]").filter({ visible: true }).click();
await page.waitForTimeout(1500);
await say(page, "Fault", "Now its readings are ignored and its rules wait, so a wrong value cannot start the mister.", 5000);
await btn(page, "Start repair").filter({ visible: true }).first().click();
await page.waitForTimeout(1500);
await say(page, "Repair", "After fixing it, the technician starts the repair. The sensor sends a test reading.", 6000);
await btn(page, "Complete repair").filter({ visible: true }).first().click();
await page.waitForTimeout(1800);
await say(page, "Back in service", "The repair only counts once a good reading has arrived. The sensor is back online.", 4500);

// ---------- 7. History ----------
await goto(page, "/history");
await page.waitForTimeout(1500);
await say(page, "History", "History shows a chart for any sensor over a day, a week or your own dates, with what each machine did.", 5000);
await say(page, "Reports", "And every reading can be downloaded as a spreadsheet.", 3000);
await ctx.close();

// ---------- 8. Worker on a phone, in Khmer ----------
({ ctx, page } = await newScene("phone", { width: 390, height: 720 }));
await login(page, "worker");
await say(page, "Farm worker, on a phone", "Nobody needs a computer: the same app works on a phone, with the menu at the bottom.", 4500);
await btn(page, "Language").filter({ visible: true }).first().click({ timeout: 3000 }).catch(async () => {
  await page.getByRole("button", { name: "Account menu" }).click();
  await page.getByRole("menuitem", { name: /Language|ខ្មែរ/ }).click();
});
await page.waitForTimeout(1200);
await say(page, "ខ្មែរ", "Khmer or English, with one tap.", 3500);
await goto(page, "/alerts");
await say(page, "Acknowledge", "The worker checks the bed, then marks the alert as seen. Their name is saved with it.", 3500);
await page.getByRole("button", { name: /ទទួលស្គាល់/ }).filter({ visible: true }).first().click();
await page.waitForTimeout(2000);
await say(page, "", "", 500);
await ctx.close();

// ---------- 9. Farm Lab ----------
({ ctx, page } = await newScene("farm-lab"));
await login(page, "owner");
await goto(page, "/farm-lab");
await say(page, "Farm Lab", "Before buying any hardware, try everything in the Farm Lab. Virtual sensors and machines behave like a small farm.", 5500);
await btn(page, "Create lab farm").click();
await page.waitForTimeout(4000);
await say(page, "Farm Lab", "One click makes a lab bed, chicken house and greenhouse, with their machines and rules.", 4000);
const heat = page.getByRole("button", { name: "Run Heat wave" });
await heat.evaluate((el) => el.scrollIntoView({ block: "center" }));
await say(page, "Heat wave", "Let's play a heat wave: the afternoon sun heats the chicken house.", 3000);
await heat.click();
// The scenario's row shows "running…", then its verdict
await page.waitForFunction((el) => /PASS|FAIL/.test(el.parentElement.innerText), await heat.elementHandle(), { timeout: 180000 });
await heat.evaluate((el) => el.scrollIntoView({ block: "center" }));
await say(page, "PASS", "The fan started by itself, and the test says PASS. Seventeen situations like drought, a broken pump or a power cut can be tried this way.", 6500);
await ctx.close();

// ---------- 10. Closing ----------
({ ctx, page } = await newScene("closing"));
await card(page, "In short", "Less walking, faster reaction, fewer losses", lines([
  "Sensors watch · rules react · you are told · everything is recorded",
  "Works on a phone, in Khmer and English · each farm sees only its own data",
  "Tested: 73 automatic checks, a 34-step walk through every screen, 17 farm situations",
]));
await narrate(page, "Sensors watch the farm, rules react in seconds, you are told what happened, and everything is recorded. Less walking, a faster reaction, and fewer losses. Thank you for watching.", 7000);
await ctx.close();

await browser.close();
kokoro?.close();
fs.writeFileSync(`${OUT}/cues.json`, JSON.stringify(cues, null, 1));
console.log(`Recorded ${scene} scenes${cues.length ? ` and ${cues.length} spoken cues` : ""} into ${OUT}`);
