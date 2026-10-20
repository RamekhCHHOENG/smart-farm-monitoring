// Copy this file to secrets.h and fill in your values.
// secrets.h is git-ignored: never commit Wi-Fi passwords or the device key.
#pragma once

#define WIFI_SSID "your-wifi-name"
#define WIFI_PASSWORD "your-wifi-password"

// Address of the computer running the Spring Boot backend on the same Wi-Fi network,
// e.g. "http://192.168.1.20:8080". Use https:// if the server is reachable from the internet.
#define SERVER_URL "http://192.168.1.20:8080"

// Must equal FARM_DEVICE_KEY on the server.
#define DEVICE_KEY "paste-the-device-key-here"

// Ids shown on the "Zones & devices" page (the #number next to each device).
// Register the sensors with "Simulated" unticked so the simulator leaves them alone.
#define SOIL_SENSOR_ID 1
#define TEMP_SENSOR_ID 2
#define PUMP_ACTUATOR_ID 1
