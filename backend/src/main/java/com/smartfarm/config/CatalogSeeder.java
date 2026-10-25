package com.smartfarm.config;

import com.smartfarm.domain.ActuatorType;
import com.smartfarm.domain.DeviceModel;
import com.smartfarm.domain.MeasurementType;
import com.smartfarm.domain.ModelChannel;
import com.smartfarm.domain.ModelEffect;
import com.smartfarm.domain.SensorType;
import com.smartfarm.repository.DeviceModelRepository;
import com.smartfarm.repository.MeasurementTypeRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Fills the master data on every start: the measurement types and a starter catalog of real devices that
 * small farms use (ESP32-friendly sensors, pumps, fans, brooder lamps, valves, boards). Adds only what is
 * missing, so staff edits and their own models are kept. Prices are rough, in US dollars.
 */
@Component
@Order(50)
public class CatalogSeeder implements ApplicationRunner {

    /** The catalog model a device of each built-in kind is made from when no model is chosen. */
    public static final Map<SensorType, String> DEFAULT_SENSOR_MODEL = Map.of(
            SensorType.SOIL_MOISTURE, "Capacitive soil moisture sensor v1.2",
            SensorType.TEMPERATURE, "DS18B20 waterproof temperature probe",
            SensorType.FEED_LEVEL, "HX711 load cell kit (feed weight)",
            SensorType.HUMIDITY, "DHT22 temperature & humidity sensor",
            SensorType.LIGHT_LEVEL, "BH1750 light sensor",
            SensorType.WATER_LEVEL, "JSN-SR04T waterproof ultrasonic level sensor");

    public static final Map<ActuatorType, String> DEFAULT_ACTUATOR_MODEL = Map.of(
            ActuatorType.WATER_PUMP, "12 V diaphragm water pump",
            ActuatorType.FAN, "Poultry exhaust fan 36-inch",
            ActuatorType.FEEDER, "Auger feeder motor",
            ActuatorType.HEATER, "Infrared brooder lamp 250 W",
            ActuatorType.MISTER, "12 V misting kit",
            ActuatorType.LIGHT, "LED grow light 50 W",
            ActuatorType.WATER_VALVE, "12 V solenoid valve 1/2-inch");

    private final MeasurementTypeRepository measurements;
    private final DeviceModelRepository models;

    public CatalogSeeder(MeasurementTypeRepository measurements, DeviceModelRepository models) {
        this.measurements = measurements;
        this.models = models;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // The first six match the built-in sensor classes exactly (same unit and range).
        measurement("SOIL_MOISTURE", "Soil moisture", "%", 0, 100, 45, true);
        measurement("TEMPERATURE", "Temperature", "°C", -10, 60, 29, true);
        measurement("FEED_LEVEL", "Feed level", "%", 0, 100, 60, true);
        measurement("HUMIDITY", "Air humidity", "%", 0, 100, 65, true);
        measurement("LIGHT_LEVEL", "Light level", "lux", 0, 100_000, 15_000, true);
        measurement("WATER_LEVEL", "Water tank level", "%", 0, 100, 70, true);
        measurement("SOIL_TEMPERATURE", "Soil temperature", "°C", -10, 60, 27, false);
        measurement("CO2", "Carbon dioxide (CO₂)", "ppm", 0, 5000, 600, false);
        measurement("AMMONIA", "Ammonia (NH₃)", "ppm", 0, 100, 8, false);
        measurement("PH", "pH (acidity)", "pH", 0, 14, 6.5, false);
        measurement("EC", "Electrical conductivity (nutrients)", "mS/cm", 0, 20, 1.8, false);
        measurement("WATER_FLOW", "Water flow", "L/min", 0, 60, 0, false);
        measurement("RAIN", "Rainfall", "mm/h", 0, 200, 0, false);
        measurement("WIND_SPEED", "Wind speed", "m/s", 0, 60, 3, false);
        measurement("MOTION", "Motion (intruder)", "0/1", 0, 1, 0, false);

        // ---- sensors ----
        sensor("Capacitive soil moisture sensor v1.2", "Generic", "Corrosion-resistant soil probe; calibrate dry and wet for your soil.",
                "analog", "3.3-5 V", 2.0, null, "SOIL_MOISTURE", "AOUT to GPIO 34 (ADC1), VCC to 3V3, GND to GND.",
                ch("SOIL_MOISTURE", "Soil moisture"));
        sensor("RS485 soil moisture & temperature probe", "Generic", "Field-grade buried probe with Modbus RTU; long cables.",
                "RS485 (Modbus)", "5-30 V", 40.0, null, "SOIL_MOISTURE", "A/B to a MAX485 module on the ESP32 UART; separate 12 V supply.",
                ch("SOIL_MOISTURE", "Soil moisture"), ch("SOIL_TEMPERATURE", "Soil temperature"));
        sensor("DHT22 temperature & humidity sensor", "Aosong (AM2302)", "Air temperature and humidity in one; read every 2 s at most.",
                "digital", "3.3-5 V", 4.0, "dht22", "TEMPERATURE", "DATA to GPIO 4 with a 10 kΩ pull-up to 3V3.",
                ch("TEMPERATURE", "Temperature"), ch("HUMIDITY", "Humidity"));
        sensor("DHT11 temperature & humidity sensor", "Aosong", "Cheaper, less accurate DHT22 (±2 °C, ±5 %).",
                "digital", "3.3-5 V", 1.5, "dht22", "TEMPERATURE", "DATA to a GPIO with a 10 kΩ pull-up.",
                ch("TEMPERATURE", "Temperature"), ch("HUMIDITY", "Humidity"));
        sensor("SHT31 temperature & humidity sensor", "Sensirion", "Accurate (±0.3 °C, ±2 %); good for greenhouses.",
                "I2C", "3.3 V", 6.0, null, "HUMIDITY", "SDA to GPIO 21, SCL to GPIO 22.",
                ch("TEMPERATURE", "Temperature"), ch("HUMIDITY", "Humidity"));
        sensor("DS18B20 waterproof temperature probe", "Maxim", "Stainless probe on a cable; several on one wire.",
                "1-wire", "3-5.5 V", 2.0, null, "TEMPERATURE", "DATA to a GPIO with a 4.7 kΩ pull-up.",
                ch("TEMPERATURE", "Temperature"));
        sensor("NTC thermistor module", "Generic", "Simple analog temperature sensor for demos.",
                "analog", "3.3-5 V", 1.0, "ntc-temperature-sensor", "TEMPERATURE", "OUT to an ADC1 pin.",
                ch("TEMPERATURE", "Temperature"));
        sensor("BH1750 light sensor", "ROHM", "Measures light in lux (1-65535).",
                "I2C", "3.3-5 V", 2.0, null, "LIGHT_LEVEL", "SDA to GPIO 21, SCL to GPIO 22.",
                ch("LIGHT_LEVEL", "Light"));
        sensor("Photoresistor (LDR) module", "Generic", "Cheap light sensor; gives brighter/darker, not exact lux.",
                "analog", "3.3-5 V", 1.0, "photoresistor-sensor", "LIGHT_LEVEL", "AO to an ADC1 pin.",
                ch("LIGHT_LEVEL", "Light"));
        sensor("JSN-SR04T waterproof ultrasonic level sensor", "Generic", "Mounted on the tank lid; measures the distance to the water.",
                "digital (trigger/echo)", "5 V", 7.0, "hc-sr04", "WATER_LEVEL", "TRIG and ECHO to two GPIOs (ECHO through a divider to 3.3 V). Note the tank height.",
                ch("WATER_LEVEL", "Tank level"));
        sensor("HC-SR04 ultrasonic sensor", "Generic", "Indoor distance sensor; for a feed hopper or a covered tank.",
                "digital (trigger/echo)", "5 V", 1.5, "hc-sr04", "WATER_LEVEL", "TRIG and ECHO to two GPIOs (ECHO through a divider).",
                ch("WATER_LEVEL", "Level"));
        sensor("Float switch", "Generic", "Says only full or not full; simple and reliable.",
                "digital", "any", 2.0, null, "WATER_LEVEL", "Between a GPIO (pull-up) and GND.",
                ch("WATER_LEVEL", "Tank full"));
        sensor("HX711 load cell kit (feed weight)", "Avia Semiconductor", "Weighs a feed hopper; more reliable than distance in dust.",
                "digital (2-wire)", "2.7-5 V", 8.0, "hx711", "FEED_LEVEL", "DOUT and SCK to two GPIOs; calibrate with a known weight.",
                ch("FEED_LEVEL", "Feed level"));
        sensor("MQ-135 air quality sensor", "Winsen", "Detects ammonia and other gases in a chicken house; needs warm-up.",
                "analog", "5 V", 3.0, "gas-sensor", "AMMONIA", "AO to an ADC1 pin through a divider; heater needs 5 V.",
                ch("AMMONIA", "Ammonia"));
        sensor("MH-Z19B CO₂ sensor", "Winsen", "Infrared CO₂ sensor for greenhouses and closed houses.",
                "UART", "5 V", 20.0, null, "CO2", "TX/RX to UART2 (GPIO 16/17).",
                ch("CO2", "CO₂"));
        sensor("Analog pH sensor kit", "DFRobot (style)", "For irrigation water and hydroponics; calibrate with buffer solutions.",
                "analog", "5 V", 15.0, null, "PH", "Signal to an ADC1 pin through a divider.",
                ch("PH", "pH"));
        sensor("Analog TDS/EC meter", "Generic", "Nutrient strength of irrigation or hydroponic water.",
                "analog", "3.3-5 V", 10.0, null, "EC", "Signal to an ADC1 pin.",
                ch("EC", "EC"));
        sensor("YF-S201 water flow sensor", "Generic", "Counts water through a pipe: proves the pump really delivers water.",
                "pulse", "5 V", 3.0, null, null, "Signal to an interrupt-capable GPIO; 450 pulses per litre.",
                ch("WATER_FLOW", "Water flow"));
        sensor("Tipping-bucket rain gauge", "Generic", "Each tip is a fixed amount of rain.",
                "pulse", "any", 25.0, null, null, "Reed switch between a GPIO (pull-up) and GND.",
                ch("RAIN", "Rainfall"));
        sensor("Cup anemometer", "Generic", "Wind speed for spraying and shade-net decisions.",
                "pulse/analog", "5-12 V", 30.0, null, null, "Pulse output to an interrupt GPIO.",
                ch("WIND_SPEED", "Wind speed"));
        sensor("PIR motion sensor (HC-SR501)", "Generic", "Detects people or animals at night (theft, predators).",
                "digital", "5 V", 1.5, "pir-motion-sensor", null, "OUT to a GPIO.",
                ch("MOTION", "Motion"));

        // ---- actuators ----
        actuator("5 V mini submersible pump", "Generic", "Tiny pump for demos and seed trays.", "relay", "5 V DC", 2.0,
                null, "WATER_PUMP", ActuatorType.WATER_PUMP, null, 5, "Through a relay module with its own 5 V supply.");
        actuator("12 V diaphragm water pump", "Generic", "Small self-priming pump for drip lines.", "relay", "12 V DC", 8.0,
                null, "WATER_PUMP", ActuatorType.WATER_PUMP, null, 10, "Through a relay; 12 V supply, flyback diode on the motor.");
        actuator("220 V water pump 0.5 HP (with contactor)", "Generic", "Farm pump on mains; switched by a contactor.", "contactor", "220 V AC", 60.0,
                null, "WATER_PUMP", ActuatorType.WATER_PUMP, null, 15, "Relay drives the contactor coil. Mains wiring by an electrician, with an RCD.");
        actuator("12 V DC fan", "Generic", "Small fan for a nursery box or a demo.", "relay/PWM", "12 V DC", 3.0,
                null, "FAN", ActuatorType.FAN, null, 30, "Through a relay or a MOSFET for speed control.");
        actuator("Poultry exhaust fan 36-inch", "Generic", "House ventilation; several are staged for tunnel ventilation.", "contactor", "220 V AC", 150.0,
                null, "FAN", ActuatorType.FAN, null, 30, "Contactor per fan or fan group; motor protection breaker.");
        actuator("Auger feeder motor", "Generic", "Drives the feed line from the hopper.", "contactor", "220 V AC", 80.0,
                null, "FEEDER", ActuatorType.FEEDER, null, 5, "Contactor; stop on a full-line sensor.");
        actuator("SG90 servo feeder gate", "TowerPro", "Opens a small feed gate for a demo feeder.", "PWM", "5 V", 3.0,
                "servo", "FEEDER", ActuatorType.FEEDER, null, 2, "Signal to a PWM GPIO; separate 5 V supply.");
        actuator("Infrared brooder lamp 250 W", "Generic", "Keeps chicks warm (about 32-35 °C the first week).", "relay", "220 V AC", 8.0,
                null, "HEATER", ActuatorType.HEATER, null, 30, "Mains relay rated for the lamp; keep away from litter.");
        actuator("Gas brooder", "Generic", "Heats a larger house; needs a gas valve and ventilation.", "relay (valve)", "220 V AC / gas", 60.0,
                null, "HEATER", ActuatorType.HEATER, null, 30, "Relay drives the gas valve controller; installed by a professional.");
        actuator("12 V misting kit", "Generic", "Pump and nozzles that raise humidity or cool by evaporation.", "relay", "12 V DC", 25.0,
                null, "MISTER", ActuatorType.MISTER, null, 5, "Through a relay; filter the water to keep nozzles clean.");
        actuator("LED grow light 50 W", "Generic", "Extra light for seedlings and greenhouses.", "relay", "220 V AC", 25.0,
                null, "LIGHT", ActuatorType.LIGHT, null, 120, "Through a relay; often on a schedule.");
        actuator("LED poultry house light", "Generic", "Layer hens need about 14-16 hours of light a day.", "relay", "220 V AC", 10.0,
                null, "LIGHT", ActuatorType.LIGHT, null, 120, "Through a relay; on a schedule plus a light sensor.");
        actuator("12 V solenoid valve 1/2-inch", "Generic", "Closes when power is cut (fail-safe); fills tanks or opens drip lines.", "relay", "12 V DC", 6.0,
                null, "WATER_VALVE", ActuatorType.WATER_VALVE, null, 15, "Through a relay; flyback diode across the coil.");
        actuator("Motorized ball valve", "Generic", "Strong flow, low power; stays in place if power fails.", "relay", "12 V DC", 20.0,
                null, "WATER_VALVE", ActuatorType.WATER_VALVE, null, 15, "Two relays for open/close, or a 3-wire valve.");
        actuator("1-channel 5 V relay module", "Songle (KS2E style)", "Switches another device on or off; the usual link from an ESP32 to power.",
                "relay", "5 V", 1.5, "ks2e-m-dc5", null, null, "Switching", 30, "IN to a GPIO, VCC to 5 V (VIN), GND; load on COM/NO.");
        actuator("Alarm siren / buzzer", "Generic", "Sounds on site when something is critical (theft, fire, flood).", "digital", "5-12 V", 3.0,
                "buzzer", null, null, "Sounding", 5, "Through a transistor or a relay.");

        // ---- controller boards ----
        controller("ESP32 DevKit V1", "Espressif", "Wi-Fi microcontroller that runs our firmware (sensors + relays).", "Wi-Fi", "5 V USB", 6.0, "esp32-devkit-v1");
        controller("LILYGO LoRa32 (ESP32 + LoRa)", "LILYGO", "ESP32 with LoRa radio for fields far from Wi-Fi (several km).", "LoRa / Wi-Fi", "5 V USB / battery", 15.0, null);
    }

    private void measurement(String code, String name, String unit, double min, double max, double typical, boolean builtIn) {
        if (!measurements.existsByCode(code)) {
            measurements.save(new MeasurementType(code, name, unit, min, max, typical, builtIn));
        }
    }

    private static ModelChannel ch(String code, String label) {
        return new ModelChannel(code, label);
    }

    private void sensor(String name, String maker, String description, String iface, String voltage, Double price,
                        String wokwi, String art, String wiring, ModelChannel... channels) {
        if (models.findByName(name).isPresent()) {
            return;
        }
        models.save(new DeviceModel(name, "SENSOR", true).describe(maker, description, iface, voltage, price)
                .looks(wokwi, art).docs(null, wiring).measures(List.of(channels)));
    }

    private void actuator(String name, String maker, String description, String iface, String voltage, Double price,
                          String wokwi, String art, ActuatorType type, String verb, int maxMinutes, String wiring) {
        if (models.findByName(name).isPresent()) {
            return;
        }
        List<ModelEffect> effects = type == null ? List.of() : switch (type) {
            case WATER_PUMP -> List.of(new ModelEffect("SOIL_MOISTURE", 1), new ModelEffect("WATER_LEVEL", -1));
            case FAN -> List.of(new ModelEffect("TEMPERATURE", -1), new ModelEffect("HUMIDITY", -1));
            case FEEDER -> List.of(new ModelEffect("FEED_LEVEL", 1));
            case HEATER -> List.of(new ModelEffect("TEMPERATURE", 1));
            case MISTER -> List.of(new ModelEffect("HUMIDITY", 1));
            case LIGHT -> List.of(new ModelEffect("LIGHT_LEVEL", 1));
            case WATER_VALVE -> List.of(new ModelEffect("WATER_LEVEL", 1));
        };
        models.save(new DeviceModel(name, "ACTUATOR", true).describe(maker, description, iface, voltage, price)
                .looks(wokwi, art).docs(null, wiring).acts(type == null ? null : type.name(), verb, maxMinutes, effects));
    }

    private void controller(String name, String maker, String description, String iface, String voltage, Double price, String wokwi) {
        if (models.findByName(name).isPresent()) {
            return;
        }
        models.save(new DeviceModel(name, "CONTROLLER", true).describe(maker, description, iface, voltage, price).looks(wokwi, null));
    }
}
