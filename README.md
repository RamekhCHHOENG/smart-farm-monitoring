# Smart Farm Monitoring System

An Agri-IoT platform that monitors crop and poultry zones, runs pumps, fans and feeders
automatically when readings cross a threshold, and alerts farm staff.
Final project for **Object-Oriented Analysis & Design**: the code implements the design
from the midterm report (use cases, class diagram, sequence, activity and state machine).

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Data JPA, H2 (dev) / PostgreSQL |
| Frontend | Nuxt 4 (Vue 3), Tailwind CSS 4, [designkit](https://github.com/RamekhCHHOENG/designkit) shadcn-vue components |
| Devices | Built-in simulator, Farm Lab (virtual devices + scenarios), Wokwi simulator, or a real ESP32 node ([firmware](firmware/esp32-node)) |

## What staff can do

| Who | Main screens |
|---|---|
| Farm Worker | Live dashboard (tap a sensor for its history), start/stop pumps, fans and feeders, acknowledge alerts |
| Technician | Everything a worker can, plus register, rename and repair devices: actuators (fault → repair state machine) and sensors (fault → repair → test with a real reading) |
| Farm Owner | Everything, plus zones, automation rules, schedules, staff accounts (add, change role, reset password) |

For people working in the field:
- **Khmer / English** switch for the daily screens (menus, dashboard, alerts, history). Setup forms and
  messages from the server stay in English. The Khmer wording should be checked by a native speaker.
- **Phone layout** with a bottom tab bar (Home, Alerts, History, Zones, More).
- **Critical alerts** show a red banner on every page; with *Alert sound* on, they also beep and show a
  system pop-up. The same problem happening again is counted on one alert ("×3") instead of flooding the list.
- **History**: quick ranges (1 h, 24 h, 7 days, 30 days) and a **CSV download** of every reading for Excel.

## Device kinds

A zone can hold any mix of these. Each kind is one small class (Open/Closed Principle), so the services,
screens and Farm Lab handle a new kind without special cases.

| Sensors | Unit | Range | Usually drives |
|---|---|---|---|
| Soil moisture | % | 0-100 | Water pump |
| Temperature | °C | -10-60 | Fan (hot) or Heater (cold) |
| Feed level | % | 0-100 | Feeder |
| Air humidity | % | 0-100 | Mister (dry) or Fan (damp) |
| Light level | lux | 0-100000 | Light |
| Water tank level | % | 0-100 | Tank fill valve |

| Actuators | Safety limit (default) | Action |
|---|---|---|
| Water pump | 10 min | Watering |
| Fan | 30 min | Cooling |
| Feeder | 5 min | Feeding |
| Heater | 30 min | Heating |
| Mister | 5 min | Misting |
| Light | 120 min | Lighting (also good on a schedule) |
| Tank fill valve | 15 min | Refilling |

**Default rules** add the matching rule whenever a zone has both the sensor and the actuator, for example
*Cold house* (temperature below 18 °C starts the heater) or *Low tank* (below 20 % opens the valve).

### A broken sensor can be repaired

A technician can take a sensor out of service and bring it back, like an actuator:

```
ONLINE ──silent 2 min──► OFFLINE ──valid reading──► ONLINE
ONLINE / OFFLINE ──Report fault──► FAULT ──Start repair──► MAINTENANCE ──Complete repair──► ONLINE
```

While a sensor is **FAULT** its readings are ignored (HTTP 202) and its rules are paused, so a stuck
probe cannot run a pump all night. In **MAINTENANCE** readings are stored so the technician can watch
them, but rules stay paused. **Complete repair** is a real test: it only succeeds if a valid reading
arrived after the repair began.

## Architecture

```
Browser (Nuxt) ──/api──► Nuxt server proxy ──► Spring Boot REST API ──► JPA ──► H2 / PostgreSQL
                                                    ▲        │
ESP32 or simulator ──POST /api/devices/readings─────┘        └──► NotificationService (console / Telegram)
```

```
backend/src/main/java/com/smartfarm
├── domain/        Entities: Farm, Zone (CropZone, PoultryZone), Sensor (6 kinds), Actuator (7 kinds),
│                  SensorReading, AutomationRule, ScheduledTask, Alert, DeviceLog, AppUser
├── service/       MonitoringService, ActuatorService, SensorService, RuleService, TaskService, AlertService, ...
├── notification/  NotificationService interface + ConsoleNotifier, TelegramNotifier
├── web/           REST controllers, DTOs, login interceptor, error handling
├── simulation/    DeviceSimulator (plays the Sensor Device actor)
└── config/        Wiring, device key, demo data, SchemaUpgrade (lets an older database accept new device kinds)
frontend/app       pages/ (dashboard, zones, rules, schedules, alerts, history, users), components/,
                   utils/i18n.ts (Khmer / English), composables/useCriticalAlerts.ts
firmware/          ESP32 sensor node (works with soil sensor only; DHT22 and relay optional)
scripts/           fake-device.mjs (ESP32 stand-in), e2e-walkthrough.mjs (browser test)
docs/              Hardware guide, PlantUML diagram sources
```

## Run it locally

Requirements: JDK 21+, Maven 3.9+, Node 20+.

```bash
# 1. Backend  (http://localhost:8080)
cd backend
mvn spring-boot:run
```

On first start the server creates a demo farm and prints two lines in the log:
the **demo password** for users `owner`, `worker`, `technician`, and a temporary **device key**.
To choose them yourself, set `FARM_DEMO_PASSWORD` and `FARM_DEVICE_KEY` (see [.env.example](.env.example)).

```bash
# 2. Frontend (http://localhost:3000)
cd frontend
npm install
npm run dev
```

Open http://localhost:3000 and sign in as `owner` with the demo password.
An existing database from an older version is upgraded when the server starts (nothing is deleted): it only learns to accept the newer device kinds.
The simulator starts sending readings right away. Each user can change their password with the key icon.

### Real installation (your own farm)

Start the backend once with `FARM_DEMO_DATA=false`, a fixed `FARM_DEVICE_KEY` and `FARM_TIME_ZONE=Asia/Phnom_Penh`
(schedules run on this clock, which matters on a cloud server set to UTC) to get an empty farm.
Then follow **[docs/HARDWARE.md](docs/HARDWARE.md)**: name the farm, add a zone, register a real sensor,
and test it with `scripts/fake-device.mjs` (no hardware) or an ESP32 (about USD 6-12 minimal kit).

### Tests

```bash
cd backend && mvn test          # 51 tests: domain rules, actuator and sensor state machines, new device kinds, API flows,
                                #           permissions, real-device setup, accounts, lockout, editing, CSV export,
                                #           alert grouping, pool reconnect, upgrade of an older database
cd frontend && npm run typecheck
# Browser walkthrough of every use case as owner, technician and worker (34 steps), see the header of:
scripts/e2e-walkthrough.mjs
```

## How the design maps to the code

| Design element | Where in the code |
|---|---|
| UC-3 Submit Sensor Reading (sequence diagram) | `ReadingController.submitReading` → `MonitoringService.processReading` |
| Evaluate Rules «include» / Send Alert «extend» | loop over `AutomationRule.isTriggered` → `AlertService.raise` |
| Actuator state machine (D9) | `Actuator.activate / deactivate / reportFault / heartbeatLost / startRepair / completeRepair / decommission` |
| Sensor state machine (docs/diagrams/08) | `Sensor.reportFault / startRepair / completeRepair`, status derived in `Sensor.getStatus`, driven by `SensorService.lifecycle` |
| Automated monitoring workflow (D8) | valid? → `Sensor.isValid`; breached? → rule; fork → actuator + alert |
| Inheritance + polymorphism | `Zone.createDefaultRules()`, `Sensor.simulateNext()`, `Actuator.getActionVerb()` |
| Composition | `Farm ◆ Zone ◆ Sensor/Actuator` with cascade delete |
| GRASP Information Expert | `AutomationRule.isTriggered`, `Sensor.isValid` |
| GRASP Creator | `Farm.addCropZone`, `Zone.addSensor`, `Sensor.record` creates `SensorReading` |
| GRASP Controller | `ReadingController` (devices), REST controllers (users) |
| SRP | one job per class: rule decides, actuator acts, repository stores, notifier delivers |
| OCP | new zone/sensor/actuator kinds are new subclasses (the heater, mister, light, tank valve and humidity, light, tank sensors were added this way); services work with the abstract types |
| DIP | `MonitoringService` → `AlertService` → `NotificationService` interface; wiring in `AppConfig` |
| Permissions table (D2) | `AccessPolicy.require(...)` in every write service method |

## Security notes

- **No secrets in the repository.** The device key, demo password, Telegram token and database
  password come only from environment variables. `.env`, local databases and the ESP32 `secrets.h`
  are git-ignored. If a value is missing, a random one is generated at startup.
- Users sign in with a password (BCrypt hash) and get a random bearer token (12 h).
  Each write action checks the user's role on the server, not only in the UI.
- After 5 wrong passwords a username is locked for 15 minutes (the owner's password reset unlocks it).
  Trade-off: someone who knows a username can lock it for a while; the lock is short and per username.
- The owner manages staff accounts; a password reset or removal signs that person out everywhere.
- Raw readings older than 90 days are deleted (`FARM_READING_RETENTION_DAYS`); alerts and device logs are kept.
- Devices authenticate with the `X-Device-Key` header (constant-time comparison).
- Limitations (fine for a class project, fix before real use): tokens and login lockouts are kept
  in memory (restart = log in again), and the ESP32 talks plain HTTP on the local network.
  Put the server behind HTTPS before exposing it to the internet.

## Farm Lab: test every farm scenario without hardware

The **Farm Lab** page creates as many virtual sensors (soil moisture, temperature, feed level, humidity, light
level, tank level) and actuators (pump, fan, feeder, heater, mister, light, tank valve) as you like. They talk to the server through the device API with the
device key, exactly like real ESP32 boards, and behave like a small farm: soil dries and the pump
wets it, heat rises and the fan cools it (the heater warms it), chickens eat and the feeder refills, the mister raises
the humidity, the lamp brightens the greenhouse and the valve fills the tank. Each lab sensor also shows what the
server thinks of it, with the same repair buttons as the Zones page.

Sixteen scenarios check how the whole system reacts, each with an automatic PASS / FAIL:

| Scenario | Expected reaction |
|---|---|
| Normal day | Nothing starts, no alert |
| Dry spell | Pump starts below 30 % |
| Heavy rain | Pump stays off |
| Heat wave | Fan starts above 32 °C |
| Cool night | Fan stays off |
| Cold snap | Heater starts below 18 °C |
| Dry air | Mister starts below 40 % humidity |
| Dark day | Light switches on below 5000 lux |
| Tank running low | Tank valve opens below 20 % |
| Feed running out | Feeder refills below 20 % |
| Broken pump | CRITICAL alert: the pump could NOT start |
| Clogged pipe | **Known gap**: the pump runs, no water arrives, and nothing says so (needs a flow sensor) |
| Sensor cable cut | "is offline" alert after about 2 minutes |
| Faulty sensor reading | 150 % rejected with HTTP 422 |
| Sensor repaired | Pump stays off while the sensor is faulty or in repair, and starts once the repaired sensor is back |
| Power cut | "is offline" alert for every sensor; relays fail safe |

Run them in the browser (**Farm Lab → Create lab farm → Power on → Run**) or from a terminal:

```bash
FARM_DEVICE_KEY=<key> FARM_PASSWORD=<owner password> node scripts/farm-scenarios.mjs        # all, about 30 min
FARM_DEVICE_KEY=<key> FARM_PASSWORD=<owner password> node scripts/farm-scenarios.mjs --only drought,heatwave
```

## Demo video

`scripts/record-demo.mjs` records a start-to-finish demo with captions (Playwright), and
`scripts/make-demo-video.mjs` joins the scenes into an MP4 with the voice-over mixed in.
The voice is [Kokoro-82M](https://github.com/hexgrad/kokoro) (Apache 2.0), an open neural voice
run locally by `scripts/tts_server.py` (`pip install kokoro-onnx soundfile`, `apt install espeak-ng`);
without it, set `VOICE=mb-us1` for the espeak-ng voice or leave both unset for captions only.

### OOP walkthrough video

`scripts/record-oop-video.mjs` records a second video for the course: one chapter per deliverable
(D1-D11), each showing the concept, the diagram, the real Java code that implements it (read from
the source files at recording time, with real line numbers) and the running app. It covers the four
OOP pillars, GRASP and SOLID. Render the diagrams first (see the script's header), then join the
scenes with `scripts/make-demo-video.mjs`.

## Slides

The presentation that goes with the OOP walkthrough video: each deliverable as concept, diagram, real
code (file and line numbers) and running app, plus the new device kinds, sensor repair and thermostat rules.
[docs/slides/Smart-Farm-OOAD.pdf](docs/slides/Smart-Farm-OOAD.pdf) (40 pages), or open
[docs/slides/index.html](docs/slides/index.html) in a browser and use the arrow keys.
Speaker notes: [docs/slides/speaker-notes.md](docs/slides/speaker-notes.md).

## User manual

Step-by-step guide for owners, workers and technicians, with screenshots of every screen:
[docs/manual/USER_MANUAL.md](docs/manual/USER_MANUAL.md).

## Hardware

Not required for the demo. Without a board you can use the **Farm Lab** (Technician and Owner menu)
or run our real firmware in the [Wokwi simulator](firmware/esp32-node/WOKWI.md). To test with a real sensor, the minimal kit is an ESP32 DevKit,
a capacitive soil moisture sensor and a USB data cable (about USD 6-12); the ESP32's built-in LED
stands in for the pump. See [docs/HARDWARE.md](docs/HARDWARE.md) for the full kit, wiring,
safety rules, and a step-by-step acceptance test.

## Credits

UI components from [designkit](https://github.com/RamekhCHHOENG/designkit) (MIT).
Group 1, OOAD course, 2026.
