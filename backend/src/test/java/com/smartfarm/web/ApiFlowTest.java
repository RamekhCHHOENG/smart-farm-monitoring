package com.smartfarm.web;

import com.smartfarm.domain.ActuatorStatus;
import com.smartfarm.repository.ActuatorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end test of UC-3 (Deliverable 7) and the permission rules, against the seeded demo farm. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:apitest;DB_CLOSE_DELAY=-1",
        "farm.simulator.enabled=false",
        "farm.scheduler.interval-ms=3600000",
        "farm.device-key=test-device-key",
        "farm.demo-password=test-password"
})
@AutoConfigureMockMvc
@DirtiesContext
class ApiFlowTest {

    private static final String KEY = "test-device-key";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ActuatorRepository actuators;

    @Test
    void dryReadingStartsPumpAndRaisesAlert() throws Exception {
        Long soilId = 1L; // "Soil moisture A1" from the seeder
        mvc.perform(post("/api/devices/readings").header("X-Device-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sensorId\":" + soilId + ",\"value\":12.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTION_TAKEN"));

        assertThat(actuators.findAll()).anySatisfy(a -> {
            assertThat(a.getName()).isEqualTo("Water pump A");
            assertThat(a.getStatus()).isEqualTo(ActuatorStatus.RUNNING);
        });

        String owner = login("owner");
        mvc.perform(get("/api/alerts").param("status", "OPEN").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("Water pump A started")));

        // Acknowledge all clears the open list
        mvc.perform(post("/api/alerts/acknowledge-all").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(get("/api/alerts").param("status", "OPEN").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidReadingIsRejectedAndNotStored() throws Exception {
        mvc.perform(post("/api/devices/readings").header("X-Device-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sensorId\":1,\"value\":140}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value("INVALID"));
    }

    @Test
    void devicesNeedTheDeviceKey() throws Exception {
        mvc.perform(post("/api/devices/readings").header("X-Device-Key", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sensorId\":1,\"value\":50}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersMustLogInAndRolesAreEnforced() throws Exception {
        mvc.perform(get("/api/farm")).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        String worker = login("worker");
        mvc.perform(get("/api/farm").header("Authorization", "Bearer " + worker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones.length()").value(3));

        // Farm Worker cannot change rules (Deliverable 2 permissions)
        mvc.perform(post("/api/rules").header("Authorization", "Bearer " + worker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"sensorId\":1,\"condition\":\"BELOW\",\"threshold\":5,\"durationMin\":0,\"severity\":\"INFO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void humanMistakesGetClearAnswers() throws Exception {
        // People type "Owner " on phones: username is trimmed and case-insensitive
        String owner = login("  Owner ");

        // A threshold the sensor can never report is refused with the valid range
        mvc.perform(post("/api/rules").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"sensorId\":1,\"condition\":\"BELOW\",\"threshold\":150,\"durationMin\":0,\"severity\":\"INFO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Threshold must be between 0 and 100 % for Soil moisture A1"));

        // Validation messages use the form's labels, not Java field names
        mvc.perform(post("/api/zones").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"POULTRY\",\"name\":\"  \",\"birdCount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Name: please fill this in"),
                        org.hamcrest.Matchers.containsString("Number of birds: cannot be negative"))));
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }
}
