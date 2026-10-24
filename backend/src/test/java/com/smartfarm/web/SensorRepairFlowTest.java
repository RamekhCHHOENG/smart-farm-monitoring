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
 * A technician can take a broken sensor out of service, repair it and bring it back, and the new
 * device kinds work end to end. Uses the seeded demo farm:
 * sensors 1 soil, 3 house temperature, 5 humidity, 6 light, 7 tank; actuators 1 pump, 4 heater,
 * 5 mister, 6 grow light, 7 tank valve; zones 1 bed, 2 chicken house, 3 greenhouse.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sensorrepair;DB_CLOSE_DELAY=-1",
        "farm.simulator.enabled=false",
        "farm.scheduler.interval-ms=3600000",
        "farm.device-key=test-device-key",
        "farm.demo-password=test-password"
})
@AutoConfigureMockMvc
@DirtiesContext
class SensorRepairFlowTest {

    private static final String KEY = "test-device-key";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ActuatorRepository actuators;

    @Test
    void aBrokenSensorIsIgnoredRepairedAndTrustedAgain() throws Exception {
        String technician = login("technician");

        // Reported faulty: its readings no longer start the pump, and the farm is told why
        lifecycle(technician, 1, "FAULT").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAULT"));
        mvc.perform(get("/api/alerts").param("status", "OPEN").header("Authorization", "Bearer " + technician))
                .andExpect(jsonPath("$[*].message", hasItem(containsString("Soil moisture A1 is faulty"))));
        reading(1, 12.5).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("IGNORED"));
        assertThat(pumpStatus()).isEqualTo(ActuatorStatus.IDLE);

        // A repair cannot be finished before it starts
        lifecycle(technician, 1, "COMPLETE_REPAIR").andExpect(status().isConflict());

        // Under repair: readings are kept for the test, but the rules stay paused
        lifecycle(technician, 1, "START_REPAIR").andExpect(jsonPath("$.status").value("MAINTENANCE"));
        reading(1, 12.5).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OK"));
        assertThat(pumpStatus()).isEqualTo(ActuatorStatus.IDLE);

        // The sensor proved itself with a valid reading: back in service
        lifecycle(technician, 1, "COMPLETE_REPAIR").andExpect(jsonPath("$.status").value("ONLINE"));
        reading(1, 12.5).andExpect(jsonPath("$.status").value("ACTION_TAKEN"));
        assertThat(pumpStatus()).isEqualTo(ActuatorStatus.RUNNING);

        // The repair left a trail in the zone's device log
        mvc.perform(get("/api/zones/1/logs").header("Authorization", "Bearer " + technician))
                .andExpect(jsonPath("$[?(@.action=='COMPLETE_REPAIR')].detail").value("test passed"))
                .andExpect(jsonPath("$[?(@.action=='FAULT')].actuatorName").value("Soil moisture A1"));
    }

    @Test
    void completingARepairWithoutANewReadingFails() throws Exception {
        String technician = login("technician");
        lifecycle(technician, 3, "FAULT");
        lifecycle(technician, 3, "START_REPAIR");

        lifecycle(technician, 3, "COMPLETE_REPAIR").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
        mvc.perform(get("/api/zones/2/logs").header("Authorization", "Bearer " + technician))
                .andExpect(jsonPath("$[?(@.action=='COMPLETE_REPAIR')].detail", hasItem(containsString("test failed"))));

        // An impossible value is not a proof either
        reading(3, 140).andExpect(status().isUnprocessableContent());
        lifecycle(technician, 3, "COMPLETE_REPAIR").andExpect(jsonPath("$.status").value("MAINTENANCE"));

        reading(3, 27.0);
        lifecycle(technician, 3, "COMPLETE_REPAIR").andExpect(jsonPath("$.status").value("ONLINE"));
    }

    @Test
    void onlyATechnicianOrOwnerRepairsSensors() throws Exception {
        lifecycle(login("worker"), 1, "FAULT").andExpect(status().isForbidden());
        // Sensor 4 (feed tray) is used by no other test here, so it can stay in repair
        lifecycle(login("owner"), 4, "FAULT").andExpect(status().isOk());
        lifecycle(login("technician"), 4, "START_REPAIR").andExpect(status().isOk());
        lifecycle(login("technician"), 999, "FAULT").andExpect(status().isNotFound());
    }

    @Test
    void theNewDeviceKindsRunTheirDefaultRules() throws Exception {
        // Cold chicken house: the heater starts
        reading(3, 10.0).andExpect(jsonPath("$.status").value("ACTION_TAKEN"));
        assertThat(actuators.findById(4L).orElseThrow().getStatus()).isEqualTo(ActuatorStatus.RUNNING);

        // Greenhouse: dry air starts the mister, an empty tank opens the valve, darkness switches the light on
        reading(5, 20.0).andExpect(jsonPath("$.status").value("ACTION_TAKEN"));
        reading(7, 12.0).andExpect(jsonPath("$.status").value("ACTION_TAKEN"));
        reading(6, 500.0).andExpect(jsonPath("$.status").value("ACTION_TAKEN"));
        assertThat(actuators.findById(5L).orElseThrow().getStatus()).isEqualTo(ActuatorStatus.RUNNING);
        assertThat(actuators.findById(6L).orElseThrow().getStatus()).isEqualTo(ActuatorStatus.RUNNING);
        assertThat(actuators.findById(7L).orElseThrow().getStatus()).isEqualTo(ActuatorStatus.RUNNING);

        // The ESP32 polling a heater relay sees it on
        mvc.perform(get("/api/devices/actuators/4/state").header("X-Device-Key", KEY))
                .andExpect(jsonPath("$.relayOn").value(true));
    }

    @Test
    void aTechnicianRegistersTheNewDeviceKinds() throws Exception {
        String technician = login("technician");
        postJson(technician, "/api/zones/3/actuators", "{\"type\":\"HEATER\",\"name\":\"Seedling heater\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("HEATER"))
                .andExpect(jsonPath("$.maxRunMinutes").value(30));
        postJson(technician, "/api/zones/3/sensors", "{\"type\":\"LIGHT_LEVEL\",\"name\":\"Roof light\",\"simulated\":false}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unit").value("lux"))
                .andExpect(jsonPath("$.status").value("ONLINE"));
    }

    private ResultActions lifecycle(String token, long sensorId, String action) throws Exception {
        return mvc.perform(post("/api/sensors/" + sensorId + "/lifecycle/" + action).header("Authorization", "Bearer " + token));
    }

    private ResultActions reading(long sensorId, double value) throws Exception {
        return mvc.perform(post("/api/devices/readings")
                .header("X-Device-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensorId\":" + sensorId + ",\"value\":" + value + "}"));
    }

    private ResultActions postJson(String token, String path, String json) throws Exception {
        return mvc.perform(post(path)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ActuatorStatus pumpStatus() {
        return actuators.findById(1L).orElseThrow().getStatus();
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }
}
