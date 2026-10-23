// Settings for the Wokwi simulator. In Wokwi, add a file named secrets.h with this content.
// Fill in SERVER_URL and DEVICE_KEY there; do not commit the filled-in copy.
#pragma once

// The simulator's built-in Wi-Fi network: no password, channel 6
#define WIFI_SSID "Wokwi-GUEST"
#define WIFI_PASSWORD ""
#define WIFI_CHANNEL 6

// Wokwi in the browser reaches the internet, not your laptop: use a tunnel address,
// e.g. "https://something.trycloudflare.com" (see WOKWI.md).
// Wokwi for VS Code can reach your laptop directly: "http://host.wokwi.internal:8080".
#define SERVER_URL "https://your-tunnel-address.trycloudflare.com"

// Must equal FARM_DEVICE_KEY on the server.
#define DEVICE_KEY "paste-the-device-key-here"

// Ids from the "Zones & devices" page; register them with "Simulated" unticked.
#define SOIL_SENSOR_ID 1
#define TEMP_SENSOR_ID 2      // the DHT22 in the diagram; 0 = do not send temperature
#define PUMP_ACTUATOR_ID 1

// The diagram has a relay (switches on with HIGH) and a blue LED on GPIO 2 for the pump
#define USE_RELAY 1
#define RELAY_ACTIVE_LOW false

// The slider gives 0-4095: left = dry soil (0 %), right = wet soil (100 %)
#define SOIL_RAW_DRY 0
#define SOIL_RAW_WET 4095
