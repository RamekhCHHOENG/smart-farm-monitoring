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
 * Creates a demo farm on first start: one crop zone and one poultry zone with
 * devices, default rules (from each zone subclass), and two scheduled tasks.
 * Demo users get the password from FARM_DEMO_PASSWORD; if it is not set, a random
 * password is generated and printed once to the server log. No password is committed.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final FarmRepository farms;
    private final AppUserRepository users;
    private final AutomationRuleRepository rules;
    private final ScheduledTaskRepository tasks;
    private final PasswordEncoder encoder;
    private final String demoPassword;

    public DemoDataSeeder(FarmRepository farms, AppUserRepository users, AutomationRuleRepository rules,
                          ScheduledTaskRepository tasks, PasswordEncoder encoder,
                          @Value("${farm.demo-password:}") String demoPassword) {
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
        Farm farm = new Farm("Group 1 Demo Farm", "Kandal, Cambodia");

        CropZone crop = farm.addCropZone("Vegetable Bed A", "Morning glory");
        crop.addSensor(SensorType.SOIL_MOISTURE, "Soil moisture A1", true);
        crop.addSensor(SensorType.TEMPERATURE, "Air temperature A", true);
        Actuator pump = crop.addActuator(ActuatorType.WATER_PUMP, "Water pump A", null);

        PoultryZone poultry = farm.addPoultryZone("Chicken House 1", 120);
        poultry.addSensor(SensorType.TEMPERATURE, "House temperature 1", true);
        poultry.addSensor(SensorType.FEED_LEVEL, "Feed tray level 1", true);
        poultry.addActuator(ActuatorType.FAN, "Exhaust fan 1", null);
        Actuator feeder = poultry.addActuator(ActuatorType.FEEDER, "Feeder 1", null);

        farms.saveAndFlush(farm);
        rules.saveAll(crop.createDefaultRules());
        rules.saveAll(poultry.createDefaultRules());

        EnumSet<DayOfWeek> everyDay = EnumSet.allOf(DayOfWeek.class);
        tasks.save(new ScheduledTask("Morning feeding", feeder, LocalTime.of(7, 0), everyDay, 3));
        tasks.save(new ScheduledTask("Evening watering", pump, LocalTime.of(17, 0), everyDay, 5));

        String password = demoPassword;
        if (password == null || password.isBlank()) {
            byte[] bytes = new byte[9];
            new SecureRandom().nextBytes(bytes);
            password = HexFormat.of().formatHex(bytes);
            log.warn("FARM_DEMO_PASSWORD is not set. Demo users owner / worker / technician use the generated password: {}", password);
        }
        String hash = encoder.encode(password);
        users.save(new AppUser(farm, "owner", "Sokha (Farm Owner)", hash, Role.OWNER));
        users.save(new AppUser(farm, "worker", "Dara (Farm Worker)", hash, Role.WORKER));
        users.save(new AppUser(farm, "technician", "Vibol (Technician)", hash, Role.TECHNICIAN));
        log.info("Demo farm created with {} zones", farm.getZones().size());
    }
}
