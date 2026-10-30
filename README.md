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

## Company model: one system, many customer farms

Smart Farm is run by a company that sets up farms for its customers. One server holds every customer's farm, and
**each farm sees only its own data**: zones, devices, readings, rules, schedules, alerts, controller boards and people.
Asking for another farm's item by its id answers "not found" (tests in `CompanyFlowTest`).

| Account | Belongs to | Works as |
|---|---|---|
| Company admin | The company | Creates farms, invites owners, manages company staff; opens any farm as its owner |
| Company support | The company | Opens the farms assigned to it, as a technician, while the owner allows support |
| Farm owner / technician / worker | One farm | As before, on their own farm only |

- **New customer:** Customer farms → *New farm* (farm, owner's name, username and phone) gives a one-time
  **invite link** (7 days) to send by Telegram or SMS; the owner sets their own password. No shared passwords.
- **Working on a farm:** company staff press *Open farm*; every request then carries `X-Farm-Id` and a bar shows
  which farm they are on. The server decides the role there (`FarmAccessResolver`).
- **Owner's consent:** Zones & devices → Farm settings → *Allow Smart Farm support to open this farm*.
- **Suspend:** a suspended farm keeps its data and keeps running its rules, but its people cannot use it.
- **First start:** the company admin account `admin` is created (password `FARM_ADMIN_PASSWORD`, else
  `FARM_DEMO_PASSWORD`). Older databases are upgraded on start: alerts and boards are given their farm.
- **Device keys:** each controller board has its own key and drives only its own devices. The server-wide
  `FARM_DEVICE_KEY` only reaches the original farm (the first one), so a leaked key cannot write into a customer's farm.
- **Catalog:** the shared catalog is kept by company staff. A farm's owner or technician adds models of their own,
  which only that farm sees.
- Still shared by all farms (next phase): the Telegram chat.

## Device catalog (master data)

Devices are not a fixed list in the code. The **Device catalog** page holds every model the farm can use, with
its picture, what it measures or does, wiring notes and a rough price. Company staff keep the shared catalog;
a farm's Owner and Technician add models of their own (only their farm sees them). A starter catalog of about 40 real models is filled on first start (DHT22, DS18B20, BH1750,
JSN-SR04T, HX711, MQ-135, MH-Z19B, pumps, 36-inch exhaust fans, brooder lamps, solenoid valves, ESP32 boards...).

| Level | What it is | Example |
|---|---|---|
| Measurement type | Something a sensor can read, with its unit and valid range | CO₂ in ppm (0-5000) |
| Device model | A real product: its readings, or its actuator kind, effects and safety limit | DHT22: temperature + humidity |
| Farm device | One physical unit in a zone, made from a model; its readings and outputs are what rules use | "House sensor 1", a DHT22 |
| Controller board | An ESP32 node with **its own key** (stored hashed, shown once) and generated `secrets.h` | "Chicken house node" |

- **Add device** picks a model from the catalog by its picture; a DHT22 becomes one device with two readings.
- New measurements (CO₂, pH, ammonia...) and new actuator kinds (a CO₂ vent, a siren) need **no code**:
  `GenericSensor` takes its range from the measurement type, `GenericActuator` its effects from the model.
- Pictures: an uploaded photo, else the model's open-source [Wokwi part](https://github.com/wokwi/wokwi-elements)
  drawing (MIT), else our animated drawing of the farm equipment.
- A board's key only drives the devices assigned to it; the server-wide device key keeps working for the original farm.
- Devices made before the catalog existed are given a matching model on start (ids and history are kept).

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
├── domain/        Entities: Farm, Zone (CropZone, PoultryZone), Sensor (6 kinds + GenericSensor), Actuator (7 kinds +
│                  GenericActuator), FarmDevice, ControllerBoard, catalog: MeasurementType, DeviceModel;
│                  SensorReading, AutomationRule, ScheduledTask, Alert, DeviceLog, AppUser
├── service/       MonitoringService, ActuatorService, SensorService, RuleService, CatalogService, FarmDeviceService,
│                  ControllerService, DeviceAccess (farm key or board key), LabService, AlertService, ...
├── notification/  NotificationService interface + ConsoleNotifier, TelegramNotifier
├── web/           REST controllers, DTOs, login interceptor, error handling
├── simulation/    DeviceSimulator (plays the Sensor Device actor)
└── config/        Wiring, device key, demo data, CatalogSeeder (starter catalog), DeviceUpgrade, SchemaUpgrade
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
cd backend && mvn test          # 73 tests: domain rules, actuator and sensor state machines, new device kinds, API flows,
                                #           permissions, real-device setup, accounts, lockout, editing, CSV export,
                                #           alert grouping, pool reconnect, upgrade of an older database, sign-ins kept over a restart
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
| UC-12 Manage Customer Farms, UC-13 Invite and Activate Account (docs/diagrams/01b, 09) | `CompanyService.createFarm` → `InviteService.create` (random token, only its SHA-256 stored); the owner's `InviteService.accept` uses the link once and starts a session |
| UC-14 Work on a Customer Farm «include» Check Farm Access (docs/diagrams/09) | `AuthInterceptor` → `FarmAccessResolver.resolve` (admin = owner, support = technician if assigned and allowed) → `Tenancy` |
| One farm never sees another's data (docs/diagrams/03b) | every service lookup goes through `Tenancy.zone / sensor / actuator ...`; another farm's id answers 404 |
| UC-16 Allow Support Access | `Farm.allowSupport` (Farm settings in the app) |
| Signed in across a restart | `UserSession` rows keep only a token hash (12 hours); see `SessionPersistenceTest` |

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
- Sign-ins are stored in the database as SHA-256 hashes of the token (never the token itself), so a server
  restart does not log anyone out; expired sessions are cleared every hour.
- Limitations (fine for a class project, fix before real use): login lockouts are kept in memory
  (a restart clears them), and the ESP32 talks plain HTTP on the local network.
  Put the server behind HTTPS before exposing it to the internet.

## Farm Lab: test every farm scenario without hardware

The **Farm Lab** page creates as many virtual sensors (soil moisture, temperature, feed level, humidity, light
level, tank level) and actuators (pump, fan, feeder, heater, mister, light, tank valve) as you like. They talk to the server through the device API with the
device key, exactly like real ESP32 boards, and behave like a small farm: soil dries and the pump
wets it, heat rises and the fan cools it (the heater warms it), chickens eat and the feeder refills, the mister raises
the humidity, the lamp brightens the greenhouse and the valve fills the tank. Each lab sensor also shows what the
server thinks of it, with the same repair buttons as the Zones page.

Seventeen scenarios check how the whole system reacts, each with an automatic PASS / FAIL:

| Scenario | Expected reaction |
|---|---|
| Normal day | Nothing starts, no alert |
| Dry spell | Pump starts below 30 % |
| Heavy rain | Pump stays off |
| Heat wave | Fan starts above 32 °C |
| Cool night | Fan stays off |
| Cold snap | Heater starts below 18 °C |
| Thermostat | Fan stops by itself at 30 °C, long before its time limit; the heater never starts |
| Dry air | Mister starts below 40 % humidity |
| Dark day | Light switches on below 5000 lux |
| Tank running low | Tank valve opens below 20 % |
| Feed running out | Feeder refills below 20 % |
| Broken pump | CRITICAL alert: the pump could NOT start |
| Clogged pipe | When the run is over, an alert says the soil did not rise: check the pump and the pipe |
| Sensor cable cut | "is offline" alert after about 2 minutes |
| Faulty sensor reading | 150 % rejected with HTTP 422 |
| Sensor repaired | Pump stays off while the sensor is faulty or in repair, and starts once the repaired sensor is back |
| Power cut | "is offline" alert for every sensor; relays fail safe |

Lab zones are marked as such: their devices are driven with your sign-in (no device key), their alerts stay
in the Farm Lab (never sent to Telegram, not counted on the dashboard), and the dashboard hides them unless
you tick *Show Farm Lab test zones*. Every device is drawn as the physical thing (pot, thermometer, pump with
flowing water, spinning fan, glowing heater...), on the Farm Lab and on the Devices page's picture view.

Every rule's run is also checked when its time is up: if the reading did not move the way it should (soil did
not rise while the pump ran), an alert says what to check. That is how the clogged pipe is caught.

Run them in the browser (**Farm Lab → Create lab farm → Power on → Run**) or from a terminal:

```bash
FARM_PASSWORD=<owner password> node scripts/farm-scenarios.mjs        # all, about 30 min
FARM_PASSWORD=<owner password> node scripts/farm-scenarios.mjs --only drought,heatwave
```

## Demo video

`scripts/record-demo.mjs` records a start-to-finish demo with captions (Playwright), and
`scripts/make-demo-video.mjs` joins the scenes into an MP4 with the voice-over mixed in.
The voice is [Kokoro-82M](https://github.com/hexgrad/kokoro) (Apache 2.0), an open neural voice
run locally by `scripts/tts_server.py` (`pip install kokoro-onnx soundfile`, `apt install espeak-ng`);
without it, set `VOICE=mb-us1` for the espeak-ng voice or leave both unset for captions only.

### OOAD walkthrough video

`scripts/record-ooad-video.mjs` records a second video for the course: one chapter per deliverable
(D1-D11), each showing the concept, the diagram, the real Java code that implements it (read from
the source files at recording time, with real line numbers) and the running app. It covers the four
OO principles (encapsulation, abstraction, inheritance, polymorphism), GRASP and SOLID. Render the diagrams first (see the script's header), then join the
scenes with `scripts/make-demo-video.mjs`.

### Video for farm users

`scripts/record-user-video.mjs` records a plain-language video with no code (about 5 minutes), following the
farm-users slides: the dashboard and a zone, dry soil that starts the pump by itself, the alert, rules like a
thermostat, schedules, a broken sensor and its repair, history, a worker on a phone in Khmer, and the Farm Lab.
On a Mac with no Kokoro model, `SAY=Samantha` uses the built-in macOS voice. `BASE` and `API` point it at other
ports. Join the scenes with `scripts/make-demo-video.mjs`.

## Slides

Two decks, same look and screenshots. Open the HTML in a browser and use the arrow keys, or use the PDF.

- **Technical (OOAD)**, goes with the OOP walkthrough video: each deliverable as concept, diagram, real code
  (file and line numbers) and running app, then sensor repair, thermostat rules, the device catalog and many farms
  on one server. The analysis and design chapters include the company model (actors, use cases UC-12 to UC-16,
  domain model, company class diagram, invite and access sequence). [index.html](docs/slides/index.html) ·
  [Smart-Farm-OOAD.pdf](docs/slides/Smart-Farm-OOAD.pdf) (50 pages) ·
  [speaker notes](docs/slides/speaker-notes.md)
- **For farm users**, no code or diagrams: what the system does for owners, workers and technicians, screen by screen.
  [farm-users.html](docs/slides/farm-users.html) · [Smart-Farm-Guide.pdf](docs/slides/Smart-Farm-Guide.pdf) (21 pages) ·
  [speaker notes](docs/slides/speaker-notes-farm-users.md)

## User manual

Step-by-step guide for owners, workers, technicians and Smart Farm company staff (customer farms, invite links,
support access), with screenshots of every screen: [docs/manual/USER_MANUAL.md](docs/manual/USER_MANUAL.md).

## User acceptance testing

[docs/UAT.md](docs/UAT.md) shows how to run the app, seed three customer farms with `scripts/seed-uat.py` (random
owner passwords in a git-ignored file) and give testers a public link through a Cloudflare quick tunnel.

## Hardware

Not required for the demo. Without a board you can use the **Farm Lab** (Technician and Owner menu)
or run our real firmware in the [Wokwi simulator](firmware/esp32-node/WOKWI.md). To test with a real sensor, the minimal kit is an ESP32 DevKit,
a capacitive soil moisture sensor and a USB data cable (about USD 6-12); the ESP32's built-in LED
stands in for the pump. See [docs/HARDWARE.md](docs/HARDWARE.md) for the full kit, wiring,
safety rules, and a step-by-step acceptance test.

## Credits

UI components from [designkit](https://github.com/RamekhCHHOENG/designkit) (MIT).
Group 1, OOAD course, 2026.
