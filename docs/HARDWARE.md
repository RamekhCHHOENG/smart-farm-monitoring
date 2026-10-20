# Hardware guide: do you need to buy a kit?

**Short answer: not to pass the course.** The system runs completely on the built-in
simulator. Every use case, the automation loop, alerts, and the state machine can be
demonstrated without any hardware. Buy a small kit only if you want a live demo with a
real plant. One sensor node is enough; the other zones stay simulated.

## How hardware fits the design

```
Real sensor ──► ESP32 ──HTTP──► ReadingController ──► MonitoringService ──► rules / actuators / alerts
                  ▲                                                             │
                  └──────────── polls /actuators/{id}/state ◄──────────────────┘ (relay on/off)
```

The simulator and the ESP32 use the **same** endpoint (`POST /api/devices/readings`),
so the software does not change when you switch from simulated to real sensors.
That is the Sensor Device actor from Deliverable 2, and polymorphism at system level.

## Phase 0 - no purchase (recommended first)

Use the simulator (`FARM_SIMULATOR_ENABLED=true`, the default). Demo script:

1. Log in as **owner**: the dashboard shows live readings.
2. Wait until soil moisture drops below 30 %: the pump starts automatically and an alert appears.
3. Log in as **worker**: acknowledge the alert, start or stop the fan manually.
4. Log in as **technician**: report a fault on the fan, then try to start it as worker (refused, 409),
   start repair, complete repair. This shows the state machine.
5. Open **History**: chart with the threshold line, and the device log.

## Phase 1 - one real node (about USD 15-30)

| Part | Why | Rough price* |
|---|---|---|
| ESP32 DevKit (ESP32-WROOM-32, USB) | Wi-Fi microcontroller | $4-8 |
| Capacitive soil moisture sensor v1.2 | Soil moisture. **Avoid the cheap resistive (fork) type: it corrodes in days** | $1-3 |
| DHT22 (AM2302) module | Temperature (and humidity) | $2-5 |
| 1-channel 5 V relay module (with optocoupler) | Switches the pump | $1-2 |
| Mini 5 V DC submersible pump + 1 m tube | Real watering | $2-4 |
| Breadboard + jumper wires (M-M, M-F) | Wiring without soldering | $2-4 |
| USB cable (data, not charge-only) + 5 V phone charger | Power and programming | often already owned |

\*Rough estimates from common online marketplaces in 2025-2026. Local electronics shops may
cost more but you get the parts the same day. **Check prices before buying.**

Optional later: HC-SR04 ultrasonic sensor for feed level (~$1-2), small 5 V fan (~$2).

### Wiring

| Part | Pin | ESP32 |
|---|---|---|
| Soil sensor | AOUT / VCC / GND | GPIO 34 / 3V3 / GND |
| DHT22 | DATA / VCC / GND | GPIO 4 (10 kΩ pull-up to 3V3, many modules have it) / 3V3 / GND |
| Relay | IN / VCC / GND | GPIO 26 / VIN (5 V) / GND |
| Pump | via relay COM/NO | separate 5 V supply |

### Safety rules (important)

- **Never connect a relay to 220 V mains** for this project. Use only a 5-12 V DC pump.
- Keep the ESP32 and breadboard away from water; put the pump in a cup or bucket.
- The firmware turns the relay **off** when Wi-Fi or the server is lost (fail-safe), and the
  server stops every run after its duration (safety limit from the state machine).

### Setup steps

1. Install the Arduino IDE, add the ESP32 board package, and install the libraries
   "DHT sensor library" (Adafruit) and "Adafruit Unified Sensor".
2. Start the backend with a device key: `FARM_DEVICE_KEY=<random value>` (see `.env.example`).
3. Log in as **technician** and register two sensors in a zone with **Simulated unticked**,
   plus a water pump. Note their `#id` numbers.
4. Copy `firmware/esp32-node/secrets.example.h` to `secrets.h` and fill in Wi-Fi, server
   address (your computer's LAN IP, e.g. `http://192.168.1.20:8080`), device key and ids.
   `secrets.h` is git-ignored: **never commit it.**
5. Upload `esp32-node.ino`, open the Serial Monitor (115200 baud) and watch `HTTP 201`.
6. Calibrate the soil sensor: note the raw value in dry air and in water, then set
   `SOIL_RAW_DRY` / `SOIL_RAW_WET`.

## Decision summary

| Option | Cost | Risk | Demo value |
|---|---|---|---|
| Simulator only | $0 | none | Covers every requirement; values move realistically |
| Simulator + 1 ESP32 node | ~$15-30 | low (5 V only) | "Real" moment: dry the soil, the pump starts |
| Full farm kit per zone | $100+ | wiring, waterproofing, time | Little extra value for an OOAD course |

**Recommendation:** finish and demo with the simulator first. If you have a week to spare
and the group agrees, buy one Phase 1 kit (split between five people, it is a few dollars each).
