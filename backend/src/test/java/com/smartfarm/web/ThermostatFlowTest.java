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
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keeping a temperature: the fan runs until the house is back to its stop value (not a fixed 5 minutes),
 * the fan and the heater never run together, and rules that would fight each other are refused.
 * Seeded demo farm: Chicken House 1 (zone 2) has house temperature #3, fan #2, heater #4, with the
 * default rules "Hot house" (above 32, stop at 29) and "Cold house" (below 18, stop at 21).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:thermostat;DB_CLOSE_DELAY=-1",
        "farm.simulator.enabled=false",
        "farm.scheduler.interval-ms=3600000",
        "farm.device-key=test-device-key",
        "farm.demo-password=test-password"
})
@AutoConfigureMockMvc
@DirtiesContext
class ThermostatFlowTest {

    private static final String KEY = "test-device-key";
    private static final long HOUSE_TEMP = 3, FAN = 2, HEATER = 4;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ActuatorRepository actuators;

    @Test
    void fanRunsUntilTheTargetAndNeverTogetherWithTheHeater() throws Exception {
        // Hot: the fan starts and says where it will stop
        reading(HOUSE_TEMP, 35).andExpect(jsonPath("$.actions[0]").value(containsString("until 29°C")));
        assertThat(statusOf(FAN)).isEqualTo(ActuatorStatus.RUNNING);

        // Cooling but not there yet: keeps running
        reading(HOUSE_TEMP, 31);
        assertThat(statusOf(FAN)).isEqualTo(ActuatorStatus.RUNNING);

        // Back to 29 °C: the rule stops its own run long before the time limit
        reading(HOUSE_TEMP, 28.5).andExpect(jsonPath("$.actions[0]").value(containsString("target reached")));
        assertThat(statusOf(FAN)).isEqualTo(ActuatorStatus.IDLE);
        String owner = login("owner");
        mvc.perform(get("/api/zones/2/logs").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$[*].detail", hasItem(containsString("target reached"))));

        // Cold night: the heater starts
        reading(HOUSE_TEMP, 10);
        assertThat(statusOf(HEATER)).isEqualTo(ActuatorStatus.RUNNING);

        // A worker starts the fan by hand: the heater is stopped first (interlock), and the log says why
        String worker = login("worker");
        mvc.perform(post("/api/actuators/" + FAN + "/on").header("Authorization", "Bearer " + worker)
                .contentType(MediaType.APPLICATION_JSON).content("{\"durationMin\":5}")).andExpect(status().isOk());
        assertThat(statusOf(FAN)).isEqualTo(ActuatorStatus.RUNNING);
        assertThat(statusOf(HEATER)).isEqualTo(ActuatorStatus.IDLE);
        mvc.perform(get("/api/zones/2/logs").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$[*].detail", hasItem(containsString("interlock"))));

        // A person's run is never stopped by a rule's target
        reading(HOUSE_TEMP, 28);
        assertThat(statusOf(FAN)).isEqualTo(ActuatorStatus.RUNNING);
    }

    @Test
    void rulesThatWouldFightAreRefused() throws Exception {
        String technician = login("technician");
        String owner = login("owner");
        // Vegetable Bed A (zone 1) gets its own heater and fan for air temperature #2
        long heater = idOf(postJson(technician, "/api/zones/1/actuators", "{\"type\":\"HEATER\",\"name\":\"Bed heater\"}"));
        long fan = idOf(postJson(technician, "/api/zones/1/actuators", "{\"type\":\"FAN\",\"name\":\"Bed fan\"}"));

        rule(owner, "Bed cold", "BELOW", 20, heater, "24").andExpect(status().isCreated())
                .andExpect(jsonPath("$.stopAt").value(24.0));

        // The heater would keep heating to 24 °C, past where this fan starts (23 °C)
        rule(owner, "Bed hot", "ABOVE", 23, fan, "21").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("would fight")));
        // The fan would cool down to 19 °C, past where the heater starts (20 °C)
        rule(owner, "Bed hot", "ABOVE", 30, fan, "19").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("would fight")));
        // A stop value on the wrong side of the threshold
        rule(owner, "Bed hot", "ABOVE", 30, fan, "31").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("must be below 30")));
        // A quiet band between 24 and 30 °C: accepted
        rule(owner, "Bed hot", "ABOVE", 30, fan, "26").andExpect(status().isCreated());
        // A fixed-time rule (no stop value) is still allowed
        rule(owner, "Bed hot timed", "ABOVE", 33, fan, "null").andExpect(status().isCreated())
                .andExpect(jsonPath("$.stopAt").doesNotExist());
    }

    private ResultActions rule(String token, String name, String condition, double threshold, long actuatorId, String stopAt)
            throws Exception {
        return postJson(token, "/api/rules", ("{\"name\":\"%s\",\"sensorId\":2,\"condition\":\"%s\",\"threshold\":%s,"
                + "\"actuatorId\":%d,\"durationMin\":10,\"severity\":\"WARNING\",\"stopAt\":%s}")
                .formatted(name, condition, threshold, actuatorId, stopAt));
    }

    private ActuatorStatus statusOf(long id) {
        return actuators.findById(id).orElseThrow().getStatus();
    }

    private long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private ResultActions reading(long sensorId, double value) throws Exception {
        return mvc.perform(post("/api/devices/readings").header("X-Device-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensorId\":" + sensorId + ",\"value\":" + value + "}"));
    }

    private ResultActions postJson(String token, String path, String json) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }
}
