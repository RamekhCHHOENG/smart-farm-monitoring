package com.smartfarm.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The heater, mister, light and tank valve, and the humidity, light and tank sensors. */
class DeviceKindsTest {

    @Test
    void everyActuatorKindCanBeAddedWithItsOwnVerbAndSafetyLimit() {
        PoultryZone house = new Farm("F", "L").addPoultryZone("House", 20);
        for (ActuatorType type : ActuatorType.values()) {
            Actuator actuator = house.addActuator(type, type.name(), null);
            assertThat(actuator.getType()).isEqualTo(type);
            assertThat(actuator.getActionVerb()).isNotBlank();
            assertThat(actuator.getMaxRunMinutes()).isPositive().isLessThanOrEqualTo(240);
        }
        assertThat(house.getActuators()).extracting(Actuator::getActionVerb)
                .contains("Heating", "Misting", "Lighting", "Refilling");
    }

    @Test
    void everySensorKindKnowsItsUnitAndRange() {
        CropZone bed = new Farm("F", "L").addCropZone("Bed", "Rice");
        for (SensorType type : SensorType.values()) {
            Sensor sensor = bed.addSensor(type, type.name(), true);
            assertThat(sensor.getType()).isEqualTo(type);
            assertThat(sensor.getUnit()).isNotBlank();
            assertThat(sensor.getMinValid()).isLessThan(sensor.getMaxValid());
            assertThat(sensor.isValid(sensor.initialSimulatedValue())).isTrue();
            assertThat(sensor.isValid(sensor.getMaxValid() + 1)).isFalse();
        }
        assertThat(bed.findSensor(SensorType.LIGHT_LEVEL).orElseThrow().getUnit()).isEqualTo("lux");
    }

    @Test
    void aColdHouseWithAHeaterGetsAHeatingRuleButNoHeaterMeansNoRule() {
        PoultryZone withHeater = new Farm("F", "L").addPoultryZone("House", 20);
        withHeater.addSensor(SensorType.TEMPERATURE, "Temp", true);
        withHeater.addActuator(ActuatorType.FAN, "Fan", null);
        withHeater.addActuator(ActuatorType.HEATER, "Heater", null);

        List<AutomationRule> rules = withHeater.createDefaultRules();
        assertThat(rules).extracting(AutomationRule::getName).anyMatch(n -> n.startsWith("Cold house"));
        AutomationRule cold = rules.stream().filter(r -> r.getName().startsWith("Cold house")).findFirst().orElseThrow();
        assertThat(cold.getCondition()).isEqualTo(Condition.BELOW);
        assertThat(cold.getActuator().getType()).isEqualTo(ActuatorType.HEATER);
        assertThat(cold.getDurationMin()).isLessThanOrEqualTo(cold.getActuator().getMaxRunMinutes());

        PoultryZone noHeater = new Farm("F", "L").addPoultryZone("House 2", 20);
        noHeater.addSensor(SensorType.TEMPERATURE, "Temp", true);
        noHeater.addActuator(ActuatorType.FAN, "Fan", null);
        assertThat(noHeater.createDefaultRules()).singleElement()
                .satisfies(r -> assertThat(r.getName()).startsWith("Hot house"));
    }

    @Test
    void aGreenhouseGetsMistLightAndTankRules() {
        CropZone greenhouse = new Farm("F", "L").addCropZone("Greenhouse", "Tomato");
        greenhouse.addSensor(SensorType.HUMIDITY, "Humidity", true);
        greenhouse.addSensor(SensorType.LIGHT_LEVEL, "Light", true);
        greenhouse.addSensor(SensorType.WATER_LEVEL, "Tank", true);
        greenhouse.addActuator(ActuatorType.MISTER, "Mister", null);
        greenhouse.addActuator(ActuatorType.LIGHT, "Lamp", null);
        greenhouse.addActuator(ActuatorType.WATER_VALVE, "Valve", null);

        assertThat(greenhouse.createDefaultRules())
                .extracting(r -> r.getSensor().getType() + "->" + r.getActuator().getType())
                .containsExactlyInAnyOrder("HUMIDITY->MISTER", "LIGHT_LEVEL->LIGHT", "WATER_LEVEL->WATER_VALVE");
    }

    @Test
    void aDecommissionedHeaterIsNotUsedByDefaultRules() {
        PoultryZone house = new Farm("F", "L").addPoultryZone("House", 20);
        house.addSensor(SensorType.TEMPERATURE, "Temp", true);
        Actuator heater = house.addActuator(ActuatorType.HEATER, "Heater", null);
        heater.reportFault();
        heater.startRepair();
        heater.decommission();

        assertThat(house.createDefaultRules()).noneMatch(r -> r.getName().startsWith("Cold house"));
    }

    @Test
    void simulatedSensorsReactToTheActuatorsThatChangeThem() {
        Random random = new Random(7);
        CropZone zone = new Farm("F", "L").addCropZone("Z", "Tomato");
        Sensor temperature = zone.addSensor(SensorType.TEMPERATURE, "T", true);
        Sensor humidity = zone.addSensor(SensorType.HUMIDITY, "H", true);
        Sensor light = zone.addSensor(SensorType.LIGHT_LEVEL, "L", true);
        Sensor tank = zone.addSensor(SensorType.WATER_LEVEL, "W", true);

        assertThat(temperature.simulateNext(30, Set.of(ActuatorType.HEATER), random)).isGreaterThan(30);
        assertThat(temperature.simulateNext(30, Set.of(ActuatorType.FAN), random)).isLessThan(30);

        assertThat(humidity.simulateNext(68, Set.of(ActuatorType.MISTER), random)).isGreaterThan(68);
        assertThat(humidity.simulateNext(68, Set.of(ActuatorType.FAN), random)).isLessThan(68);

        assertThat(light.simulateNext(15_000, Set.of(ActuatorType.LIGHT), random)).isGreaterThan(15_000);

        assertThat(tank.simulateNext(50, Set.of(ActuatorType.WATER_VALVE), random)).isGreaterThan(50);
        assertThat(tank.simulateNext(50, Set.of(ActuatorType.WATER_PUMP), random)).isLessThan(50);
        assertThat(tank.simulateNext(50, Set.of(), random)).isLessThan(50);
    }

    @Test
    void heaterAndFanOpposeEachOtherAndNothingElse() {
        PoultryZone house = new Farm("F", "L").addPoultryZone("House", 20);
        Actuator heater = house.addActuator(ActuatorType.HEATER, "Heater", null);
        Actuator fan = house.addActuator(ActuatorType.FAN, "Fan", null);
        Actuator feeder = house.addActuator(ActuatorType.FEEDER, "Feeder", null);
        java.time.Instant now = java.time.Instant.parse("2026-10-23T08:00:00Z");

        assertThat(heater.opposes(ActuatorType.FAN)).isTrue();
        assertThat(fan.opposes(ActuatorType.HEATER)).isTrue();
        assertThat(feeder.opposes(ActuatorType.FAN)).isFalse();

        fan.activate(5, now);
        feeder.activate(2, now);
        assertThat(house.runningOpposites(heater)).containsExactly(fan);
        assertThat(house.runningOpposites(feeder)).isEmpty();
    }

    @Test
    void aRuleOnlyOwnsTheRunItStarted() {
        Actuator fan = new Farm("F", "L").addPoultryZone("House", 20).addActuator(ActuatorType.FAN, "Fan", null);
        java.time.Instant now = java.time.Instant.parse("2026-10-23T08:00:00Z");
        fan.activateForRule(5, now, 7L);
        assertThat(fan.isRunningFor(7L)).isTrue();
        assertThat(fan.isRunningFor(8L)).isFalse();
        fan.deactivate();
        fan.activate(5, now); // a person started it
        assertThat(fan.isRunningFor(7L)).isFalse();
    }

    @Test
    void defaultRulesNeverAskForMoreThanTheActuatorAllows() {
        PoultryZone house = new Farm("F", "L").addPoultryZone("House", 20);
        house.addSensor(SensorType.TEMPERATURE, "Temp", true);
        house.addActuator(ActuatorType.FAN, "Small fan", 2);
        assertThat(house.createDefaultRules()).singleElement().satisfies(r -> {
            assertThat(r.getDurationMin()).isEqualTo(2);
            assertThat(r.getStopAt()).isEqualTo(29.0);
        });
    }

    @Test
    void aZoneReportsWhichKindsOfActuatorAreRunning() {
        PoultryZone house = new Farm("F", "L").addPoultryZone("House", 20);
        Actuator heater = house.addActuator(ActuatorType.HEATER, "Heater", null);
        house.addActuator(ActuatorType.FAN, "Fan", null);
        assertThat(house.runningActuatorTypes()).isEmpty();

        heater.activate(5, java.time.Instant.parse("2026-10-23T08:00:00Z"));
        assertThat(house.runningActuatorTypes()).containsExactly(ActuatorType.HEATER);
    }
}
