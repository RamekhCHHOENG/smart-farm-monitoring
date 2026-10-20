/*
 * Smart Farm sensor node (ESP32)
 *
 * Plays the "Sensor Device" actor of the design:
 *  - every 10 s it sends soil moisture and temperature readings  (UC-3, FR-03)
 *  - every 3 s it asks the server whether the pump should run and switches the relay
 *
 * Wiring (see docs/HARDWARE.md):
 *  - Capacitive soil moisture sensor v1.2  AOUT -> GPIO 34, VCC -> 3V3, GND -> GND
 *  - DHT22 temperature sensor              DATA -> GPIO 4 (10k pull-up to 3V3)
 *  - 1-channel relay module (5 V, opto)    IN   -> GPIO 26, VCC -> 5V/VIN, GND -> GND
 *    Relay switches a SEPARATE 5-12 V DC supply for the small pump. Never mains 220 V.
 *
 * Libraries (Arduino Library Manager): "DHT sensor library" by Adafruit, "Adafruit Unified Sensor".
 */
#include <WiFi.h>
#include <HTTPClient.h>
#include <DHT.h>
#include "secrets.h"

const int SOIL_PIN = 34;
const int DHT_PIN = 4;
const int RELAY_PIN = 26;
const bool RELAY_ACTIVE_LOW = true;  // most cheap relay modules switch on with LOW

// Calibrate: read the raw value in dry air and in a glass of water, then put them here.
const int SOIL_RAW_DRY = 3000;
const int SOIL_RAW_WET = 1250;

const unsigned long READING_INTERVAL_MS = 10000;
const unsigned long COMMAND_INTERVAL_MS = 3000;

DHT dht(DHT_PIN, DHT22);
unsigned long lastReading = 0;
unsigned long lastCommand = 0;

void setRelay(bool on) {
  digitalWrite(RELAY_PIN, (on ^ RELAY_ACTIVE_LOW) ? HIGH : LOW);
}

void connectWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  Serial.printf("Connecting to %s", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  for (int i = 0; i < 40 && WiFi.status() != WL_CONNECTED; i++) {
    delay(500);
    Serial.print('.');
  }
  Serial.println(WiFi.status() == WL_CONNECTED ? " connected" : " failed");
}

float readSoilPercent() {
  long sum = 0;
  for (int i = 0; i < 10; i++) {  // average to reduce noise
    sum += analogRead(SOIL_PIN);
    delay(5);
  }
  float raw = sum / 10.0;
  float percent = (SOIL_RAW_DRY - raw) * 100.0 / (SOIL_RAW_DRY - SOIL_RAW_WET);
  return constrain(percent, 0, 100);
}

void sendReading(int sensorId, float value) {
  if (isnan(value)) {
    Serial.printf("Sensor %d: no value, skipped\n", sensorId);
    return;
  }
  HTTPClient http;
  http.begin(String(SERVER_URL) + "/api/devices/readings");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-Device-Key", DEVICE_KEY);
  String body = "{\"sensorId\":" + String(sensorId) + ",\"value\":" + String(value, 1) + "}";
  int status = http.POST(body);
  Serial.printf("Sensor %d = %.1f -> HTTP %d %s\n", sensorId, value, status, http.getString().c_str());
  http.end();
}

void pollPump() {
  HTTPClient http;
  http.begin(String(SERVER_URL) + "/api/devices/actuators/" + String(PUMP_ACTUATOR_ID) + "/state");
  http.addHeader("X-Device-Key", DEVICE_KEY);
  int status = http.GET();
  if (status == 200) {
    bool on = http.getString().indexOf("\"relayOn\":true") >= 0;
    setRelay(on);
  } else {
    setRelay(false);  // fail safe: no answer from the server means pump off
    Serial.printf("Pump state -> HTTP %d, relay off\n", status);
  }
  http.end();
}

void setup() {
  Serial.begin(115200);
  pinMode(RELAY_PIN, OUTPUT);
  setRelay(false);
  analogReadResolution(12);
  dht.begin();
  connectWifi();
}

void loop() {
  connectWifi();
  if (WiFi.status() != WL_CONNECTED) {
    setRelay(false);
    delay(2000);
    return;
  }
  unsigned long now = millis();
  if (now - lastReading >= READING_INTERVAL_MS) {
    lastReading = now;
    sendReading(SOIL_SENSOR_ID, readSoilPercent());
    sendReading(TEMP_SENSOR_ID, dht.readTemperature());
  }
  if (now - lastCommand >= COMMAND_INTERVAL_MS) {
    lastCommand = now;
    pollPump();
  }
}
