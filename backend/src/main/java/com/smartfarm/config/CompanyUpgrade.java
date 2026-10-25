package com.smartfarm.config;

import com.smartfarm.domain.Alert;
import com.smartfarm.domain.AppUser;
import com.smartfarm.domain.ControllerBoard;
import com.smartfarm.domain.Farm;
import com.smartfarm.repository.AlertRepository;
import com.smartfarm.repository.AppUserRepository;
import com.smartfarm.repository.ControllerBoardRepository;
import com.smartfarm.repository.FarmRepository;
import com.smartfarm.repository.ZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * Brings a database from the one-farm days into the company model, safely on every start:
 * alerts and controller boards made before farms were kept apart are given their farm, and the company gets its
 * first admin account (username "admin") if it has none. The admin's password comes from FARM_ADMIN_PASSWORD,
 * else FARM_DEMO_PASSWORD, else a random one printed once to the log. No password is committed.
 */
@Component
@Order(150)
public class CompanyUpgrade implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanyUpgrade.class);

    private final FarmRepository farms;
    private final ZoneRepository zones;
    private final AlertRepository alerts;
    private final ControllerBoardRepository boards;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String adminPassword;
    private final String demoPassword;

    public CompanyUpgrade(FarmRepository farms, ZoneRepository zones, AlertRepository alerts, ControllerBoardRepository boards,
                          AppUserRepository users, PasswordEncoder encoder,
                          @Value("${farm.admin-password:}") String adminPassword,
                          @Value("${farm.demo-password:}") String demoPassword) {
        this.farms = farms;
        this.zones = zones;
        this.alerts = alerts;
        this.boards = boards;
        this.users = users;
        this.encoder = encoder;
        this.adminPassword = adminPassword;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Farm first = farms.findFirstByOrderByIdAsc().orElse(null);
        if (first != null) {
            List<Alert> orphans = alerts.findByFarmIdIsNull();
            for (Alert a : orphans) {
                Long farmId = a.getZoneId() == null ? null : zones.findById(a.getZoneId()).map(z -> z.getFarm().getId()).orElse(null);
                a.setFarmId(farmId != null ? farmId : first.getId());
            }
            List<ControllerBoard> loose = boards.findByFarmIsNull();
            loose.forEach(b -> b.setFarm(first));
            if (!orphans.isEmpty() || !loose.isEmpty()) {
                log.info("Gave {} older alerts and {} controller boards their farm", orphans.size(), loose.size());
            }
        }
        if (!users.existsByCompanyRole("ADMIN") && users.findByUsername("admin").isEmpty()) {
            String password = !adminPassword.isBlank() ? adminPassword : demoPassword;
            if (password == null || password.isBlank()) {
                byte[] bytes = new byte[9];
                new SecureRandom().nextBytes(bytes);
                password = HexFormat.of().formatHex(bytes);
                log.warn("FARM_ADMIN_PASSWORD is not set. The company admin account 'admin' uses the generated password: {}", password);
            }
            users.save(AppUser.company("admin", "Smart Farm admin", encoder.encode(password), "ADMIN"));
            log.info("Created the company admin account 'admin'");
        }
    }
}
