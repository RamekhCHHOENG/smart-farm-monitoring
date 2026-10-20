# Hardware guide: what you need to test with real devices

**You do not need hardware to finish or demo the project.** The built-in simulator runs every
use case. When you want to prove the system works with a real sensor, start small:

| Level | You buy | About | What it proves |
|---|---|---|---|
| 0 - laptop only | nothing | $0 | The real-device API path: a script acts as the ESP32 |
| **1 - minimal real kit** (recommended) | ESP32 + capacitive soil sensor + USB cable + 3 jumper wires | **$6-12** | A real sensor in real soil drives the rule; the ESP32's built-in LED shows the "pump" |
| 2 - full kit | Level 1 + DHT22 + relay + 5 V mini pump + tube + breadboard | $15-30 total | Real watering: dry soil, the pump starts by itself |

Prices are rough estimates from common online marketplaces in 2025-2026. Local electronics shops
cost more but you get the parts the same day. **Check before buying.** Split across five
members, level 1 is about $2 each.

## Level 1 shopping list (minimal real kit)

| Qty | Part | Notes |
|---|---|---|
| 1 | **ESP32 DevKit V1** (ESP32-WROOM-32, 30 or 38 pins) | Has Wi-Fi and a blue built-in LED on GPIO 2 |
| 1 | **Capacitive soil moisture sensor v1.2** | Not the cheap two-fork resistive type: it corrodes in days |
| 1 | **Micro-USB or USB-C data cable** (match your board) | Must be a *data* cable; many charge-only cables do not work |
| 3 | Female-to-female jumper wires | The sensor usually comes with a 3-wire cable already |
| - | A cup of soil and a glass of water | For calibration and the demo |

Also needed, but you already have them: a laptop with the backend running, and a **2.4 GHz** Wi-Fi
network shared by the laptop and the ESP32 (a phone hotspot works; the ESP32 cannot join 5 GHz).

## Level 2 additions (real watering)

| Qty | Part | Notes |
|---|---|---|
| 1 | DHT22 (AM2302) module | Temperature; module version has the pull-up resistor built in |
| 1 | 1-channel 5 V relay module with optocoupler | Switches the pump |
| 1 | Mini 3-5 V submersible pump + 1 m tube | Small pumps draw about 100-200 mA |
| 1 | Breadboard + male-to-male and male-to-female jumpers | No soldering needed |

### Wiring

| Part | Pin | ESP32 |
|---|---|---|
| Soil sensor | AOUT / VCC / GND | GPIO 34 / 3V3 / GND |
| DHT22 (level 2) | DATA / VCC / GND | GPIO 4 / 3V3 / GND |
| Relay (level 2) | IN / VCC / GND | GPIO 26 / VIN (5 V) / GND |
| Pump (level 2) | + / - | through relay COM-NO from 5 V; - to GND |

### Safety rules

- **Never connect the relay to 220 V mains** in this project. Only 5 V DC.
- A mini pump may be powered from the ESP32's VIN (USB 5 V) only if it draws under ~200 mA;
  otherwise use a separate 5 V supply with a shared GND.
- Keep the ESP32 and breadboard away from water; put the pump in a cup.
- The firmware turns the pump **off** when Wi-Fi or the server is lost, and the server stops every
  run after its duration (the safety limit from the state machine).

## Step by step: add a farm, add a device, test for real

### A. Start the server for a real installation

```bash
# on the laptop, in the backend folder
export FARM_DEVICE_KEY=$(openssl rand -hex 24)   # Windows PowerShell: $env:FARM_DEVICE_KEY="choose-a-long-random-text"
export FARM_DEMO_DATA=false                       # start with an empty farm (no demo zones)
mvn spring-boot:run
```

Note the **demo password** printed in the log, then start the frontend (`npm run dev` in `frontend/`).
Find the laptop's LAN address (`ipconfig` on Windows, `ip addr` / `ifconfig` on Linux/macOS),
for example `192.168.1.20`. On Windows, allow Java through the firewall when asked (port 8080).

### B. Set up the farm in the web app

1. Sign in as **owner** → click the key icon → change the password.
2. **Zones & devices → Farm settings**: enter your farm name and location.
3. **New zone**: type *Crop zone*, name *Test bed*, crop *Basil*.
4. Sign out, sign in as **technician** → **+ Device** on *Test bed*:
   - Sensor → *Soil moisture* → name *ESP32 soil* → **untick "Simulated"** → Register.
   - Actuator → *Water pump* → name *Test pump* → safety limit *2* min → Register.
   - Write down the **#id** next to each device (for example soil #1, pump #1).
5. Sign in as **owner** → **Automation rules → New rule**: sensor *ESP32 soil*, *Below 30*,
   actuator *Test pump*, run *1* min, severity *Warning*.

### C. Level 0: test without hardware

```bash
FARM_DEVICE_KEY=<same key> node scripts/fake-device.mjs --sensor 1 --pump 1 --from 40
```

The script lowers the value by 2 % every 3 s. Below 30 % the dashboard shows the pump **RUNNING**,
an alert appears, and the script prints `pump 1: ON`.

### D. Level 1/2: test with the ESP32

1. Install the Arduino IDE → Boards Manager → install **esp32 by Espressif** → select **ESP32 Dev Module**.
   Library Manager → install **DHT sensor library** (Adafruit) and **Adafruit Unified Sensor**.
2. Copy `firmware/esp32-node/secrets.example.h` to `secrets.h` (same folder) and fill in:
   Wi-Fi name and password, `SERVER_URL` = `http://<laptop-ip>:8080`, `DEVICE_KEY`,
   `SOIL_SENSOR_ID` and `PUMP_ACTUATOR_ID` from step B4.
   Level 1: leave `TEMP_SENSOR_ID 0` and `USE_RELAY 0`. Level 2: set them.
   **Never commit `secrets.h`** (it is already in `.gitignore`).
3. Wire the soil sensor, plug in the USB cable, upload the sketch, open the Serial Monitor at 115200 baud.
4. Calibrate: note `Soil raw` in dry air and with the probe in water; put the two numbers in
   `SOIL_RAW_DRY` / `SOIL_RAW_WET` and upload again.

### E. Acceptance test (tick each line)

| # | Do this | Expected |
|---|---|---|
| 1 | Watch the Serial Monitor | `connected, IP ...` then `HTTP 201` every 10 s |
| 2 | Open the dashboard | *ESP32 soil* shows a value, a green dot and "Device" (not "Simulated") |
| 3 | Probe in a glass of water | Value rises above 80 %, no alert |
| 4 | Probe in dry air or dry soil | Below 30 %: pump **RUNNING**, built-in LED on (level 2: pump runs), WARNING alert |
| 5 | Wait for the run time | Pump back to **IDLE**, LED off, History shows *activated* and *deactivated* |
| 6 | Unplug the ESP32 for 2 minutes | Sensor dot turns red, alert "is offline" |
| 7 | Sign in as worker, start the pump for 1 min | LED turns on within 3 s |
| 8 | Stop the laptop backend | LED turns off (fail-safe) |

### Troubleshooting

| Serial Monitor shows | Meaning | Fix |
|---|---|---|
| `failed` while connecting | Wrong Wi-Fi name/password, or a 5 GHz network | Use a 2.4 GHz network or phone hotspot |
| `HTTP -1` | Server not reachable | Check `SERVER_URL` IP, same network, firewall allows port 8080 |
| `HTTP 403 Invalid device key` | Key mismatch | Use exactly the `FARM_DEVICE_KEY` the server was started with |
| `HTTP 404 Sensor N not found` | Wrong id | Use the #id shown on *Zones & devices* |
| `HTTP 422 ... outside the valid range` | Calibration off | Redo step D4 |
| Value never changes | Sensor on wrong pin | AOUT must be on GPIO 34 |
