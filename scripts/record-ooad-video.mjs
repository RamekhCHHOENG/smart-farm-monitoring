// Records "OOAD in practice": the analysis and design deliverables (D1-D11) and the working app built from them.
// Each chapter shows the concept, the diagram, the real code that implements it (read from the
// source files at recording time, with real line numbers) and, where possible, the running app.
// Run against the demo farm on a FRESH database, while both servers run:
//   PW=<demo password> KEY=<device key> OUT=<folder> DIAGRAMS=<folder with the PlantUML PNGs> \
//   [KOKORO="<python> scripts/tts_server.py <model.onnx> <voices.npz> af_heart"] \
//   node scripts/record-ooad-video.mjs
// Then: node scripts/make-demo-video.mjs <OUT> <video.mp4>
// Render the diagrams first: java -jar plantuml.jar -tpng -Sdpi=130 -o <DIAGRAMS> docs/diagrams/0*.puml
import { chromium } from "playwright";
import { spawn } from "node:child_process";
import readline from "node:readline";
import fs from "node:fs";
import path from "node:path";

const BASE = "http://localhost:3000";
const PW = process.env.PW;
const KEY = process.env.KEY;
const OUT = process.env.OUT ?? "ooad-video";
const DIAGRAMS = process.env.DIAGRAMS ?? "diagrams";
const SRC = "backend/src/main/java/com/smartfarm";
const TEST = "backend/src/test/java/com/smartfarm";
fs.mkdirSync(`${OUT}/audio`, { recursive: true });
const browser = await chromium.launch();
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
  await lines.next();
  kokoro = {
    async say(text, out) {
      proc.stdin.write(JSON.stringify({ text, out }) + "\n");
      return JSON.parse((await lines.next()).value).ms;
    },
    close: () => proc.stdin.end(),
  };
}
const spoken = (text) => text.replace(/<[^>]+>/g, "").replace(/°C/g, " degrees").replace(/ %/g, " percent")
  .replace(/#(\d)/g, "number $1").replace(/\bD(\d+)\b/g, "deliverable $1").replace(/\bFR-0?(\d+)/g, "F R $1")
  .replace(/\bUC-(\d+)/g, "use case $1").replace(/→/g, " to ");
async function speak(text, shownAt) {
  if (!kokoro || !text) return 0;
  cueCount += 1;
  const file = `${OUT}/audio/cue-${String(cueCount).padStart(3, "0")}.wav`;
  const ms = await kokoro.say(spoken(text), file);
  cues.push({ scene, offsetMs: shownAt - sceneStart, file, ms });
  return ms;
}

// ---------- scenes, captions, narration ----------
const CAPTION_JS = `
  window.__caption = (title, text) => {
    let el = document.getElementById("__cap");
    if (!el) {
      el = document.createElement("div");
      el.id = "__cap";
      el.style.cssText = "position:fixed;left:50%;bottom:18px;transform:translateX(-50%);z-index:99999;max-width:90%;" +
        "background:rgba(20,35,27,0.94);color:#F1F5EF;font:500 18px/1.4 Geist,Arial,sans-serif;padding:10px 18px;" +
        "border-radius:12px;box-shadow:0 10px 30px rgba(0,0,0,.35);pointer-events:none";
      document.documentElement.appendChild(el);
    }
    el.innerHTML = (title ? '<div style="font-size:12px;letter-spacing:2px;text-transform:uppercase;color:#7DCB94;margin-bottom:2px">' + title + "</div>" : "") + text;
    el.style.display = text ? "block" : "none";
  };`;

async function newScene(name, viewport = SIZE) {
  scene += 1;
  const ctx = await browser.newContext({ viewport, deviceScaleFactor: 1, recordVideo: { dir: `${OUT}/raw-${String(scene).padStart(2, "0")}-${name}`, size: viewport } });
  await ctx.addInitScript(CAPTION_JS);
  const page = await ctx.newPage();
  sceneStart = Date.now();
  page.on("dialog", (d) => d.accept());
  return { ctx, page };
}
/** Caption + voice; stays as long as the sentence takes. */
async function say(page, title, text, holdMs = 3000) {
  await page.evaluate(([t, x]) => window.__caption?.(t, x), [title, text]);
  const shownAt = Date.now();
  const ms = await speak(text, shownAt);
  await page.waitForTimeout(Math.max(0, Math.max(holdMs, ms ? ms + 600 : 0) - (Date.now() - shownAt)));
}
async function login(page, user) {
  await page.goto(`${BASE}/login`);
  await page.fill("#username", user);
  await page.fill("#password", PW);
  await page.press("#password", "Enter");
  await page.waitForURL(`${BASE}/`);
  await page.waitForTimeout(1200);
}
const btn = (page, name) => page.getByRole("button", { name, exact: false });

// ---------- slides (rendered from HTML; bottom 96 px stay free for captions) ----------
const FONT = "font-family:Geist,Arial,sans-serif";
const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const shell = (inner, bg = "#F6F8F4", fg = "#14231B") => `<html><body style="margin:0;width:1280px;height:720px;overflow:hidden;
  background:${bg};color:${fg};${FONT}"><div style="box-sizing:border-box;width:1280px;height:720px;padding:34px 48px 104px;
  display:flex;flex-direction:column;gap:14px">${inner}</div></body></html>`;
const head = (kicker, title, dark = false) => `<div style="font-size:14px;letter-spacing:3px;text-transform:uppercase;font-weight:600;
  color:${dark ? "#7DCB94" : "#2F8F4E"}">${kicker}</div><div style="font-size:34px;font-weight:700;line-height:1.15">${title}</div>`;

async function card(page, kicker, title, lines) {
  await page.setContent(shell(`<div style="flex:1;display:flex;flex-direction:column;justify-content:center;gap:18px">
    ${head(kicker, `<span style="font-size:52px">${title}</span>`, true)}
    ${lines.map((l) => `<div style="font-size:23px;color:#BFD3C4;line-height:1.55">${l}</div>`).join("")}</div>`, "#14231B", "#F1F5EF"));
}
async function info(page, kicker, title, html) {
  await page.setContent(shell(`${head(kicker, title)}<div style="flex:1;min-height:0">${html}</div>`));
}
const table = (headRow, rows, widths) => `<table style="width:100%;border-collapse:collapse;font-size:16px;line-height:1.35">
  <tr>${headRow.map((h, i) => `<th style="text-align:left;background:#E3F0E7;padding:7px 10px;width:${widths?.[i] ?? "auto"}">${h}</th>`).join("")}</tr>
  ${rows.map((r) => `<tr>${r.map((c) => `<td style="padding:6px 10px;border-bottom:1px solid #DCE6DE;vertical-align:top">${c}</td>`).join("")}</tr>`).join("")}</table>`;
async function diagram(page, kicker, title, file, note = "") {
  const data = fs.readFileSync(path.join(DIAGRAMS, file)).toString("base64");
  await page.setContent(shell(`${head(kicker, title)}<div style="flex:1;min-height:0;display:flex;gap:18px">
    <div style="flex:1;min-width:0;background:#FDFEFB;border:1px solid #DCE6DE;border-radius:14px;padding:10px;display:flex;align-items:center;justify-content:center">
      <img src="data:image/png;base64,${data}" style="max-width:100%;max-height:100%;object-fit:contain"></div>
    ${note ? `<div style="width:300px;font-size:17px;line-height:1.5;color:#3F4F45">${note}</div>` : ""}</div>`));
}

// Minimal Java highlighter: comments, strings, annotations, keywords, types, numbers
const KW = new Set("public private protected abstract class interface extends implements return if else for new final static void boolean int double long throw throws switch case default this super true false null try catch var record enum".split(" "));
function highlight(line) {
  const out = [];
  const re = /(\/\/.*$|\/\*\*?.*?(\*\/|$)|^\s*\*.*$|"(?:[^"\\]|\\.)*"|@\w+|\b[A-Za-z_]\w*\b|\b\d+(\.\d+)?\b|[^\w\s]+|\s+)/g;
  let m;
  while ((m = re.exec(line))) {
    const t = m[0];
    let color = null;
    if (/^(\/\/|\/\*|\s*\*)/.test(t)) color = "#7F9C8A";
    else if (t.startsWith('"')) color = "#E6C07B";
    else if (t.startsWith("@")) color = "#C792EA";
    else if (KW.has(t)) color = "#7DCB94";
    else if (/^[A-Z]\w*$/.test(t)) color = "#82AAFF";
    else if (/^\d/.test(t)) color = "#F78C6C";
    out.push(color ? `<span style="color:${color}">${esc(t)}</span>` : esc(t));
  }
  return out.join("");
}
/** Lines of a real source file, found by text markers; marked lines are highlighted. */
function snippet(file, from, count, marks = []) {
  const lines = fs.readFileSync(file, "utf8").split("\n");
  const start = lines.findIndex((l) => l.includes(from));
  if (start < 0) throw new Error(`"${from}" not found in ${file}`);
  return { file: file.replace(/^backend\/src\/(main|test)\/java\//, ""), start: start + 1, lines: lines.slice(start, start + count), marks };
}
async function code(page, kicker, title, points, snip) {
  const body = snip.lines.map((l, i) => {
    const marked = snip.marks.some((m) => l.includes(m));
    return `<div style="display:flex;${marked ? "background:rgba(125,203,148,0.16);box-shadow:inset 3px 0 #7DCB94" : ""}">
      <span style="width:42px;flex:none;text-align:right;padding-right:12px;color:#5C7466">${snip.start + i}</span>
      <span style="white-space:pre-wrap;padding-left:2.5em;text-indent:-2.5em">${highlight(l)}</span></div>`;
  }).join("");
  await page.setContent(shell(`${head(kicker, title)}<div style="flex:1;min-height:0;display:flex;gap:20px">
    <div style="width:330px;flex:none;display:flex;flex-direction:column;gap:10px">
      ${points.map((p) => `<div style="font-size:17px;line-height:1.45;background:#FDFEFB;border:1px solid #DCE6DE;border-radius:10px;padding:10px 12px">${p}</div>`).join("")}</div>
    <div style="flex:1;min-width:0;background:#14231B;border-radius:14px;overflow:hidden;display:flex;flex-direction:column">
      <div style="padding:8px 14px;font:600 13px/1 'Geist Mono',monospace;color:#9DB5A4;background:#1D3226">${snip.file}</div>
      <div style="padding:10px 0;font:12.5px/1.5 'Geist Mono',Consolas,monospace;color:#E7EFE9;overflow:hidden">${body}</div></div></div>`));
}

// =====================================================================================
let { ctx, page } = await newScene("title");
await card(page, "Object-Oriented Analysis and Design · Group 1", "OOAD in practice: from analysis to a working app", [
  "Smart Farm Monitoring System",
  "Eleven OOAD deliverables, each shown as concept, model, real code and running app",
]);
await say(page, "", "This video walks through our eleven object oriented analysis and design deliverables, and the working farm system we built from them.", 6000);
await say(page, "", "The goal is to show that we can analyse a real problem, design it with objects, and then build software that follows the design.", 4500);
await info(page, "The OOAD process we followed", "Analysis → Design → Implementation → Verification", table(["Phase", "Question it answers", "Deliverables"], [
  ["<b>Analysis</b>", "What must the system do, and for whom?", "D1 Problem · D2 Actors · D3 Requirements · D4 Use cases · D5 Domain model"],
  ["<b>Design</b>", "Which objects do the work, and how do they collaborate?", "D6 Class diagram · D7 Sequence · D8 Activity · D9 State machine · D10 GRASP · D11 SOLID"],
  ["<b>Implementation</b>", "Does the code follow the design?", "Java Spring Boot backend, Nuxt frontend, ESP32 firmware"],
  ["<b>Verification</b>", "Does it really work?", "31 tests · 34-step walkthrough · 11 farm scenarios"],
], ["190px", "360px"]));
await say(page, "The OOAD process", "We followed the OOAD process. Analysis answers what the system must do and for whom: deliverables one to five. Design answers which objects do the work and how they collaborate: deliverables six to eleven. Then we implemented the design and verified it with tests.", 6000);
await ctx.close();

// ---------- D1 ----------
({ ctx, page } = await newScene("d1-problem"));
await info(page, "Analysis · Deliverable 1", "Problem statement", table(["", ""], [
  ["<b>Problem</b>", "Small farms in Cambodia are checked by hand. Dry soil, heat stress in a chicken house or an empty feeder are found too late: crop loss, sick animals, wasted water and feed, long hours."],
  ["<b>Solution</b>", "Sensors in crop beds and chicken houses; rules that start pumps, fans and feeders automatically; alerts to farm staff; schedules; reports."],
  ["<b>Core value</b>", "Faster response (seconds, not hours) · fewer losses · lower workload · decisions based on data"],
], ["170px"]));
await say(page, "Deliverable 1 · Problem", "Small farms are still checked by hand, so problems like dry soil, heat stress or an empty feeder are found too late.", 5000);
await say(page, "Deliverable 1 · Solution", "Our solution: sensors watch each zone, rules react automatically, and staff are alerted within seconds.", 4500);
await login(page, "owner");
await say(page, "The running system", "Here is the result: the live dashboard of a crop bed and a chicken house, refreshing every four seconds.", 5000);
await ctx.close();

// ---------- D2 ----------
({ ctx, page } = await newScene("d2-actors"));
await info(page, "Analysis · Deliverable 2", "Actors and permissions", table(["Actor", "Type", "Can do"], [
  ["Farm Owner", "Primary", "Everything on the farm: zones, rules, schedules, staff accounts"],
  ["Farm Worker", "Primary", "Watch the dashboard, start and stop devices, acknowledge alerts"],
  ["Technician", "Supporting", "Register devices, report faults, repair or decommission them"],
  ["Sensor Device", "External system", "Send readings and read its relay state, with a device key"],
  ["Notification Service", "External system", "Deliver alerts (console or Telegram)"],
], ["190px", "160px"]));
await say(page, "Deliverable 2 · Actors", "We identified five actors: three people, the farm owner, worker and technician, and two external systems, the sensor device and the notification service.", 6500);
await code(page, "Analysis · Deliverable 2 · In the code", "Permissions are checked on the server", [
  "Every write operation calls <b>AccessPolicy.require</b> with the roles allowed to do it.",
  "The owner always passes; other roles only get what the permissions table gives them.",
  "Hiding a button in the UI is never the only protection.",
], snippet(`${SRC}/service/AccessPolicy.java`, "public AppUser require", 11, ["Role.OWNER", "throw new ForbiddenException"]));
await say(page, "Deliverable 2 · AccessPolicy", "The permissions table becomes this method. Every service calls require with the allowed roles, so the server enforces the rules even if someone bypasses the user interface.", 7000);
await login(page, "worker");
await page.goto(`${BASE}/rules`);
await page.waitForTimeout(1200);
await say(page, "Live: Farm Worker", "Signed in as a farm worker, the rules are read only: there is no button to create, edit or delete a rule.", 5000);
await ctx.close();

// ---------- D3 ----------
({ ctx, page } = await newScene("d3-requirements"));
await info(page, "Analysis · Deliverable 3", "Functional requirements, each one a screen or a service", table(["ID", "Requirement", "Where in the app"], [
  ["FR-01", "Farm and zones (crop, poultry)", "Zones & devices · FarmService"],
  ["FR-02", "Register, rename, remove devices", "Zones & devices · FarmService"],
  ["FR-03", "Receive and validate readings", "ReadingController · MonitoringService"],
  ["FR-04", "Live dashboard", "Dashboard · QueryService"],
  ["FR-05 / 06", "Automation rules, evaluated on every reading", "Automation rules · AutomationRule"],
  ["FR-07", "Scheduled tasks", "Schedules · TaskService"],
  ["FR-08", "Manual control with a log", "Dashboard · ActuatorService"],
  ["FR-09", "Alerts: breach, fault, offline", "Alerts · AlertService"],
  ["FR-10", "History, device log, CSV", "History · QueryService"],
  ["FR-11", "Staff accounts", "Users · UserService"],
], ["110px", "430px"]));
await say(page, "Deliverable 3 · Requirements", "Each functional requirement maps to a screen and to the service that implements it.", 5000);
await login(page, "owner");
for (const [url, text] of [
  ["/zones", "Zones and devices: requirements one and two."],
  ["/rules", "Automation rules: requirements five and six."],
  ["/schedules", "Schedules: requirement seven."],
  ["/history", "History and reports: requirement ten."],
]) {
  await page.goto(`${BASE}${url}`);
  await page.waitForTimeout(900);
  await say(page, "Deliverable 3 · Live", text, 3000);
}
await ctx.close();

// ---------- D4 ----------
({ ctx, page } = await newScene("d4-use-cases"));
await diagram(page, "Analysis · Deliverable 4", "Use case diagram", "use-case.png",
  "Eleven use cases for five actors.<br><br><b>«include»</b>: every reading is evaluated against the rules; every manual action is logged.<br><br><b>«extend»</b>: an alert is sent only when a threshold is breached.");
await say(page, "Deliverable 4 · Use cases", "The use case diagram shows eleven use cases. Submit sensor reading always includes evaluate rules, and send alert extends it only when a threshold is breached.", 7500);
await code(page, "Analysis · Deliverable 4 · In the code", "Use case 3: Submit Sensor Reading", [
  "The device's request arrives at <b>ReadingController</b> (the use case's entry point).",
  "«include» Evaluate Rules: the loop over every active rule always runs.",
  "«extend» Send Alert: only inside <b>if (rule.isTriggered(...))</b>.",
], snippet(`${SRC}/service/MonitoringService.java`, "public ReadingResult processReading", 22, ["for (AutomationRule rule", "rule.isTriggered", "actions.add(respond"]));
await say(page, "Deliverable 4 · include and extend", "In the code, the include is this loop that always checks every active rule, and the extend is the response that only happens when a rule is triggered.", 7000);
await ctx.close();

// ---------- D5 ----------
({ ctx, page } = await newScene("d5-domain"));
await diagram(page, "Analysis · Deliverable 5", "Conceptual domain model", "domain-model.png",
  "Analysis model: real-world concepts, not code yet. Ten classes with associations and multiplicities.<br><br>A farm contains zones; a zone has sensors and actuators; a sensor produces readings.<br><br>Multiplicity 0..* lets a zone exist before its devices are installed.");
await say(page, "Deliverable 5 · Domain model", "The domain model ends the analysis. It shows the concepts of the farm, not software yet: a farm contains zones, a zone has sensors and actuators, and a sensor produces readings.", 6500);
await code(page, "Analysis · Deliverable 5 · In the code", "Composition: a zone owns its devices", [
  "<b>Zone ◆── Sensor</b> and <b>Zone ◆── Actuator</b> are compositions.",
  "<b>cascade = ALL</b> and <b>orphanRemoval</b>: deleting a zone deletes its devices, readings, rules and schedules.",
  "Multiplicity 0..* is a List that may be empty.",
], snippet(`${SRC}/domain/Zone.java`, "public abstract class Zone", 24, ["cascade = CascadeType.ALL", "List<Sensor> sensors", "List<Actuator> actuators"]));
await say(page, "Deliverable 5 · Composition", "The composition from the domain model is real in the code: a zone owns its sensors and actuators, so deleting a zone removes them too.", 6500);
await ctx.close();

// ---------- D6 ----------
({ ctx, page } = await newScene("d6-class-diagram"));
await diagram(page, "Design · Deliverable 6", "Design class diagram", "class-diagram.png",
  "Design moves from concepts to software classes: attributes, methods and five kinds of relationship.<br><br>It applies the four object-oriented design principles; next, each one in the real code.");
await say(page, "Deliverable 6 · Class diagram", "Design starts here. The design class diagram turns the domain concepts into software classes, with attributes, methods and five kinds of relationship. It applies four object oriented design principles. Let's see each one in the code.", 7000);
await code(page, "Design · Deliverable 6 · OO principle: Encapsulation", "The actuator protects its own state", [
  "<b>status</b> is private. Nobody can set it directly.",
  "The only way to change it is through methods like <b>activate</b>, which check the rules first.",
  "A pump can never be started twice, or longer than its safety limit.",
], snippet(`${SRC}/domain/Actuator.java`, "public boolean activate(int durationMin", 18, ["if (status != ActuatorStatus.IDLE", "status = ActuatorStatus.RUNNING", "status = ActuatorStatus.IDLE"]));
await say(page, "Encapsulation", "Encapsulation: the actuator's status is private. It only changes through methods like activate, which first check that the actuator is idle and the duration is within its safety limit.", 7500);
await code(page, "Design · Deliverable 6 · OO principle: Abstraction", "Sensor says what every sensor must do, not how", [
  "<b>Sensor</b> is an <b>abstract class</b>.",
  "Services only know these methods: unit, valid range, which actuator corrects it.",
  "Each concrete sensor fills in the details.",
], snippet(`${SRC}/domain/Sensor.java`, "public abstract SensorType getType()", 16, ["public abstract"]));
await say(page, "Abstraction", "Abstraction: Sensor is an abstract class. It declares what every sensor must provide, like its unit and valid range, without saying how.", 6500);
await code(page, "Design · Deliverable 6 · OO principle: Inheritance", "A soil moisture sensor IS-A sensor", [
  "<b>SoilMoistureSensor extends Sensor</b> and inherits recording, validation and offline detection.",
  "It only adds what is specific: percent, 0 to 100, corrected by the water pump.",
  "Same for TemperatureSensor, FeedLevelSensor, and WaterPump, Fan, Feeder.",
], snippet(`${SRC}/domain/SoilMoistureSensor.java`, "public class SoilMoistureSensor extends Sensor", 24, ["extends Sensor", "@Override"]));
await say(page, "Inheritance", "Inheritance: the soil moisture sensor extends Sensor. It inherits recording and validation, and only adds what is special about soil: percent, zero to one hundred, and the water pump that corrects it.", 8000);
await code(page, "Design · Deliverable 6 · OO principle: Polymorphism", "One call, different behaviour per zone", [
  "<b>zone.createDefaultRules()</b> is one call.",
  "A <b>CropZone</b> answers with a dry-soil rule for the pump; a <b>PoultryZone</b> with heat and feed rules.",
  "The service never asks which kind of zone it has.",
], snippet(`${SRC}/domain/CropZone.java`, "/** Dry soil starts the pump", 13, ["public List<AutomationRule> createDefaultRules()", "Condition.BELOW, 30", "@Override"]));
await say(page, "Polymorphism", "Polymorphism: the service calls create default rules on any zone. A crop zone answers with a dry soil rule for the pump, a poultry zone with heat and feed rules.", 7000);
await login(page, "owner");
await page.goto(`${BASE}/rules`);
await page.waitForTimeout(1200);
await say(page, "Live: polymorphism", "These rules in the app were created by that one polymorphic call: dry soil for the vegetable bed, hot house and low feed for the chicken house.", 6500);
await ctx.close();

// ---------- D7 ----------
({ ctx, page } = await newScene("d7-sequence"));
await diagram(page, "Design · Deliverable 7", "Sequence diagram: Submit Sensor Reading", "sequence.png",
  "Device → ReadingController → MonitoringService → Sensor → rules → Actuator → AlertService → NotificationService.<br><br>loop over the rules, alt when the actuator refuses.");
await say(page, "Deliverable 7 · Sequence", "The sequence diagram follows one reading: from the device, through the controller and the monitoring service, to the sensor, the rules, the actuator and the alert.", 7000);
await code(page, "Design · Deliverable 7 · In the code", "Each message is a method call", [
  "<b>rule.getActuator()</b>, <b>actuator.activate(...)</b> and <b>alertService.raise(...)</b> are the arrows of the diagram.",
  "<b>alt</b>: if the actuator refuses, the alert becomes <b>CRITICAL</b>.",
], snippet(`${SRC}/service/MonitoringService.java`, "private String respond(AutomationRule rule", 25, ["rule.getActuator()", "actuator.activate(", "Severity.CRITICAL", "alertService.raise("]));
await say(page, "Deliverable 7 · Messages", "Every arrow in the diagram is a method call here. And the alternative fragment is this branch: when the actuator refuses, the alert becomes critical.", 7000);
await login(page, "owner");
await page.goto(`${BASE}/farm-lab`);
await btn(page, "Create lab farm").click();
await page.getByText("Lab farm ready").waitFor({ timeout: 15000 });
await page.fill("#lkey", KEY);
await btn(page, "Power on").click();
await say(page, "Live: the sequence in action", "In the Farm Lab, virtual devices send real readings to the server, exactly like an E S P thirty two board. Let's make the soil dry.", 5500);
await page.getByRole("button", { name: "Run Dry spell" }).click();
await page.locator("[role=img][aria-label='Lab pump relay on']").waitFor({ timeout: 120000 });
await page.locator("[role=img][aria-label='Lab pump relay on']").scrollIntoViewIfNeeded();
await say(page, "Live: pump started", "The reading went through that exact sequence: the rule fired, the pump was activated, and an alert was raised. The blue light is the pump's relay.", 6500);
await btn(page, "Power off").click();
await ctx.close();

// ---------- D8 ----------
({ ctx, page } = await newScene("d8-activity"));
await diagram(page, "Design · Deliverable 8", "Activity diagram: automated monitoring and response", "activity.png",
  "Decision <b>[valid?]</b>: an impossible value is rejected and the sensor marked suspect.<br><br><b>Fork</b>: the actuator acts and the alert is sent in parallel.");
await say(page, "Deliverable 8 · Activity", "The activity diagram shows the whole workflow. First a decision: is the reading valid? Then a fork: the actuator acts while the alert is sent.", 7000);
await code(page, "Design · Deliverable 8 · In the code", "The [valid?] decision", [
  "<b>sensor.isValid(value)</b> is the decision node.",
  "No: mark the sensor suspect and answer HTTP 422; nothing else happens.",
  "Yes: record the reading and evaluate the rules.",
], snippet(`${SRC}/service/MonitoringService.java`, "// [valid?] no", 10, ["sensor.isValid(value)", "markSuspect", "sensor.record(value, now)"]));
await say(page, "Deliverable 8 · Decision", "The decision node is this if statement. An invalid value marks the sensor as suspect and is rejected, so a broken sensor can never start a pump.", 6500);
await login(page, "owner");
await page.goto(`${BASE}/farm-lab`);
await btn(page, "Create lab farm").click(); // a new browser session: load the lab devices again (creates nothing twice)
await page.getByText("Lab farm ready").waitFor({ timeout: 15000 });
await page.fill("#lkey", KEY);
const soilCard = page.locator("[data-slot=card]").filter({ hasText: "Lab soil" }).first();
await soilCard.scrollIntoViewIfNeeded();
await soilCard.getByRole("button", { name: "Send impossible value" }).click();
await page.getByText("rejected as impossible").first().waitFor({ timeout: 10000 });
await page.locator("[role=log]").scrollIntoViewIfNeeded();
await say(page, "Live: invalid reading", "Live: the soil sensor sends one hundred and fifty percent. The server answers four twenty two and rejects it.", 5500);
await ctx.close();

// ---------- D9 ----------
({ ctx, page } = await newScene("d9-state-machine"));
await diagram(page, "Design · Deliverable 9", "State machine: the actuator lifecycle", "state-machine.png",
  "Six states: IDLE, RUNNING, FAULT, OFFLINE, MAINTENANCE, DECOMMISSIONED.<br><br>Each transition is a method that first checks the current state. A wrong transition is refused with HTTP 409.");
await say(page, "Deliverable 9 · State machine", "The state machine gives the actuator six states. Every transition in the diagram is a method in the code.", 6000);
await code(page, "Design · Deliverable 9 · In the code", "Guarded transitions", [
  "Each method lists the states it may start from, like the arrows of the diagram.",
  "<b>require(...)</b> throws when the transition is not allowed; the API answers 409 Conflict.",
  "A broken pump can never jump back to IDLE.",
], snippet(`${SRC}/domain/Actuator.java`, "/** IDLE/RUNNING → FAULT */", 28, ["require(Set.of", "status = ActuatorStatus."]));
await say(page, "Deliverable 9 · Guards", "Each transition first checks the current state. A pump in fault can only go to maintenance, and only a passed test brings it back to idle.", 6500);
await login(page, "technician");
await page.goto(`${BASE}/zones`);
const feeder = page.getByRole("row").filter({ hasText: "Feeder 1" });
await feeder.scrollIntoViewIfNeeded();
await say(page, "Live: Technician", "Live, as the technician. The buttons change with the state, so only valid transitions are offered.", 4500);
await feeder.getByRole("button", { name: "Report fault" }).click();
await feeder.getByText("FAULT").waitFor();
await say(page, "IDLE → FAULT", "Report fault: idle to fault. A critical alert is sent, and only start repair is offered now.", 5000);
await feeder.getByRole("button", { name: "Start repair" }).click();
await feeder.getByText("MAINTENANCE").waitFor();
await say(page, "FAULT → MAINTENANCE", "Start repair: fault to maintenance.", 3000);
await feeder.getByRole("button", { name: "Repair done (test passed)" }).click();
await feeder.getByText("IDLE").waitFor();
await say(page, "MAINTENANCE → IDLE", "Repair done with a passed test: back to idle.", 3500);
await ctx.close();

// ---------- D10 ----------
({ ctx, page } = await newScene("d10-grasp"));
await code(page, "Design · Deliverable 10 · GRASP", "Information Expert", [
  "Give the job to the class that has the information.",
  "The <b>rule</b> owns condition, threshold and cooldown, so the rule decides whether a reading triggers it.",
  "The <b>sensor</b> knows its range, so <b>Sensor.isValid</b> decides validity.",
], snippet(`${SRC}/domain/AutomationRule.java`, "public boolean isTriggered(SensorReading reading", 16, ["public boolean isTriggered", "case ABOVE", "case BELOW", "isCoolingDown"]));
await say(page, "GRASP · Information Expert", "Information expert: the class that has the information does the work. The rule owns its condition, threshold and cooldown, so the rule decides if a reading triggers it.", 7500);
await code(page, "Design · Deliverable 10 · GRASP", "Creator", [
  "A class creates the objects it contains or records.",
  "<b>Farm</b> creates its zones, <b>Zone</b> creates its sensors, and <b>Sensor.record</b> creates the <b>SensorReading</b>.",
], snippet(`${SRC}/domain/Sensor.java`, "/** GRASP Creator", 11, ["public SensorReading record", "new SensorReading(this, value, at)"]));
await say(page, "GRASP · Creator", "Creator: a sensor produces readings, so the sensor creates the sensor reading object, after validating the value.", 6000);
await code(page, "Design · Deliverable 10 · GRASP", "Controller, Low Coupling, High Cohesion", [
  "<b>Controller</b>: ReadingController receives the device's system event and delegates; it holds no farm logic.",
  "<b>Low coupling</b>: the device only knows one endpoint.",
  "<b>High cohesion</b>: each service has one focused job.",
], snippet(`${SRC}/web/ReadingController.java`, "/** UC-3 Submit Sensor Reading", 8, ["monitoringService.processReading", "checkKey(key)"]));
await say(page, "GRASP · Controller", "Controller: the reading controller receives the device's request, checks the device key, and hands the work to the monitoring service. Each class stays small and focused.", 7000);
await ctx.close();

// ---------- D11 ----------
({ ctx, page } = await newScene("d11-solid"));
await info(page, "Design · Deliverable 11 · SOLID", "Single Responsibility Principle", table(["Class", "Its one job"], [
  ["AutomationRule", "Decide whether a reading breaks the rule"],
  ["Actuator", "Act, and guard its own state"],
  ["MonitoringService", "Run the workflow for one reading"],
  ["AlertService", "Create, store and deliver alerts"],
  ["NotificationService", "Deliver a message (console or Telegram)"],
  ["Repositories", "Store and load"],
], ["260px"]));
await say(page, "SOLID · Single Responsibility", "Single responsibility: every class has one reason to change. The rule decides, the actuator acts, the alert service alerts, and repositories store.", 7000);
await code(page, "Design · Deliverable 11 · SOLID", "Open/Closed Principle", [
  "Open for extension: a new zone kind (a fish pond) is a new subclass of <b>Zone</b>.",
  "Closed for modification: the services work with the abstract <b>Zone</b> and do not change.",
  "Honest limit: the small <b>create</b> factory still needs one new line.",
], snippet(`${SRC}/domain/CropZone.java`, "public class CropZone extends Zone", 14, ["extends Zone", "super(farm, name)"]));
await say(page, "SOLID · Open Closed", "Open closed: to add a fish pond, we add a subclass of zone. The services that use zone do not change.", 5500);
await code(page, "Design · Deliverable 11 · SOLID", "Dependency Inversion Principle", [
  "<b>AlertService</b> depends on the <b>NotificationService</b> interface, never on Telegram.",
  "<b>ConsoleNotifier</b> and <b>TelegramNotifier</b> implement it.",
  "Choosing one is configuration (AppConfig), not a code change.",
], snippet(`${SRC}/config/AppConfig.java`, "public NotificationService notificationService", 9, ["NotificationService notificationService", "new TelegramNotifier", "new ConsoleNotifier"]));
await say(page, "SOLID · Dependency Inversion", "Dependency inversion: the alert service depends on the notification service interface. Whether alerts go to the console or to Telegram is decided by configuration, not by changing the code.", 7500);
const logFile = process.env.BACKEND_LOG;
if (logFile && fs.existsSync(logFile)) {
  const alerts = fs.readFileSync(logFile, "utf8").split("\n").filter((l) => l.includes("[ALERT")).slice(-6)
    .map((l) => l.replace(/^.*ConsoleNotifier\s*:\s*/, ""));
  await page.setContent(shell(`${head("Design · Deliverable 11 · Live", "ConsoleNotifier output from the running server")}
    <div style="flex:1;background:#0B140F;border-radius:14px;padding:16px;font:14px/1.7 'Geist Mono',monospace;color:#B9F6CA;overflow:hidden">
    ${alerts.map((a) => `<div>${esc(a)}</div>`).join("")}</div>`));
  await say(page, "Live: the interface at work", "These lines were written by the console notifier while we recorded this video. Set a Telegram token, and the same alerts go to staff phones instead.", 6500);
}
await ctx.close();

// ---------- Proof ----------
({ ctx, page } = await newScene("proof"));
await code(page, "Proof", "Every rule of the design has a test", [
  "<b>31</b> automated tests, like these for the state machine.",
  "A <b>34-step</b> browser walkthrough of every use case.",
  "<b>11</b> farm scenarios in the Farm Lab: 10 pass, 1 known gap recorded.",
], snippet(`${TEST}/domain/ActuatorStateMachineTest.java`, "@Test", 26, ["void "]));
await say(page, "Proof", "Finally, every rule of the design has a test. Thirty one automated tests, a thirty four step browser walkthrough, and eleven farm scenarios check that the code really follows the diagrams.", 7500);
await card(page, "Summary", "Analysis → Design → Code → Tests", [
  "Analysis told us what to build; design told us how the objects work together",
  "Every model became classes and methods, and every rule became a test",
  "github.com/RamekhCHHOENG/smart-farm-monitoring",
]);
await say(page, "", "Analysis told us what to build, and design told us how the objects work together. Every model became classes and methods, and every rule became a test. That is object oriented analysis and design in practice. Thank you for watching.", 6000);
await ctx.close();

kokoro?.close();
fs.writeFileSync(`${OUT}/cues.json`, JSON.stringify(cues, null, 1));
await browser.close();
console.log(`Recorded ${scene} scenes and ${cues.length} spoken cues into ${OUT}`);
