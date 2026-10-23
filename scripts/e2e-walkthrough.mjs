// Browser walkthrough of every use case as Owner, Technician and Worker (34 steps).
// Run from the repository root against the demo farm on a FRESH database, while both servers run:
//   npm i --no-save playwright && npx playwright install chromium     (once)
//   PW=<demo password> KEY=<device key> node scripts/e2e-walkthrough.mjs
// Failure screenshots go to SHOTS (default: the system temp folder).
import { chromium } from "playwright";

const BASE = "http://localhost:3000";
const PASSWORD = process.env.PW;
const DEVICE_KEY = process.env.KEY;
const SHOTS = process.env.SHOTS ?? (await import("node:os")).tmpdir();
const results = [];
const problems = [];

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1366, height: 860 } });
page.on("pageerror", (e) => problems.push(`pageerror: ${e}`));
page.on("console", (m) => m.type() === "error" && !m.text().includes("401") && !m.text().includes("403") && !m.text().includes("409") && !m.text().includes("429") && problems.push(`console: ${m.text()}`));
page.on("response", (r) => r.status() >= 500 && problems.push(`HTTP ${r.status()} ${r.url()}`));
page.on("dialog", (d) => d.accept());

async function step(name, fn) {
  try {
    await fn();
    results.push(`PASS  ${name}`);
  } catch (e) {
    results.push(`FAIL  ${name}: ${String(e.message).split("\n")[0]}`);
    await page.screenshot({ path: `${SHOTS}/fail-${results.length}.png`, fullPage: true });
  }
}
// Only visible elements count: phone and desktop layouts both exist in the page, one hidden.
const expectText = async (text, timeout = 8000) =>
  page.getByText(text, { exact: false }).filter({ visible: true }).first().waitFor({ timeout });
const button = (name) => page.getByRole("button", { name, exact: false });
const dialog = () => page.getByRole("dialog");

async function login(user, password = PASSWORD) {
  await page.goto(`${BASE}/login`);
  await page.fill("#username", user);
  await page.fill("#password", password);
  await button("Sign in").click();
  await page.waitForURL(`${BASE}/`, { timeout: 10000 });
}
async function logout() {
  await page.keyboard.press("Escape");
  await page.goto(`${BASE}/`);
  await button("Log out").click();
  await page.waitForURL(`${BASE}/login`);
}
async function device(path, body) {
  const res = await fetch(`http://localhost:8080${path}`, {
    method: body ? "POST" : "GET",
    headers: { "Content-Type": "application/json", "X-Device-Key": DEVICE_KEY },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, json: await res.json() };
}
async function idOf(name) {
  const row = page.getByRole("row").filter({ hasText: name });
  const text = await row.first().innerText();
  return Number(text.match(/#(\d+)/)[1]);
}

let sensorId, pumpId;

await step("Wrong password shows an error", async () => {
  await page.goto(`${BASE}/login`);
  await page.fill("#username", "owner");
  await page.fill("#password", "not-the-password");
  await button("Sign in").click();
  await expectText("Wrong username or password");
});

await step("Owner logs in and sees the dashboard", async () => {
  await login("owner");
  await expectText("Group 1 Demo Farm");
  await expectText("Sensors online");
});

await step("Unauthenticated page redirects to login", async () => {
  const ctx = await browser.newContext();
  const p2 = await ctx.newPage();
  await p2.goto(`${BASE}/rules`);
  await p2.waitForURL(`${BASE}/login`);
  await ctx.close();
});

await step("Owner edits farm settings", async () => {
  await page.goto(`${BASE}/zones`);
  await button("Farm settings").click();
  await page.fill("#farm-name", "Group 1 Test Farm");
  await page.fill("#farm-location", "Phnom Penh");
  await dialog().getByRole("button", { name: "Save" }).click();
  await expectText("Farm settings saved");
  await page.goto(`${BASE}/`);
  await expectText("Group 1 Test Farm");
});

await step("Owner creates a zone", async () => {
  await page.goto(`${BASE}/zones`);
  await button("New zone").click();
  await page.selectOption("#zone-type", "CROP");
  await page.fill("#zone-name", "E2E Bed");
  await page.fill("#crop", "Basil");
  await dialog().getByRole("button", { name: "Save" }).click();
  await expectText("Zone created");
  await expectText("E2E Bed");
});

await step("Owner can also register devices (full access)", async () => {
  // Owner has full access, so the Device button is visible
  await page.getByRole("button", { name: "Device" }).first().waitFor({ timeout: 5000 });
});

await step("(log out)", logout);

await step("Technician registers a real sensor and a pump", async () => {
  await login("technician");
  await page.goto(`${BASE}/zones`);
  if (await button("New zone").count()) throw new Error("technician should not see New zone");
  const card = page.locator("[data-slot=card]").filter({ hasText: "E2E Bed" });
  await card.getByRole("button", { name: "Device" }).click();
  await page.selectOption("#kind", "sensor");
  await page.selectOption("#dtype", "SOIL_MOISTURE");
  await page.fill("#dname", "E2E soil");
  await dialog().locator("input[type=checkbox]").uncheck();
  await dialog().getByRole("button", { name: "Register" }).click();
  await expectText("Device registered");
  await card.getByRole("button", { name: "Device" }).click();
  await page.selectOption("#kind", "actuator");
  await page.selectOption("#dtype", "WATER_PUMP");
  await page.fill("#dname", "E2E pump");
  await page.fill("#maxrun", "3");
  await dialog().getByRole("button", { name: "Register" }).click();
  await expectText("E2E pump");
  sensorId = await idOf("E2E soil");
  pumpId = await idOf("E2E pump");
  await expectText("real device");
});

await step("Technician runs the fault -> repair cycle (state machine)", async () => {
  const row = page.getByRole("row").filter({ hasText: "E2E pump" });
  await row.getByRole("button", { name: "Report fault" }).click();
  await row.getByText("FAULT").waitFor();
  await row.getByRole("button", { name: "Start repair" }).click();
  await row.getByText("MAINTENANCE").waitFor();
  await row.getByRole("button", { name: "Repair done (test passed)" }).click();
  await row.getByText("IDLE").waitFor();
});

await step("(log out)", logout);

await step("Owner creates a rule for the real sensor", async () => {
  await login("owner");
  await page.goto(`${BASE}/rules`);
  await button("New rule").click();
  await page.fill("#rname", "E2E dry soil");
  await page.selectOption("#rsensor", String(sensorId));
  await page.selectOption("#rcond", "BELOW");
  await page.fill("#rthr", "30");
  await page.selectOption("#ract", String(pumpId));
  await page.fill("#rdur", "2");
  await page.selectOption("#rsev", "WARNING");
  await dialog().getByRole("button", { name: "Save rule" }).click();
  await expectText("E2E dry soil");
});

await step("Owner creates a schedule", async () => {
  await page.goto(`${BASE}/schedules`);
  await button("New schedule").click();
  await page.fill("#tname", "E2E evening water");
  await page.selectOption("#tact", String(pumpId));
  await page.fill("#ttime", "18:30");
  await page.fill("#tdur", "2");
  await dialog().getByRole("button", { name: "Save schedule" }).click();
  await expectText("E2E evening water");
  await expectText("Every day");
});

await step("Real device: wet then dry reading starts the pump", async () => {
  const wet = await device("/api/devices/readings", { sensorId, value: 48 });
  if (wet.status !== 201 || wet.json.status !== "OK") throw new Error(JSON.stringify(wet));
  const dry = await device("/api/devices/readings", { sensorId, value: 21 });
  if (dry.json.status !== "ACTION_TAKEN") throw new Error(JSON.stringify(dry));
  const state = await device(`/api/devices/actuators/${pumpId}/state`);
  if (!state.json.relayOn) throw new Error("relay should be on");
  await page.goto(`${BASE}/`);
  const card = page.locator("[data-slot=card]").filter({ hasText: "E2E Bed" });
  await card.getByText("21.0").first().waitFor({ timeout: 8000 });
  await card.getByText("RUNNING").waitFor({ timeout: 8000 });
  await expectText("E2E soil reads 21.0%");
  await page.screenshot({ path: `${SHOTS}/e2e-dashboard.png`, fullPage: true });
});

await step("Invalid reading is rejected", async () => {
  const bad = await device("/api/devices/readings", { sensorId, value: 140 });
  if (bad.status !== 422) throw new Error(`expected 422, got ${bad.status}`);
});

await step("(log out)", logout);

await step("Worker: no edit buttons for rules, zones or schedules", async () => {
  await login("worker");
  await page.goto(`${BASE}/rules`);
  await expectText("E2E dry soil");
  if (await button("New rule").count()) throw new Error("worker sees New rule");
  await page.goto(`${BASE}/zones`);
  if (await button("New zone").count()) throw new Error("worker sees New zone");
  if (await button("Farm settings").count()) throw new Error("worker sees Farm settings");
  await page.goto(`${BASE}/schedules`);
  if (await button("New schedule").count()) throw new Error("worker sees New schedule");
});

await step("Worker acknowledges the alert", async () => {
  await page.goto(`${BASE}/alerts`);
  const row = page.getByRole("row").filter({ hasText: "E2E soil" }).first();
  await row.getByRole("button", { name: "Acknowledge" }).click();
  await page.getByRole("tab", { name: "All" }).click();
  await page.getByRole("row").filter({ hasText: "E2E soil" }).first().getByText("by Dara").waitFor();
});

await step("Worker stops the running pump and starts it manually", async () => {
  await page.goto(`${BASE}/`);
  const pump = page.locator("[data-slot=card]").filter({ hasText: "E2E Bed" });
  await pump.getByRole("button", { name: "Stop" }).click();
  await pump.getByText("IDLE").waitFor();
  await pump.locator("input[type=number]").fill("9");
  await pump.getByRole("button", { name: "Start" }).click();
  await pump.getByText("Enter whole minutes from 1 to 3.").waitFor(); // checked before sending, shown next to the button
  await pump.locator("input[type=number]").fill("1");
  await pump.getByRole("button", { name: "Start" }).click();
  await pump.getByText("RUNNING").waitFor();
});

await step("History shows readings chart and device log", async () => {
  await page.goto(`${BASE}/history`);
  const zoneOption = await page.locator("#hzone option").filter({ hasText: "E2E Bed" }).getAttribute("value");
  await page.selectOption("#hzone", zoneOption);
  await expectText("2 readings");
  await page.locator("svg[role=img]").waitFor();
  await expectText("E2E pump activated");
  await page.screenshot({ path: `${SHOTS}/e2e-history.png`, fullPage: true });
});

await step("Worker changes password and logs in with the new one", async () => {
  await button("Change password").click();
  await page.fill("#pw-current", PASSWORD);
  await page.fill("#pw-new", "worker-new-pass");
  await page.fill("#pw-repeat", "worker-new-pass");
  await dialog().getByRole("button", { name: "Change password" }).click();
  await expectText("Password changed");
  await page.keyboard.press("Escape");
  await logout();
  await login("worker", "worker-new-pass");
});

await step("(log out)", logout);

await step("Owner deletes the test zone (cascade)", async () => {
  await login("owner");
  await page.goto(`${BASE}/zones`);
  await page.getByRole("button", { name: "Delete E2E Bed" }).click();
  await expectText("Zone deleted");
  await page.goto(`${BASE}/rules`);
  await page.waitForTimeout(800);
  if (await page.getByText("E2E dry soil").count()) throw new Error("rule still listed");
  await page.goto(`${BASE}/schedules`);
  await page.waitForTimeout(800);
  if (await page.getByText("E2E evening water").count()) throw new Error("schedule still listed");
});

// ---------- Added in round 2: accounts, editing, export, language, alerts, phone ----------

await step("Owner adds a worker account; the new worker signs in", async () => {
  await page.goto(`${BASE}/users`);
  await button("Add user").click();
  await page.fill("#uname", "Srey Mom");
  await page.fill("#uuser", "sreymom");
  await page.fill("#upass", "first-pass-1");
  await dialog().getByRole("button", { name: "Save" }).click();
  await expectText('can now sign in as "sreymom"');
  await logout();
  await login("sreymom", "first-pass-1");
  await expectText("Group 1 Test Farm");
  if (await page.getByRole("link", { name: "Users" }).filter({ visible: true }).count()) throw new Error("worker sees Users menu");
  await logout();
  await login("owner");
});

await step("Owner edits a schedule in place", async () => {
  await page.goto(`${BASE}/schedules`);
  await page.getByRole("button", { name: "Edit Evening watering" }).click();
  await page.fill("#ttime", "17:30");
  await dialog().getByRole("button", { name: "Save schedule" }).click();
  await page.getByRole("row").filter({ hasText: "Evening watering" }).getByText("17:30").first().waitFor();
});

await step("Sensor renamed without changing its #id", async () => {
  await page.goto(`${BASE}/zones`);
  const row = page.getByRole("row").filter({ hasText: "Air temperature A" });
  const before = await idOf("Air temperature A");
  await row.getByRole("button", { name: "Edit" }).click();
  await page.fill("#ename", "Air temperature A (shade)");
  await dialog().getByRole("button", { name: "Save" }).click();
  await expectText("Device updated");
  if ((await idOf("Air temperature A (shade)")) !== before) throw new Error("id changed");
});

await step("Dashboard tile shows the rule mark and opens its history", async () => {
  await page.goto(`${BASE}/`);
  const tile = page.locator("a[href*='/history?']").filter({ hasText: "Soil moisture A1" }).filter({ visible: true }).first();
  await tile.getByText("< 30").waitFor();
  await tile.click();
  await page.waitForURL(/\/history\?zone=\d+&sensor=\d+/);
  await page.getByRole("heading", { name: "Soil moisture A1" }).or(page.getByText("Soil moisture A1").filter({ visible: true })).first().waitFor();
  const selected = await page.locator("#hsensor option:checked").innerText();
  if (selected !== "Soil moisture A1") throw new Error(`sensor select shows ${selected}`);
});

await step("History quick range and CSV download", async () => {
  await button("24 hours").click();
  await expectText("readings");
  const [download] = await Promise.all([page.waitForEvent("download"), button("Download CSV").click()]);
  const path = await download.path();
  const text = (await import("node:fs")).readFileSync(path, "utf8");
  if (!text.startsWith("time,value\n") || text.split("\n").length < 3) throw new Error(`bad CSV: ${text.slice(0, 60)}`);
});

await step("History ignores a slow, outdated answer after switching zone", async () => {
  // The first zone's sensor answers 3 s late; meanwhile the person switches to the chicken house
  let delayed = false;
  await page.route("**/api/sensors/*/readings?**", async (route) => {
    if (delayed) return route.continue();
    delayed = true; // the first answer comes 3 s late and carries a marker value
    await new Promise((r) => setTimeout(r, 3000));
    await route.fulfill({ json: [{ id: 1, sensorId: 1, value: 99.9, recordedAt: new Date().toISOString() }] });
  });
  await page.goto(`${BASE}/history`);
  const chicken = await page.locator("#hzone option").filter({ hasText: "Chicken House 1" }).getAttribute("value");
  await page.selectOption("#hzone", chicken);
  await page.waitForTimeout(4500); // the late answer for the first zone has arrived by now
  const summary = await page.getByText(/readings · min/).first().innerText();
  await page.unroute("**/api/sensors/*/readings?**");
  if (summary.includes("99.9")) throw new Error(`chart shows the outdated answer: ${summary}`);
});

await step("Farm Lab: create lab farm, faulty reading and dry spell scenarios pass", async () => {
  await page.goto(`${BASE}/farm-lab`);
  await button("Create lab farm").click();
  await expectText("Lab farm ready", 15000);
  await page.fill("#lkey", DEVICE_KEY);
  await button("Power on").click();
  await page.getByRole("button", { name: "Run Faulty sensor reading" }).click();
  await page.locator("div", { hasText: /^Faulty sensor reading\s*PASS/ }).first().waitFor({ timeout: 30000 });
  await page.getByRole("button", { name: "Run Dry spell" }).click();
  await page.locator("[role=img][aria-label='Lab pump relay on']").waitFor({ timeout: 90000 });
  await page.locator("div", { hasText: /^Dry spell\s*PASS/ }).first().waitFor({ timeout: 30000 });
  await button("Power off").click();
});

await step("Khmer language switch", async () => {
  await page.goto(`${BASE}/`);
  await button("Language").click();
  await expectText("ការជូនដំណឹងមិនទាន់ដោះស្រាយ");
  await page.reload();
  await expectText("ការជូនដំណឹងមិនទាន់ដោះស្រាយ"); // remembered
  await page.screenshot({ path: `${SHOTS}/e2e-khmer.png`, fullPage: true });
  await button("ភាសា").click(); // "Language" in Khmer
  await expectText("Open alerts");
});

await step("A device fault shows the critical alert banner on every page", async () => {
  await page.goto(`${BASE}/zones`);
  const feeder = page.getByRole("row").filter({ hasText: "Feeder 1" });
  await feeder.getByRole("button", { name: "Report fault" }).click();
  await feeder.getByText("FAULT").waitFor();
  await page.goto(`${BASE}/schedules`);
  await page.getByRole("button", { name: "Report fault" }).count(); // stay on another page
  await page.goto(`${BASE}/zones`);
  await feeder.getByRole("button", { name: "Start repair" }).click();
  await feeder.getByRole("button", { name: "Repair done (test passed)" }).click();
  await feeder.getByText("IDLE").waitFor();
  await feeder.getByRole("button", { name: "Report fault" }).click(); // second fault: same open alert, counted
  await expectText("New critical alert", 15000);
  await page.goto(`${BASE}/alerts`);
  await page.getByRole("row").filter({ hasText: "Feeder 1 reported a fault" }).getByText("happened 2 times").waitFor();
  const feeder2 = () => page.getByRole("row").filter({ hasText: "Feeder 1" });
  await page.goto(`${BASE}/zones`);
  await feeder2().getByRole("button", { name: "Start repair" }).click();
  await feeder2().getByRole("button", { name: "Repair done (test passed)" }).click();
  await feeder2().getByText("IDLE").waitFor();
});

await step("Dark mode and mobile layout render", async () => {
  await page.goto(`${BASE}/`);
  await button("Dark mode").click();
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${SHOTS}/e2e-dark.png` });
  await button("Light mode").click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await expectText("Group 1 Test Farm");
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
  if (overflow) throw new Error("horizontal scroll on mobile");
  await page.screenshot({ path: `${SHOTS}/e2e-mobile.png` });
});

await step("Phone: bottom tab bar and More menu", async () => {
  const tabBar = page.locator("nav[aria-label=Menu]");
  await tabBar.getByRole("link", { name: "Alerts" }).click();
  await page.waitForURL(`${BASE}/alerts`);
  await tabBar.getByRole("button", { name: "More" }).click();
  await dialog().getByRole("link", { name: "Automation rules" }).click();
  await page.waitForURL(`${BASE}/rules`);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
  if (overflow) throw new Error("horizontal scroll on phone rules page");
  await page.setViewportSize({ width: 1366, height: 860 });
});

await step("Five wrong passwords lock the account; the message says what to do", async () => {
  await logout();
  for (let i = 0; i < 5; i++) {
    await page.fill("#username", "technician");
    await page.fill("#password", `guess-${i}`);
    await button("Sign in").click();
    await expectText("Wrong username or password");
  }
  await page.fill("#password", PASSWORD);
  await button("Sign in").click();
  await expectText("Too many wrong passwords");
});

console.log(results.join("\n"));
console.log(`\n${results.filter((r) => r.startsWith("PASS")).length}/${results.length} passed`);
console.log(problems.length ? `Problems:\n${[...new Set(problems)].join("\n")}` : "No console errors or 5xx responses");
await browser.close();
