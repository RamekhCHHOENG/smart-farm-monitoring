package com.smartfarm.web;

import com.jayway.jsonpath.JsonPath;
import com.smartfarm.repository.AutomationRuleRepository;
import com.smartfarm.repository.ScheduledTaskRepository;
import com.smartfarm.repository.SensorReadingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The path a group follows for a real installation: empty farm, set it up, register a real
 * (non-simulated) sensor and a pump, send readings like the ESP32 does, then clean up.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:realsetup;DB_CLOSE_DELAY=-1",
        "farm.demo-data=false",
        "farm.simulator.enabled=false",
        "farm.scheduler.interval-ms=3600000",
        "farm.device-key=test-device-key",
        "farm.demo-password=test-password"
})
@AutoConfigureMockMvc
@DirtiesContext
class RealSetupFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SensorReadingRepository readings;

    @Autowired
    private AutomationRuleRepository rules;

    @Autowired
    private ScheduledTaskRepository tasks;

    @Test
    void ownerSetsUpFarmTechnicianAddsRealDeviceAndDeviceDrivesThePump() throws Exception {
        String owner = login("owner", "test-password");
        String tech = login("technician", "test-password");

        // Clean start: farm exists, no zones
        call(get("/api/farm"), owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.zones.length()").value(0));

        // UC-1: owner names the farm and adds a zone
        call(put("/api/farm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sokha Farm\",\"location\":\"Takeo\"}"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sokha Farm"));
        long zoneId = id(call(post("/api/zones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CROP\",\"name\":\"Test bed\",\"cropName\":\"Basil\"}"), owner)
                .andExpect(status().isCreated()));

        // UC-2: technician registers a REAL sensor and a pump
        long sensorId = id(call(post("/api/zones/" + zoneId + "/sensors").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"SOIL_MOISTURE\",\"name\":\"ESP32 soil\",\"simulated\":false}"), tech)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulated").value(false)));
        long pumpId = id(call(post("/api/zones/" + zoneId + "/actuators").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"WATER_PUMP\",\"name\":\"Mini pump\",\"maxRunMinutes\":2}"), tech)
                .andExpect(status().isCreated()));

        // UC-4: owner adds a rule; UC-5: and a schedule
        call(post("/api/rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Dry\",\"sensorId\":" + sensorId + ",\"condition\":\"BELOW\",\"threshold\":30,"
                        + "\"actuatorId\":" + pumpId + ",\"durationMin\":1,\"severity\":\"WARNING\"}"), owner)
                .andExpect(status().isCreated());
        call(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Water\",\"actuatorId\":" + pumpId + ",\"runTime\":\"17:00\","
                        + "\"repeatDays\":[\"MONDAY\"],\"durationMin\":1}"), owner)
                .andExpect(status().isCreated());

        // The ESP32: a wet reading does nothing, the pump stays off
        device(post("/api/devices/readings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensorId\":" + sensorId + ",\"value\":55}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OK"));
        device(get("/api/devices/actuators/" + pumpId + "/state"))
                .andExpect(jsonPath("$.relayOn").value(false));

        // A dry reading starts the pump, and the ESP32 sees relayOn = true
        device(post("/api/devices/readings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensorId\":" + sensorId + ",\"value\":18}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTION_TAKEN"));
        device(get("/api/devices/actuators/" + pumpId + "/state"))
                .andExpect(jsonPath("$.relayOn").value(true));

        // Composition: deleting the zone removes devices, readings, rules and schedules
        assertThat(readings.count()).isEqualTo(2);
        call(delete("/api/zones/" + zoneId), owner).andExpect(status().isNoContent());
        assertThat(readings.count()).isZero();
        assertThat(rules.count()).isZero();
        assertThat(tasks.count()).isZero();
        call(get("/api/farm"), owner).andExpect(jsonPath("$.zones.length()").value(0));
    }

    @Test
    void workerCannotEditFarmAndCanChangeOwnPassword() throws Exception {
        String worker = login("worker", "test-password");
        call(put("/api/farm").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"), worker)
                .andExpect(status().isForbidden());

        call(post("/api/auth/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong-pass\",\"newPassword\":\"new-secret-1\"}"), worker)
                .andExpect(status().isBadRequest());
        call(post("/api/auth/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"test-password\",\"newPassword\":\"short\"}"), worker)
                .andExpect(status().isBadRequest());
        call(post("/api/auth/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"test-password\",\"newPassword\":\"new-secret-1\"}"), worker)
                .andExpect(status().isNoContent());

        login("worker", "new-secret-1");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"worker\",\"password\":\"test-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions device(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("X-Device-Key", "test-device-key"));
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
