#!/usr/bin/env node
// Pretends to be an ESP32: sends readings through the same device API and shows the pump state.
// Use it to test a "real" (non-simulated) sensor before you have hardware.
//
//   FARM_DEVICE_KEY=... node scripts/fake-device.mjs --sensor 1 --value 22.5          send one reading
//   FARM_DEVICE_KEY=... node scripts/fake-device.mjs --sensor 1 --pump 1 --from 45     soil dries until the pump runs
//
// Options: --server http://localhost:8080  --every 3 (seconds)
import process from "node:process";

const args = Object.fromEntries(
  process.argv.slice(2).reduce((pairs, arg, i, all) => (arg.startsWith("--") ? [...pairs, [arg.slice(2), all[i + 1]]] : pairs), []),
);
const server = args.server ?? "http://localhost:8080";
const key = process.env.FARM_DEVICE_KEY;
const sensorId = Number(args.sensor);
const pumpId = args.pump ? Number(args.pump) : null;

if (!key || !sensorId) {
  console.error("Usage: FARM_DEVICE_KEY=<key> node scripts/fake-device.mjs --sensor <id> [--value <v> | --from <start> --pump <id>]");
  process.exit(1);
}

const headers = { "Content-Type": "application/json", "X-Device-Key": key };

async function send(value) {
  const res = await fetch(`${server}/api/devices/readings`, { method: "POST", headers, body: JSON.stringify({ sensorId, value }) });
  const body = await res.json().catch(() => ({}));
  const outcome = res.ok ? `${body.status} ${(body.actions ?? []).join("; ")}` : (body.error ?? body.detail ?? "");
  console.log(`sensor ${sensorId} = ${value.toFixed(1)} -> HTTP ${res.status} ${outcome}`);
}

async function pumpOn() {
  if (!pumpId) return false;
  const res = await fetch(`${server}/api/devices/actuators/${pumpId}/state`, { headers });
  if (!res.ok) return false;
  const state = await res.json();
  return state.relayOn;
}

if (args.value !== undefined) {
  await send(Number(args.value));
} else {
  // Loop: soil dries by 2 % per step; while the pump runs it gets 6 % wetter.
  let value = Number(args.from ?? 45);
  const every = Number(args.every ?? 3) * 1000;
  console.log("Ctrl+C to stop");
  for (;;) {
    const on = await pumpOn();
    value = Math.min(100, Math.max(0, value + (on ? 6 : -2)));
    await send(value);
    if (pumpId) console.log(`  pump ${pumpId}: ${on ? "ON" : "off"}`);
    await new Promise((r) => setTimeout(r, every));
  }
}
