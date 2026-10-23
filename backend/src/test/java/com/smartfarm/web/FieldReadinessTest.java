package com.smartfarm.web;

import com.jayway.jsonpath.JsonPath;
import com.smartfarm.domain.Severity;
import com.smartfarm.service.AlertService;
import com.smartfarm.service.DataRetentionService;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Features added after the first full test round: accounts, editing, export, and protection against misuse. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:readiness;DB_CLOSE_DELAY=-1",
        "farm.simulator.enabled=false",
        "farm.scheduler.interval-ms=3600000",
        "farm.device-key=test-device-key",
        "farm.demo-password=test-password",
        "farm.login.max-failures=3"
})
@AutoConfigureMockMvc
@DirtiesContext
class FieldReadinessTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AlertService alerts;

    @Autowired
    private DataRetentionService retention;

    @Test
    void repeatedWrongPasswordsLockTheUsernameButNotOthers() throws Exception {
        String owner = login("owner", "test-password");
        long id = id(call(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"guessme\",\"name\":\"Target\",\"role\":\"WORKER\",\"password\":\"right-pass-1\"}"), owner)
                .andExpect(status().isCreated()));
        for (int i = 0; i < 3; i++) {
            loginAttempt("guessme", "wrong").andExpect(status().isUnauthorized());
        }
        // Even the right password is refused while locked
        loginAttempt("guessme", "right-pass-1").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(containsString("Wait")));
        // The owner's password reset unlocks the account
        call(post("/api/users/" + id + "/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"fresh-pass-3\"}"), owner).andExpect(status().isNoContent());
        login("guessme", "fresh-pass-3");
        login("owner", "test-password"); // other users are not affected
    }

    @Test
    void ownerManagesStaffAccounts() throws Exception {
        String owner = login("owner", "test-password");

        long id = id(call(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\" Srey.Mom \",\"name\":\"Srey Mom\",\"role\":\"WORKER\",\"password\":\"field-pass-1\"}"), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("srey.mom")));
        call(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"srey.mom\",\"name\":\"Copy\",\"role\":\"WORKER\",\"password\":\"field-pass-1\"}"), owner)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("already taken")));
        call(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"a b\",\"name\":\"Bad\",\"role\":\"WORKER\",\"password\":\"field-pass-1\"}"), owner)
                .andExpect(status().isBadRequest());

        String worker = login("srey.mom", "field-pass-1");
        call(get("/api/users"), worker).andExpect(status().isForbidden());

        // Reset ends the worker's session and the new password works
        call(post("/api/users/" + id + "/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"new-pass-22\"}"), owner).andExpect(status().isNoContent());
        call(get("/api/auth/me"), worker).andExpect(status().isUnauthorized());
        login("srey.mom", "new-pass-22");

        call(put("/api/users/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Srey Mom\",\"role\":\"TECHNICIAN\"}"), owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TECHNICIAN"));

        long ownerId = ((Number) JsonPath.read(call(get("/api/auth/me"), owner).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        call(delete("/api/users/" + ownerId), owner).andExpect(status().isBadRequest());
        call(put("/api/users/" + ownerId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sokha\",\"role\":\"WORKER\"}"), owner)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("at least one Farm Owner")));

        call(delete("/api/users/" + id), owner).andExpect(status().isNoContent());
        loginAttempt("srey.mom", "new-pass-22").andExpect(status().isUnauthorized());
    }

    @Test
    void schedulesAndDevicesCanBeEditedInPlace() throws Exception {
        String owner = login("owner", "test-password");
        String tech = login("technician", "test-password");
        String worker = login("worker", "test-password");
        String tasks = call(get("/api/tasks"), owner).andReturn().getResponse().getContentAsString();
        int taskId = JsonPath.read(tasks, "$[0].id");
        int actuatorId = JsonPath.read(tasks, "$[0].actuatorId");

        call(put("/api/tasks/" + taskId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Early feeding\",\"actuatorId\":" + actuatorId + ",\"runTime\":\"06:30\",\"repeatDays\":[\"MONDAY\"],\"durationMin\":2}"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Early feeding"))
                .andExpect(jsonPath("$.runTime").value("06:30:00"));
        call(put("/api/tasks/" + taskId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Too long\",\"actuatorId\":" + actuatorId + ",\"runTime\":\"06:30\",\"repeatDays\":[\"MONDAY\"],\"durationMin\":999}"), owner)
                .andExpect(status().isBadRequest());

        call(put("/api/sensors/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bed A soil (north)\"}"), tech)
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Bed A soil (north)"));
        call(put("/api/sensors/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"), worker)
                .andExpect(status().isForbidden());
        call(put("/api/actuators/" + actuatorId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Feeder (new motor)\",\"maxRunMinutes\":8}"), tech)
                .andExpect(status().isOk()).andExpect(jsonPath("$.maxRunMinutes").value(8));
    }

    @Test
    void sameOpenProblemIsCountedOnOneAlert() {
        var first = alerts.raise("Test zone: pump reported a fault", Severity.CRITICAL, null, null);
        var second = alerts.raise("Test zone: pump reported a fault", Severity.CRITICAL, null, null);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getRepeatCount()).isEqualTo(2);
    }

    @Test
    void readingsExportAsSpreadsheetAndOldOnesAreCleanedUp() throws Exception {
        String owner = login("owner", "test-password");
        mvc.perform(post("/api/devices/readings").header("X-Device-Key", "test-device-key")
                .contentType(MediaType.APPLICATION_JSON).content("{\"sensorId\":2,\"value\":27.5}"))
                .andExpect(status().isCreated());
        call(get("/api/sensors/2/readings.csv"), owner)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("sensor-2-readings.csv")))
                .andExpect(content().string(startsWith("time,value\n")))
                .andExpect(content().string(containsString(",27.5")));
        assertThat(retention.deleteOldReadings()).isZero(); // nothing is 90 days old yet
    }

    @Test
    void tooLongNamesGetAPlainMessage() throws Exception {
        String owner = login("owner", "test-password");
        call(post("/api/zones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CROP\",\"name\":\"" + "A".repeat(81) + "\"}"), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Name: use at most 80 characters"));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private ResultActions loginAttempt(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private String login(String username, String password) throws Exception {
        String body = loginAttempt(username, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
