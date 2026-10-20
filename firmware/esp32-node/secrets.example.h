// Copy this file to secrets.h and fill in your values.
// secrets.h is git-ignored: never commit Wi-Fi passwords or the device key.
#pragma once

#define WIFI_SSID "your-wifi-name"
#define WIFI_PASSWORD "your-wifi-password"

// Address of the computer running the Spring Boot backend on the SAME Wi-Fi network,
// e.g. "http://192.168.1.20:8080" (find it with `ipconfig` on Windows or `ip addr` on Linux/macOS).
#define SERVER_URL "http://192.168.1.20:8080"

// Must equal FARM_DEVICE_KEY on the server.
#define DEVICE_KEY "paste-the-device-key-here"

// Ids shown on the "Zones & devices" page (the #number next to each device).
// Register sensors with "Simulated" unticked so the simulator leaves them alone.
// Set an id to 0 to disable that part.
#define SOIL_SENSOR_ID 1
#define TEMP_SENSOR_ID 0      // 0 = no DHT22 connected
#define PUMP_ACTUATOR_ID 1    // the built-in LED shows the pump state even without a relay

// 1 = a relay module is connected on GPIO 26, 0 = LED only (minimal kit)
#define USE_RELAY 0
