package com.smartfarm.config;

import com.smartfarm.domain.Actuator;
import com.smartfarm.domain.DeviceModel;
import com.smartfarm.domain.Sensor;
import com.smartfarm.repository.ActuatorRepository;
import com.smartfarm.repository.DeviceModelRepository;
import com.smartfarm.repository.SensorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Gives every sensor and actuator made before the catalog existed a device of its own, made from the matching
 * catalog model (a soil sensor becomes a "Capacitive soil moisture sensor v1.2"). Ids, readings, rules and
 * logs stay as they are. Runs after the catalog and the demo farm are seeded; does nothing once done.
 */
@Component
@Order(200)
public class DeviceUpgrade implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeviceUpgrade.class);

    private final SensorRepository sensors;
    private final ActuatorRepository actuators;
    private final DeviceModelRepository models;
    private final Clock clock;

    public DeviceUpgrade(SensorRepository sensors, ActuatorRepository actuators, DeviceModelRepository models, Clock clock) {
        this.sensors = sensors;
        this.actuators = actuators;
        this.models = models;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int count = 0;
        for (Sensor sensor : sensors.findByDeviceIsNull()) {
            if (sensor.getType() != null) {
                sensor.getZone().adopt(sensor, model(CatalogSeeder.DEFAULT_SENSOR_MODEL.get(sensor.getType())), clock.instant());
                count++;
            }
        }
        for (Actuator actuator : actuators.findByDeviceIsNull()) {
            if (actuator.getType() != null) {
                actuator.getZone().adopt(actuator, model(CatalogSeeder.DEFAULT_ACTUATOR_MODEL.get(actuator.getType())), clock.instant());
                count++;
            }
        }
        if (count > 0) {
            log.info("Put {} existing sensors and actuators into catalog devices", count);
        }
    }

    private DeviceModel model(String name) {
        return models.findByName(name).orElseThrow(() -> new IllegalStateException("Catalog model missing: " + name));
    }
}
