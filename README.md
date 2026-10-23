# Smart Farm Monitoring System

An Agri-IoT platform that monitors crop and poultry zones, runs pumps, fans and feeders
automatically when readings cross a threshold, and alerts farm staff.
Final project for **Object-Oriented Analysis & Design**: the code implements the design
from the midterm report (use cases, class diagram, sequence, activity and state machine).

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Data JPA, H2 (dev) / PostgreSQL |
| Frontend | Nuxt 4 (Vue 3), Tailwind CSS 4, [designkit](https://github.com/RamekhCHHOENG/designkit) shadcn-vue components |
| Devices | Built-in simulator, or an ESP32 node ([firmware](firmware/esp32-node)) |

## What staff can do

| Who | Main screens |
|---|---|
| Farm Worker | Live dashboard (tap a sensor for its history), start/stop pumps, fans and feeders, acknowledge alerts |
| Technician | Everything a worker can, plus register, rename and repair devices (fault → repair state machine) |
| Farm Owner | Everything, plus zones, automation rules, schedules, staff accounts (add, change role, reset password) |

For people working in the field:
- **Khmer / English** switch for the daily screens (menus, dashboard, alerts, history). Setup forms and
  messages from the server stay in English. The Khmer wording should be checked by a native speaker.
- **Phone layout** with a bottom tab bar (Home, Alerts, History, Zones, More).
- **Critical alerts** show a red banner on every page; with *Alert sound* on, they also beep and show a
  system pop-up. The same problem happening again is counted on one alert ("×3") instead of flooding the list.
- **History**: quick ranges (1 h, 24 h, 7 days, 30 days) and a **CSV download** of every reading for Excel.

## Architecture

```
Browser (Nuxt) ──/api──► Nuxt server proxy ──► Spring Boot REST API ──► JPA ──► H2 / PostgreSQL
                                                    ▲        │
ESP32 or simulator ──POST /api/devices/readings─────┘        └──► NotificationService (console / Telegram)
```

```
backend/src/main/java/com/smartfarm
├── domain/        Entities: Farm, Zone (CropZone, PoultryZone), Sensor (3 types), Actuator (3 types),
│                  SensorReading, AutomationRule, ScheduledTask, Alert, DeviceLog, AppUser
├── service/       MonitoringService, ActuatorService, RuleService, TaskService, AlertService, ...
├── notification/  NotificationService interface + ConsoleNotifier, TelegramNotifier
├── web/           REST controllers, DTOs, login interceptor, error handling
├── simulation/    DeviceSimulator (plays the Sensor Device actor)
└── config/        Wiring, device key, demo data
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
The simulator starts sending readings right away. Each user can change their password with the key icon.

### Real installation (your own farm)

Start the backend once with `FARM_DEMO_DATA=false`, a fixed `FARM_DEVICE_KEY` and `FARM_TIME_ZONE=Asia/Phnom_Penh`
(schedules run on this clock, which matters on a cloud server set to UTC) to get an empty farm.
Then follow **[docs/HARDWARE.md](docs/HARDWARE.md)**: name the farm, add a zone, register a real sensor,
and test it with `scripts/fake-device.mjs` (no hardware) or an ESP32 (about USD 6-12 minimal kit).

### Tests

```bash
cd backend && mvn test          # 30 tests: domain rules, state machine, API flows, permissions, real-device setup,
                                #           accounts, lockout, editing, CSV export, alert grouping
cd frontend && npm run typecheck
# Browser walkthrough of every use case as owner, technician and worker (32 steps), see the header of:
scripts/e2e-walkthrough.mjs
```

## How the design maps to the code

| Design element | Where in the code |
|---|---|
| UC-3 Submit Sensor Reading (sequence diagram) | `ReadingController.submitReading` → `MonitoringService.processReading` |
| Evaluate Rules «include» / Send Alert «extend» | loop over `AutomationRule.isTriggered` → `AlertService.raise` |
| Actuator state machine (D9) | `Actuator.activate / deactivate / reportFault / heartbeatLost / startRepair / completeRepair / decommission` |
| Automated monitoring workflow (D8) | valid? → `Sensor.isValid`; breached? → rule; fork → actuator + alert |
| Inheritance + polymorphism | `Zone.createDefaultRules()`, `Sensor.simulateNext()`, `Actuator.getActionVerb()` |
| Composition | `Farm ◆ Zone ◆ Sensor/Actuator` with cascade delete |
| GRASP Information Expert | `AutomationRule.isTriggered`, `Sensor.isValid` |
| GRASP Creator | `Farm.addCropZone`, `Zone.addSensor`, `Sensor.record` creates `SensorReading` |
| GRASP Controller | `ReadingController` (devices), REST controllers (users) |
| SRP | one job per class: rule decides, actuator acts, repository stores, notifier delivers |
| OCP | new zone/sensor types are new subclasses; services work with the abstract types |
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

## User manual

Step-by-step guide for owners, workers and technicians, with screenshots of every screen:
[docs/manual/USER_MANUAL.md](docs/manual/USER_MANUAL.md).

## Hardware

Not required for the demo. To test with a real sensor, the minimal kit is an ESP32 DevKit,
a capacitive soil moisture sensor and a USB data cable (about USD 6-12); the ESP32's built-in LED
stands in for the pump. See [docs/HARDWARE.md](docs/HARDWARE.md) for the full kit, wiring,
safety rules, and a step-by-step acceptance test.

## Credits

UI components from [designkit](https://github.com/RamekhCHHOENG/designkit) (MIT).
Group 1, OOAD course, 2026.
