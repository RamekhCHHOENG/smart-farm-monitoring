# Run the ESP32 node in the Wokwi simulator

[Wokwi](https://wokwi.com) simulates an ESP32 in the browser and runs this exact sketch.
A slider stands in for the soil moisture sensor, and the simulated board sends real HTTP requests
to our backend. Wokwi is free for hobby and student use, but it is **not open source**.

What it proves: the firmware logic (Wi-Fi, HTTP, the soil calculation, the relay and LED).
What it does not prove: real sensor noise, a corroding probe, weak Wi-Fi or power problems.
For those you still need the real board (see [docs/HARDWARE.md](../../docs/HARDWARE.md)).

> These files were prepared without running Wokwi itself. They follow Wokwi's part names and
> settings; if Wokwi has changed one, its editor shows the problem in the `diagram.json` tab.

## Files in this folder

| File | Used for |
|---|---|
| `esp32-node.ino` | The sketch, the same one that runs on a real board |
| `diagram.json` | The virtual wiring: ESP32, slider on GPIO 34, DHT22 on GPIO 4, relay on GPIO 26, blue LED on GPIO 2 |
| `libraries.txt` | Libraries for Wokwi in the browser (DHT sensor library, Adafruit Unified Sensor) |
| `secrets.wokwi.h` | Settings for the simulator; it becomes `secrets.h` inside Wokwi |
| `wokwi.toml` | Settings for Wokwi for VS Code |

## Step 1. Prepare the farm (same as for a real board)

1. Start the backend with a fixed device key:
   `FARM_DEVICE_KEY=<a long random key> FARM_DEMO_PASSWORD=<a strong password> mvn spring-boot:run`
2. Technician: **Zones & devices → + Device**: a soil moisture sensor with **Simulated unticked**,
   optionally a temperature sensor (also unticked), and a water pump. Note their **#ids**.
3. Owner: **Automation rules → New rule**: soil below 30 %, run the pump 1 minute.

## Step 2, option A: Wokwi in the browser (nothing to install)

The simulated board reaches the internet, but not your laptop. A free tunnel gives your
backend a temporary public address:

```bash
# Cloudflare quick tunnel: no account needed (download "cloudflared" for your system)
cloudflared tunnel --url http://localhost:8080
# It prints an address like https://random-words.trycloudflare.com
```

Check it: open `https://<that address>/api/farm` in a browser. `{"title":"Unauthorized"...}`
means the tunnel works (you are not signed in, which is correct).

> **Security.** While the tunnel runs, anyone with the address can reach the API. Use a strong
> device key and strong passwords (not demo values), and stop the tunnel (Ctrl+C) after testing.
> The firmware encrypts https traffic but does not check the certificate unless you set
> `SERVER_ROOT_CA` in `secrets.h`; that is fine for a test, not for a real farm.

Then in Wokwi:

1. Open [wokwi.com](https://wokwi.com) → **New project** → **ESP32** (Arduino).
2. Replace the contents of `sketch.ino` with `esp32-node.ino`.
3. Replace the contents of the `diagram.json` tab with this folder's `diagram.json`.
4. Open **Library Manager** (or the `libraries.txt` tab) and add *DHT sensor library* and *Adafruit Unified Sensor*.
5. Add a new file named `secrets.h`, paste `secrets.wokwi.h`, and fill in:
   - `SERVER_URL`: the tunnel address from above (starting with `https://`, no `/` at the end)
   - `DEVICE_KEY`: the same value as `FARM_DEVICE_KEY`
   - `SOIL_SENSOR_ID`, `TEMP_SENSOR_ID`, `PUMP_ACTUATOR_ID`: the #ids from step 1 (0 = not used)
6. Press **Play**. Keep the project private if it contains your key, and delete the key from it afterwards.

## Step 2, option B: Wokwi for VS Code (talks to your laptop directly)

1. Install the **Wokwi Simulator** extension in VS Code and follow its licence prompt.
2. Build the firmware once (needs [arduino-cli](https://arduino.github.io/arduino-cli/) with the `esp32` core and the two libraries):
   ```bash
   cp secrets.wokwi.h secrets.h          # secrets.h is git-ignored
   # in secrets.h set: SERVER_URL "http://host.wokwi.internal:8080", the device key and the ids
   arduino-cli compile --fqbn esp32:esp32:esp32 --output-dir build .
   ```
3. Open this folder in VS Code, press F1 → **Wokwi: Start Simulator**.

`host.wokwi.internal` is how the simulator reaches your computer through Wokwi's private gateway.
Check the current Wokwi documentation if the address or the licence terms have changed.

## Step 3. Test it

| Do this | Expected |
|---|---|
| Watch the Serial Monitor | `Connecting to Wokwi-GUEST... connected`, then `Sensor N = ... -> HTTP 201` every 10 s |
| Open the dashboard | The sensor shows the slider's value, a green dot and "Device" |
| Slide to the right (wet) | Above 80 %, no alert |
| Slide to the left (dry), below 30 % | Pump RUNNING on the dashboard, a WARNING alert, the blue LED and the relay switch on within 3 s |
| Wait 1 minute | The pump stops, LED and relay off; History shows "activated" and "deactivated" |
| Press Stop in Wokwi for 2 minutes | The sensor dot turns red and an "is offline" alert appears |

## Troubleshooting

| Serial Monitor shows | Meaning |
|---|---|
| `HTTP -1` | The server cannot be reached: check `SERVER_URL`, that the backend and the tunnel are running |
| `HTTP 403 Invalid device key` | `DEVICE_KEY` differs from `FARM_DEVICE_KEY` |
| `HTTP 404` | A sensor or pump id does not exist; check the #ids |
| `HTTP 422` | The value is outside the sensor's range (for example a temperature sensor id in `SOIL_SENSOR_ID`) |
| Values jump around on the dashboard | The sensor is still "Simulated"; register one with Simulated unticked |

No Wokwi either? The app has a **Virtual ESP32** page (Technician and Owner menu): a slider and a
pump LED in the browser that use the same device API and device key.
