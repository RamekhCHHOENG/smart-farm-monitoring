package com.smartfarm.web;

import com.jayway.jsonpath.JsonPath;
import com.smartfarm.domain.Actuator;
import com.smartfarm.repository.ActuatorRepository;
import com.smartfarm.repository.AutomationRuleRepository;
import com.smartfarm.service.ActuatorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The "did it work?" check (a pump that runs while the pipe is clogged), the Farm Lab's device API that needs
 * no device key, and lab alerts that stay out of the farm's list. Seeded demo farm: soil #1, pump #1 in zone 1.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:labeffect;DB_CLOSE_DELAY=-1",
        "farm.simulator.enabled=false",
        "farm.scheduler.interval-ms=3600000",
        "farm.device-key=test-device-key",
        "farm.demo-password=test-password"
})
@AutoConfigureMockMvc
@DirtiesContext
class LabAndEffectTest {

    private static final String KEY = "test-device-key";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ActuatorRepository actuators;

    @Autowired
    private ActuatorService actuatorService;

    @Autowired
    private AutomationRuleRepository rules;

    @Test
    void aRunThatChangesNothingRaisesAnAlertAndOneThatWorksDoesNot() throws Exception {
        String owner = login("owner");

        // Dry soil starts the pump; the soil barely moves during the whole run (clogged pipe)
        reading(1, 12.5).andExpect(jsonPath("$.status").value("ACTION_TAKEN"));
        reading(1, 12.6);
        expireRun(1);
        actuatorService.stopExpiredRuns();
        alerts(owner, "FARM").andExpect(jsonPath("$[*].message",
                hasItem(containsString("Water pump A ran its full time but Soil moisture A1 did not rise (12.5 → 12.6%). Check the pump and the pipe"))));

        // A working run: the soil rose, nothing to report. (Cooldown passed: rule fires again.)
        endCooldown(1);
        acknowledgeAll(owner);
        reading(1, 13.0).andExpect(jsonPath("$.status").value("ACTION_TAKEN"));
        reading(1, 25.0);
        expireRun(1);
        actuatorService.stopExpiredRuns();
        alerts(owner, "FARM").andExpect(jsonPath("$[*].message", not(hasItem(containsString("did not rise")))));
    }

    @Test
    void theFarmLabDrivesLabDevicesWithoutTheDeviceKeyAndKeepsItsAlerts() throws Exception {
        String owner = login("owner");
        String technician = login("technician");
        long zone = idOf(postJson(owner, "/api/zones", "{\"type\":\"CROP\",\"name\":\"Test lab bed\",\"lab\":true}")
                .andExpect(jsonPath("$.lab").value(true)));
        long sensor = idOf(postJson(technician, "/api/zones/" + zone + "/sensors",
                "{\"type\":\"SOIL_MOISTURE\",\"name\":\"Test lab soil\",\"simulated\":false}"));
        long pump = idOf(postJson(technician, "/api/zones/" + zone + "/actuators",
                "{\"type\":\"WATER_PUMP\",\"name\":\"Test lab pump\",\"maxRunMinutes\":2}"));
        postJson(owner, "/api/rules", "{\"name\":\"Test lab dry\",\"sensorId\":" + sensor + ",\"condition\":\"BELOW\","
                + "\"threshold\":30,\"actuatorId\":" + pump + ",\"durationMin\":1,\"severity\":\"WARNING\"}").andExpect(status().isCreated());

        // No device key: the technician's sign-in is enough for a lab device
        postJson(technician, "/api/lab/readings", "{\"sensorId\":" + sensor + ",\"value\":20}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTION_TAKEN"));
        postJson(technician, "/api/lab/readings", "{\"sensorId\":" + sensor + ",\"value\":150}")
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/lab/actuators/" + pump + "/state").header("Authorization", "Bearer " + technician))
                .andExpect(jsonPath("$.relayOn").value(true));

        // Not for real farm devices, not for a worker, not without signing in
        postJson(technician, "/api/lab/readings", "{\"sensorId\":1,\"value\":20}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("real farm device")));
        postJson(login("worker"), "/api/lab/readings", "{\"sensorId\":" + sensor + ",\"value\":20}").andExpect(status().isForbidden());
        mvc.perform(post("/api/lab/readings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensorId\":" + sensor + ",\"value\":20}")).andExpect(status().isUnauthorized());

        // The lab's alert stays in the lab: not in the farm's list or count, not sent to phones
        alerts(owner, "LAB").andExpect(jsonPath("$[*].message", hasItem(containsString("Test lab soil"))))
                .andExpect(jsonPath("$[0].lab").value(true)).andExpect(jsonPath("$[0].delivered").value(false));
        alerts(owner, "FARM").andExpect(jsonPath("$[*].message", not(hasItem(containsString("Test lab soil")))));
        mvc.perform(get("/api/farm").header("Authorization", "Bearer " + owner)).andExpect(jsonPath("$.openAlerts").value(0));
    }

    private void endCooldown(long sensorId) {
        rules.findBySensor_Id(sensorId).forEach(r -> {
            ReflectionTestUtils.setField(r, "lastTriggeredAt", null);
            rules.save(r);
        });
    }

    private void expireRun(long actuatorId) {
        Actuator a = actuators.findById(actuatorId).orElseThrow();
        ReflectionTestUtils.setField(a, "runningUntil", Instant.now().minusSeconds(1));
        actuators.save(a);
    }

    private void acknowledgeAll(String token) throws Exception {
        mvc.perform(post("/api/alerts/acknowledge-all").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    private ResultActions alerts(String token, String scope) throws Exception {
        return mvc.perform(get("/api/alerts").param("status", "OPEN").param("scope", scope).header("Authorization", "Bearer " + token));
    }

    private ResultActions reading(long sensorId, double value) throws Exception {
        return mvc.perform(post("/api/devices/readings").header("X-Device-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensorId\":" + sensorId + ",\"value\":" + value + "}"));
    }

    private ResultActions postJson(String token, String path, String json) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
