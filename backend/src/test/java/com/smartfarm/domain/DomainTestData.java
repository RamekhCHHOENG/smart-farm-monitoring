package com.smartfarm.domain;

import org.springframework.test.util.ReflectionTestUtils;

/** Builds small in-memory object graphs for unit tests (ids are set as the database would). */
final class DomainTestData {

    final Farm farm = new Farm("Test farm", "Phnom Penh");
    final CropZone crop = farm.addCropZone("Bed", "Rice");
    final Sensor soil = crop.addSensor(SensorType.SOIL_MOISTURE, "Soil", true);
    final Actuator pump = crop.addActuator(ActuatorType.WATER_PUMP, "Pump", null);

    DomainTestData() {
        ReflectionTestUtils.setField(crop, "id", 1L);
        ReflectionTestUtils.setField(soil, "id", 10L);
        ReflectionTestUtils.setField(pump, "id", 20L);
    }
}
