#!/usr/bin/env node
// Runs the Farm Lab scenarios against a running backend, without a browser:
// virtual sensors and actuators talk to the server like real ESP32 boards, and each scenario
// (dry spell, heat wave, broken pump, power cut...) checks how the system reacts.
//
//   FARM_USER=owner FARM_PASSWORD=... node scripts/farm-scenarios.mjs
//   ... node scripts/farm-scenarios.mjs --only drought,heatwave     run some scenarios
//   node scripts/farm-scenarios.mjs --list                          list the scenarios
//
// The first run creates three "Lab" zones (bed, chicken house, greenhouse) with real (non-simulated)
// devices and rules. Lab zones are driven with the signed-in account (no device key), and their alerts
// stay in the lab (never sent to staff phones). A full run takes about 30 minutes (offline detection alone needs 2 minutes per scenario).
// Exit code 1 if a scenario fails; a known gap is reported but does not fail the run.
import process from "node:process";
import { FarmLab, SCENARIOS, ensureLabFarm } from "../frontend/app/utils/farmLab.ts";

const args = process.argv.slice(2);
if (args.includes("--list")) {
  for (const s of SCENARIOS) console.log(`${s.id.padEnd(17)} ${s.title}: ${s.expect}`);
  process.exit(0);
}

const BASE = process.env.FARM_URL ?? "http://localhost:8080";
const USER = process.env.FARM_USER ?? "owner";
const PASSWORD = process.env.FARM_PASSWORD;
if (!PASSWORD) {
  console.error("Set FARM_PASSWORD (the Farm Owner's password); FARM_URL defaults to http://localhost:8080");
  process.exit(2);
}

const login = await fetch(`${BASE}/api/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: USER, password: PASSWORD }),
});
if (!login.ok) {
  console.error(`Sign-in as ${USER} failed: HTTP ${login.status}`);
  process.exit(2);
}
const { token } = await login.json();

const api = async (method, path, body) => {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} -> HTTP ${res.status} ${text}`);
  return text ? JSON.parse(text) : null;
};

const time = () => new Date().toLocaleTimeString();
const io = {
  async sendReading(sensorId, value) {
    try {
      const res = await fetch(`${BASE}/api/lab/readings`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ sensorId, value }),
      });
      return res.status;
    } catch {
      return -1;
    }
  },
  async relayState(actuatorId) {
    try {
      const res = await fetch(`${BASE}/api/lab/actuators/${actuatorId}/state`, { headers: { Authorization: `Bearer ${token}` } });
      return res.ok ? (await res.json()).relayOn : null;
    } catch {
      return null;
    }
  },
  openAlerts: () => api("GET", "/api/alerts?status=OPEN&scope=LAB"),
  lifecycle: (id, action, body = {}) => api("POST", `/api/actuators/${id}/lifecycle/${action}`, body),
  sensorLifecycle: async (id, action) => (await api("POST", `/api/sensors/${id}/lifecycle/${action}`)).status,
  sleep: (ms) => new Promise((r) => setTimeout(r, ms)),
  log: (text) => console.log(`${time()}  ${text}`),
};

const lab = new FarmLab(io, Number(process.env.FARM_TICK_MS ?? 5000));
await ensureLabFarm(api, lab);
console.log(`Lab devices: ${lab.sensors.map((s) => `${s.name} #${s.id}`).join(", ")}; ${lab.actuators.map((a) => `${a.name} #${a.id}`).join(", ")}`);

const onlyArg = args.find((a) => a.startsWith("--only"));
const only = onlyArg ? (onlyArg.includes("=") ? onlyArg.split("=")[1] : args[args.indexOf(onlyArg) + 1]).split(",") : null;
const list = only ? SCENARIOS.filter((s) => only.includes(s.id)) : SCENARIOS;

// Start from a calm farm with every relay off
await lab.settle(true, 0);
const results = await lab.runAll(list);

console.log("\nScenario results");
for (const r of results) {
  const mark = { pass: "PASS", fail: "FAIL", gap: "GAP " }[r.verdict];
  console.log(`  ${mark}  ${r.title.padEnd(22)} ${String(r.seconds).padStart(4)} s  ${r.note}`);
}
const failed = results.filter((r) => r.verdict === "fail").length;
const gaps = results.filter((r) => r.verdict === "gap").length;
console.log(`\n${results.length - failed - gaps} passed, ${failed} failed, ${gaps} known gap(s)`);
process.exit(failed ? 1 : 0);
