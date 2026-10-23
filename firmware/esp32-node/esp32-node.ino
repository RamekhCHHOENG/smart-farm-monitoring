/*
 * Smart Farm sensor node (ESP32)
 *
 * Plays the "Sensor Device" actor of the design:
 *  - every 10 s it sends its readings                          (UC-3, FR-03)
 *  - every 3 s it asks the server whether the pump should run and
 *    switches the built-in LED (and the relay, if one is connected)
 *
 * Minimal kit: ESP32 + capacitive soil moisture sensor. The blue built-in LED stands in for the pump.
 * Full kit adds a DHT22 temperature sensor and a relay with a small 5 V pump.
 *
 * Wiring (see docs/HARDWARE.md):
 *  - Capacitive soil moisture sensor v1.2  AOUT -> GPIO 34, VCC -> 3V3, GND -> GND
 *  - DHT22 (optional)                      DATA -> GPIO 4 (10k pull-up to 3V3), VCC -> 3V3, GND -> GND
 *  - 1-channel relay module (optional)     IN   -> GPIO 26, VCC -> VIN (5 V), GND -> GND
 *    The relay switches a SEPARATE 5 V DC supply for the pump. Never mains 220 V.
 *
 * Libraries (Arduino Library Manager): "DHT sensor library" by Adafruit, "Adafruit Unified Sensor".
 * Board: "ESP32 Dev Module" (Boards Manager package "esp32" by Espressif).
 *
 * No board yet? The same sketch runs in the Wokwi simulator: see WOKWI.md.
 */
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <DHT.h>
#include "secrets.h"

const int SOIL_PIN = 34;
const int DHT_PIN = 4;
const int RELAY_PIN = 26;
const int LED_PIN = 2;               // built-in LED on most ESP32 DevKit boards
#ifndef RELAY_ACTIVE_LOW
#define RELAY_ACTIVE_LOW true  // most cheap relay modules switch on with LOW (the Wokwi relay: HIGH)
#endif

// Calibrate: open the Serial Monitor, note "raw" with the sensor in dry air and in a glass of water.
// secrets.h may override these (the Wokwi slider uses the full 0-4095 range).
#ifndef SOIL_RAW_DRY
#define SOIL_RAW_DRY 3000
#endif
#ifndef SOIL_RAW_WET
#define SOIL_RAW_WET 1250
#endif

const unsigned long READING_INTERVAL_MS = 10000;
const unsigned long COMMAND_INTERVAL_MS = 3000;
const int HTTP_TIMEOUT_MS = 5000;

DHT dht(DHT_PIN, DHT22);
unsigned long lastReading = 0;
unsigned long lastCommand = 0;

void setPump(bool on) {
  digitalWrite(LED_PIN, on ? HIGH : LOW);
  if (USE_RELAY) {
    digitalWrite(RELAY_PIN, (on != RELAY_ACTIVE_LOW) ? HIGH : LOW);
  }
}

void connectWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  Serial.printf("Connecting to %s", WIFI_SSID);
#ifdef WIFI_CHANNEL
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD, WIFI_CHANNEL);  // a fixed channel connects faster (Wokwi uses 6)
#else
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
#endif
  for (int i = 0; i < 40 && WiFi.status() != WL_CONNECTED; i++) {
    delay(500);
    Serial.print('.');
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf(" connected, IP %s\n", WiFi.localIP().toString().c_str());
  } else {
    Serial.println(" failed (check name/password; ESP32 only supports 2.4 GHz Wi-Fi)");
  }
}

// Opens a request. An https:// server address (for example an internet tunnel used with Wokwi)
// is reached over TLS; the tls object must live until the request has finished.
bool beginRequest(HTTPClient& http, WiFiClientSecure& tls, const String& url) {
  http.setTimeout(HTTP_TIMEOUT_MS);
  if (url.startsWith("https://")) {
#ifdef SERVER_ROOT_CA
    tls.setCACert(SERVER_ROOT_CA);  // checks the server's certificate
#else
    tls.setInsecure();  // encrypted, but the certificate is not checked: fine for testing only
#endif
    return http.begin(tls, url);
  }
  return http.begin(url);
}

float readSoilPercent() {
  long sum = 0;
  for (int i = 0; i < 10; i++) {  // average to reduce noise
    sum += analogRead(SOIL_PIN);
    delay(5);
  }
  float raw = sum / 10.0;
  float percent = (SOIL_RAW_DRY - raw) * 100.0 / (SOIL_RAW_DRY - SOIL_RAW_WET);
  Serial.printf("Soil raw %.0f -> %.1f %%\n", raw, percent);
  return constrain(percent, 0, 100);
}

void sendReading(int sensorId, float value) {
  if (sensorId <= 0) return;
  if (isnan(value)) {
    Serial.printf("Sensor %d: no value (check wiring), skipped\n", sensorId);
    return;
  }
  WiFiClientSecure tls;
  HTTPClient http;
  beginRequest(http, tls, String(SERVER_URL) + "/api/devices/readings");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-Device-Key", DEVICE_KEY);
  String body = "{\"sensorId\":" + String(sensorId) + ",\"value\":" + String(value, 1) + "}";
  int status = http.POST(body);
  // 201 = stored, 422 = value rejected, 403 = wrong device key, 404 = wrong sensor id, <0 = server not reachable
  Serial.printf("Sensor %d = %.1f -> HTTP %d %s\n", sensorId, value, status, http.getString().c_str());
  http.end();
}

void pollPump() {
  if (PUMP_ACTUATOR_ID <= 0) return;
  WiFiClientSecure tls;
  HTTPClient http;
  beginRequest(http, tls, String(SERVER_URL) + "/api/devices/actuators/" + String(PUMP_ACTUATOR_ID) + "/state");
  http.addHeader("X-Device-Key", DEVICE_KEY);
  int status = http.GET();
  if (status == 200) {
    bool on = http.getString().indexOf("\"relayOn\":true") >= 0;
    setPump(on);
  } else {
    setPump(false);  // fail safe: no answer from the server means pump off
    Serial.printf("Pump state -> HTTP %d, pump off\n", status);
  }
  http.end();
}

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  if (USE_RELAY) pinMode(RELAY_PIN, OUTPUT);
  setPump(false);
  analogReadResolution(12);
  if (TEMP_SENSOR_ID > 0) dht.begin();
  connectWifi();
}

void loop() {
  connectWifi();
  if (WiFi.status() != WL_CONNECTED) {
    setPump(false);
    delay(2000);
    return;
  }
  unsigned long now = millis();
  if (now - lastReading >= READING_INTERVAL_MS) {
    lastReading = now;
    if (SOIL_SENSOR_ID > 0) sendReading(SOIL_SENSOR_ID, readSoilPercent());
    if (TEMP_SENSOR_ID > 0) sendReading(TEMP_SENSOR_ID, dht.readTemperature());
  }
  if (now - lastCommand >= COMMAND_INTERVAL_MS) {
    lastCommand = now;
    pollPump();
  }
}
