package com.smartfarm.config;

import com.smartfarm.domain.*;
import com.smartfarm.repository.AppUserRepository;
import com.smartfarm.repository.AutomationRuleRepository;
import com.smartfarm.repository.FarmRepository;
import com.smartfarm.repository.ScheduledTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HexFormat;

/**
 * On first start, creates the farm and its three users. With FARM_DEMO_DATA=false the farm
 * starts empty, ready for a real setup. Otherwise it creates a demo farm: a vegetable bed, a greenhouse and a
 * chicken house with devices, default rules (from each zone subclass), and three scheduled tasks.
 * Demo users get the password from FARM_DEMO_PASSWORD; if it is not set, a random
 * password is generated and printed once to the server log. No password is committed.
 */
@Component
@org.springframework.core.annotation.Order(100)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final FarmRepository farms;
    private final AppUserRepository users;
    private final AutomationRuleRepository rules;
    private final ScheduledTaskRepository tasks;
    private final PasswordEncoder encoder;
    private final String demoPassword;
    private final boolean demoData;

    public DemoDataSeeder(FarmRepository farms, AppUserRepository users, AutomationRuleRepository rules,
                          ScheduledTaskRepository tasks, PasswordEncoder encoder,
                          @Value("${farm.demo-password:}") String demoPassword,
                          @Value("${farm.demo-data:true}") boolean demoData) {
        this.demoData = demoData;
        this.farms = farms;
        this.users = users;
        this.rules = rules;
        this.tasks = tasks;
        this.encoder = encoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (farms.count() > 0) {
            return;
        }
        Farm farm = demoData ? new Farm("Group 1 Demo Farm", "Kandal, Cambodia") : new Farm("My Farm", "");
        if (demoData) {
            createDemoZones(farm);
        } else {
            farms.save(farm);
        }
        createUsers(farm);
        log.info("Farm created with {} zones (demo data: {})", farm.getZones().size(), demoData);
    }

    private void createDemoZones(Farm farm) {
        CropZone crop = farm.addCropZone("Vegetable Bed A", "Morning glory");
        crop.addSensor(SensorType.SOIL_MOISTURE, "Soil moisture A1", true);
        crop.addSensor(SensorType.TEMPERATURE, "Air temperature A", true);
        Actuator pump = crop.addActuator(ActuatorType.WATER_PUMP, "Water pump A", null);

        PoultryZone poultry = farm.addPoultryZone("Chicken House 1", 120);
        poultry.addSensor(SensorType.TEMPERATURE, "House temperature 1", true);
        poultry.addSensor(SensorType.FEED_LEVEL, "Feed tray level 1", true);
        poultry.addActuator(ActuatorType.FAN, "Exhaust fan 1", null);
        Actuator feeder = poultry.addActuator(ActuatorType.FEEDER, "Feeder 1", null);
        poultry.addActuator(ActuatorType.HEATER, "Brooder heater 1", null);

        // Added after the first two zones, so the ids of the original devices do not change.
        CropZone greenhouse = farm.addCropZone("Greenhouse 1", "Tomato");
        greenhouse.addSensor(SensorType.HUMIDITY, "Greenhouse humidity", true);
        greenhouse.addSensor(SensorType.LIGHT_LEVEL, "Greenhouse light", true);
        greenhouse.addSensor(SensorType.WATER_LEVEL, "Water tank level", true);
        greenhouse.addActuator(ActuatorType.MISTER, "Mist sprayer", null);
        Actuator growLight = greenhouse.addActuator(ActuatorType.LIGHT, "Grow light", null);
        greenhouse.addActuator(ActuatorType.WATER_VALVE, "Tank fill valve", null);

        farms.saveAndFlush(farm);
        rules.saveAll(crop.createDefaultRules());
        rules.saveAll(poultry.createDefaultRules());
        rules.saveAll(greenhouse.createDefaultRules());

        EnumSet<DayOfWeek> everyDay = EnumSet.allOf(DayOfWeek.class);
        tasks.save(new ScheduledTask("Morning feeding", feeder, LocalTime.of(7, 0), everyDay, 3));
        tasks.save(new ScheduledTask("Evening watering", pump, LocalTime.of(17, 0), everyDay, 5));
        tasks.save(new ScheduledTask("Evening grow light", growLight, LocalTime.of(18, 30), everyDay, 90));
    }

    private void createUsers(Farm farm) {
        String password = demoPassword;
        if (password == null || password.isBlank()) {
            byte[] bytes = new byte[9];
            new SecureRandom().nextBytes(bytes);
            password = HexFormat.of().formatHex(bytes);
            log.warn("FARM_DEMO_PASSWORD is not set. Demo users owner / worker / technician use the generated password: {}", password);
        }
        String hash = encoder.encode(password);
        users.save(new AppUser(farm, "owner", "Sokha", hash, Role.OWNER));
        users.save(new AppUser(farm, "worker", "Dara", hash, Role.WORKER));
        users.save(new AppUser(farm, "technician", "Vibol", hash, Role.TECHNICIAN));
    }
}
