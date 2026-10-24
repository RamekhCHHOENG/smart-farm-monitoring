package com.smartfarm.domain;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZoneAndSensorTest {

    @Test
    void eachZoneTypeProposesItsOwnDefaultRules() {
        Farm farm = new Farm("F", "L");
        CropZone crop = farm.addCropZone("Bed", "Rice");
        crop.addSensor(SensorType.SOIL_MOISTURE, "Soil", true);
        crop.addActuator(ActuatorType.WATER_PUMP, "Pump", null);
        PoultryZone house = farm.addPoultryZone("House", 50);
        house.addSensor(SensorType.TEMPERATURE, "Temp", true);
        house.addActuator(ActuatorType.FAN, "Fan", null);

        List<AutomationRule> cropRules = crop.createDefaultRules();
        List<AutomationRule> houseRules = house.createDefaultRules();

        assertThat(cropRules).singleElement().satisfies(r -> {
            assertThat(r.getCondition()).isEqualTo(Condition.BELOW);
            assertThat(r.getActuator().getType()).isEqualTo(ActuatorType.WATER_PUMP);
        });
        assertThat(houseRules).singleElement().satisfies(r -> {
            assertThat(r.getCondition()).isEqualTo(Condition.ABOVE);
            assertThat(r.getActuator().getType()).isEqualTo(ActuatorType.FAN);
        });
    }

    @Test
    void sensorRejectsValuesOutsideItsPhysicalRange() {
        Sensor soil = new DomainTestData().soil;
        assertThat(soil.isValid(-1)).isFalse();
        assertThat(soil.isValid(101)).isFalse();
        assertThat(soil.isValid(Double.NaN)).isFalse();
        assertThatThrownBy(() -> soil.record(150, Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simulatedSoilGetsWetterOnlyWhilePumpRuns() {
        Sensor soil = new DomainTestData().soil;
        Random random = new Random(1);
        assertThat(soil.simulateNext(40, Set.of(), random)).isLessThan(40);
        assertThat(soil.simulateNext(40, Set.of(ActuatorType.WATER_PUMP), random)).isGreaterThan(40);
        // A fan running in the same zone does not water the soil
        assertThat(soil.simulateNext(40, Set.of(ActuatorType.FAN), random)).isLessThan(40);
    }

    @Test
    void scheduledTaskRunsOncePerDayInsideItsWindow() {
        Actuator feeder = new Farm("F", "L").addPoultryZone("H", 10).addActuator(ActuatorType.FEEDER, "Feeder", null);
        ScheduledTask task = new ScheduledTask("Feed", feeder, LocalTime.of(7, 0), EnumSet.of(DayOfWeek.TUESDAY), 2);
        LocalDateTime tuesday7 = LocalDateTime.of(2026, 10, 20, 7, 0); // 20 Oct 2026 is a Tuesday

        assertThat(task.isDue(tuesday7.minusMinutes(1))).isFalse();
        assertThat(task.isDue(tuesday7)).isTrue();
        assertThat(task.isDue(tuesday7.plusMinutes(ScheduledTask.GRACE_MINUTES))).isFalse();
        assertThat(task.isDue(tuesday7.plusDays(1))).isFalse(); // Wednesday not selected

        task.markRun(tuesday7.toLocalDate());
        assertThat(task.isDue(tuesday7.plusMinutes(1))).isFalse();
    }

    @Test
    void nextRunSkipsTodayOnceDoneAndWaitsForTheNextSelectedDay() {
        Actuator feeder = new Farm("F", "L").addPoultryZone("H", 10).addActuator(ActuatorType.FEEDER, "Feeder", null);
        ScheduledTask task = new ScheduledTask("Feed", feeder, LocalTime.of(7, 0),
                EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), 2);
        LocalDateTime tuesday6 = LocalDateTime.of(2026, 10, 20, 6, 0);

        assertThat(task.nextRun(tuesday6)).isEqualTo(LocalDateTime.of(2026, 10, 20, 7, 0));
        task.markRun(tuesday6.toLocalDate());
        assertThat(task.nextRun(tuesday6.plusHours(2))).isEqualTo(LocalDateTime.of(2026, 10, 22, 7, 0)); // Thursday
        task.setActive(false);
        assertThat(task.nextRun(tuesday6)).isNull();
    }
}
