// Records a start-to-finish demo video of the app with on-screen captions and an optional voice-over.
// Run against the demo farm on a FRESH database, while both servers run:
//   PW=<demo password> KEY=<device key> OUT=<folder> node scripts/record-demo.mjs
// Produces one .webm per scene in OUT; scripts/make-demo-video.mjs joins them into an .mp4.
//
// Voice-over (optional): each caption is also spoken, and stays on screen as long as the sentence
// takes; the timings go to OUT/cues.json. Two voices are supported:
//   KOKORO="<python> <tts_server.py> <model.onnx> <voices.npz> af_heart"   natural neural voice (Kokoro-82M)
//   VOICE=mb-us1                                                           espeak-ng, robotic but needs no model
import { chromium } from "playwright";
import { execFileSync, spawn } from "node:child_process";
import readline from "node:readline";
import fs from "node:fs";

const BASE = "http://localhost:3000";
const PW = process.env.PW;
const KEY = process.env.KEY;
const OUT = process.env.OUT ?? "demo-video";
const VOICE = process.env.VOICE;
fs.mkdirSync(`${OUT}/audio`, { recursive: true });
const cues = [];
let sceneStart = 0;
let cueCount = 0;

// Kokoro runs in a long-lived Python process, so the model loads only once
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

/** Speaks a sentence into a WAV file placed at the moment the caption appeared; returns its length in ms. */
async function speak(text, shownAt) {
  if ((!kokoro && !VOICE) || !text) return 0;
  cueCount += 1;
  const file = `${OUT}/audio/cue-${String(cueCount).padStart(3, "0")}.wav`;
  const plain = text.replace(/<[^>]+>/g, "").replace(/°C/g, " degrees").replace(/ %/g, " percent").replace(/#/g, "number ");
  let ms;
  if (kokoro) {
    ms = await kokoro.say(plain, file);
  } else {
    execFileSync("espeak-ng", ["-v", VOICE, "-s", "150", "-w", file, plain]);
    const header = fs.readFileSync(file);
    ms = Math.round((header.readUInt32LE(40) / header.readUInt32LE(28)) * 1000); // data bytes / bytes per second
  }
  cues.push({ scene, offsetMs: shownAt - sceneStart, file, ms });
  return ms;
}
const browser = await chromium.launch();
const SIZE = { width: 1280, height: 720 };

// Caption bar drawn over the page; a new page starts without a caption
const CAPTION_JS = `
  window.__caption = (title, text) => {
    let el = document.getElementById("__cap");
    if (!el) {
      el = document.createElement("div");
      el.id = "__cap";
      el.style.cssText = "position:fixed;left:50%;bottom:24px;transform:translateX(-50%);z-index:99999;max-width:88%;" +
        "background:rgba(20,35,27,0.92);color:#F1F5EF;font:500 19px/1.4 Geist,Arial,sans-serif;padding:12px 20px;" +
        "border-radius:14px;box-shadow:0 10px 30px rgba(0,0,0,.35);pointer-events:none;transition:opacity .3s";
      document.documentElement.appendChild(el);
    }
    el.innerHTML = (title ? '<div style="font-size:13px;letter-spacing:2px;text-transform:uppercase;color:#7DCB94;margin-bottom:2px">' + title + "</div>" : "") + text;
    el.style.opacity = text ? "1" : "0";
  };
`;

let scene = 0;
async function newScene(name, viewport = SIZE) {
  scene += 1;
  const ctx = await browser.newContext({
    viewport, deviceScaleFactor: 1,
    recordVideo: { dir: `${OUT}/raw-${String(scene).padStart(2, "0")}-${name}`, size: viewport },
  });
  await ctx.addInitScript(CAPTION_JS);
  const page = await ctx.newPage();
  sceneStart = Date.now(); // the video starts with the first page
  page.on("dialog", (d) => d.accept());
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
async function login(page, user, password = PW) {
  await page.goto(`${BASE}/login`);
  await page.waitForTimeout(600);
  await page.fill("#username", user);
  await page.fill("#password", password);
  await page.waitForTimeout(500);
  await page.press("#password", "Enter");
  await page.waitForURL(`${BASE}/`);
  await page.waitForTimeout(1500);
}
async function card(page, kicker, title, lines) {
  await page.setContent(`<html><body style="margin:0;height:100vh;display:flex;flex-direction:column;justify-content:center;
    padding:0 96px;background:#14231B;color:#F1F5EF;font-family:Geist,Arial,sans-serif">
    <div style="font-size:18px;letter-spacing:4px;text-transform:uppercase;color:#7DCB94;font-weight:600">${kicker}</div>
    <div style="font-size:58px;font-weight:700;line-height:1.1;margin:18px 0 26px">${title}</div>
    ${lines.map((l) => `<div style="font-size:24px;color:#BFD3C4;line-height:1.6">${l}</div>`).join("")}
  </body></html>`);
}
const btn = (page, name) => page.getByRole("button", { name, exact: false });

// ---------- 1. Title ----------
let { ctx, page } = await newScene("title");
await card(page, "OOAD Final Project · Group 1", "Smart Farm Monitoring System", [
  "Sensors watch crop beds and chicken houses.",
  "Rules start pumps, fans and feeders by themselves, and alert farm staff.",
  "Java Spring Boot · Nuxt · ESP32",
]);
await narrate(page, "Smart Farm Monitoring System. Sensors watch crop beds and chicken houses. Rules start pumps, fans and feeders by themselves, and alert the farm staff.", 6000);
await ctx.close();

// ---------- 2. Owner: sign in and dashboard ----------
({ ctx, page } = await newScene("dashboard"));
await page.goto(`${BASE}/login`);
await say(page, "Sign in", "Each person has an account: Farm Owner, Farm Worker or Technician. Five wrong passwords lock the account.", 3500);
await login(page, "owner");
await say(page, "Dashboard", "The live dashboard refreshes every 4 seconds. Each zone shows its sensors, and its pumps, fans and feeders.", 5000);
await say(page, "Rule marks", "The black mark on each bar is a rule's threshold. When a value crosses it, the value turns red and the rule acts.", 5000);
await page.locator("a[href*='/history?']").filter({ hasText: "Soil moisture A1" }).first().hover();
await say(page, "Tap a sensor", "Clicking a sensor opens its history.", 2500);
await page.locator("a[href*='/history?']").filter({ hasText: "Soil moisture A1" }).first().click();
await page.waitForTimeout(1500);
await say(page, "History", "The chart shows the readings and the rule's threshold (red dashed line). Device activity lists every start and stop, and who or what caused it.", 5000);
await btn(page, "24 hours").click();
await say(page, "Reports", "Quick ranges from one hour to 30 days, and every reading can be downloaded as a CSV file for Excel.", 4000);

// ---------- 3. Zones, rules, schedules ----------
await page.goto(`${BASE}/zones`);
await say(page, "Zones & devices", "The owner creates zones; the technician registers sensors and actuators. Each device has an #id that the ESP32 uses.", 5000);
await page.goto(`${BASE}/rules`);
await say(page, "Automation rules", "IF a reading crosses a threshold THEN start an actuator and send an alert. A cooldown stops repeated alerts.", 5000);
await page.goto(`${BASE}/schedules`);
await say(page, "Schedules", "Fixed-time tasks, such as feeding at 07:00 and watering at 17:00, in the farm's own time zone.", 4500);

// ---------- 4. Farm Lab ----------
await page.goto(`${BASE}/farm-lab`);
await page.waitForTimeout(1200);
await say(page, "Farm Lab", "No hardware? The Farm Lab creates virtual sensors and actuators that talk to the server exactly like real ESP32 boards.", 5000);
await btn(page, "Create lab farm").click();
await page.getByText("Lab farm ready").waitFor({ timeout: 15000 });
await say(page, "Farm Lab", "One click makes a lab vegetable bed and chicken house, with a real-device soil sensor, temperature and feed sensors, a pump, a fan, a feeder and their rules.", 5000);
await page.fill("#lkey", KEY);
await btn(page, "Power on").click();
await say(page, "Devices on", "Every 5 seconds the virtual devices send their readings and ask the server whether each relay should be on.", 5000);
await page.getByRole("button", { name: "Run Dry spell" }).scrollIntoViewIfNeeded();
await say(page, "Scenario: dry spell", "Hot wind dries the bed. Watch the soil value fall, and the pump light switch on when the server's rule fires below 30 %.", 3000);
await page.getByRole("button", { name: "Run Dry spell" }).click();
await page.locator("[role=img][aria-label='Lab pump relay on']").waitFor({ timeout: 120000 });
await page.locator("[role=img][aria-label='Lab pump relay on']").scrollIntoViewIfNeeded();
await say(page, "Pump started by itself", "The soil dropped below 30 %; the server started the pump for 1 minute and raised a WARNING alert. Nobody pressed a button.", 6000);
await page.getByText("PASS").first().waitFor({ timeout: 30000 });
await page.getByRole("button", { name: "Run Dry spell" }).scrollIntoViewIfNeeded();
await say(page, "Automatic check", "Each scenario checks the server's reaction and reports PASS, FAIL or KNOWN GAP. Eleven scenarios cover drought, rain, heat, feed, broken devices and a power cut.", 6000);
await page.goto(`${BASE}/`);
await page.waitForTimeout(1500);
await say(page, "Back on the dashboard", "The lab zones appear like any other zone, with the pump RUNNING and the alert in the list.", 5000);

// ---------- 5. Broken pump: critical alert ----------
await page.goto(`${BASE}/zones`);
const pumpRow = page.getByRole("row").filter({ hasText: "Water pump A" });
await say(page, "Technician", "The technician finds the pump in Vegetable Bed A broken and reports a fault.", 3000);
await pumpRow.getByRole("button", { name: "Report fault" }).click();
await pumpRow.getByText("FAULT").waitFor();
await page.getByText("New critical alert").waitFor({ timeout: 15000 });
await say(page, "Critical alert", "A red banner appears on every page. With the bell on, it also beeps and shows a pop-up, so nobody misses it.", 5500);
await ctx.close();

// ---------- 6. Worker on a phone, in Khmer ----------
({ ctx, page } = await newScene("phone", { width: 390, height: 720 }));
await login(page, "worker");
await say(page, "Farm Worker, on a phone", "Menu at the bottom, within thumb reach.", 3500);
await page.getByRole("button", { name: "Language" }).click();
await page.waitForTimeout(800);
await say(page, "ខ្មែរ", "Khmer or English with one tap.", 3500);
await page.locator("nav[aria-label]").getByRole("link").nth(1).click();
await page.waitForTimeout(1500);
await say(page, "Acknowledge", "The worker checks the zone on site, then acknowledges the alert. Their name is saved with it.", 3500);
await page.getByRole("button", { name: /ទទួលស្គាល់/ }).first().click();
await page.waitForTimeout(2000);
await say(page, "", "", 500);
await ctx.close();

// ---------- 7. Owner: users, then repair ----------
({ ctx, page } = await newScene("owner-end"));
await login(page, "owner");
await page.goto(`${BASE}/users`);
await say(page, "Staff accounts", "The owner adds staff, changes roles and resets forgotten passwords. A reset signs that person out everywhere.", 5000);
await page.goto(`${BASE}/zones`);
const row = page.getByRole("row").filter({ hasText: "Water pump A" });
await row.getByRole("button", { name: "Start repair" }).click();
await row.getByRole("button", { name: "Repair done (test passed)" }).click();
await row.getByText("IDLE").waitFor();
await say(page, "Repair", "After the repair the pump goes back to IDLE. Every step follows the actuator's state machine from the design.", 4500);
await ctx.close();

// ---------- 8. Closing ----------
({ ctx, page } = await newScene("closing"));
await card(page, "Verified", "Designed with OOAD, proven by tests", [
  "31 automated tests · 34-step browser walkthrough · 11 farm scenarios",
  "Runs with the built-in simulator, the Farm Lab, the Wokwi simulator or a real ESP32",
  "github.com/RamekhCHHOENG/smart-farm-monitoring",
]);
await narrate(page, "Designed with object oriented analysis and design, and proven by thirty one automated tests, a thirty four step browser walkthrough, and eleven farm scenarios. Thank you for watching.", 6000);
await ctx.close();
await browser.close();
kokoro?.close();
fs.writeFileSync(`${OUT}/cues.json`, JSON.stringify(cues, null, 1));
console.log(`Recorded ${scene} scenes${cues.length ? ` and ${cues.length} spoken cues` : ""} into ${OUT}`);
