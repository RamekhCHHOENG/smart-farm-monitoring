package com.smartfarm.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The company model: the Smart Farm company creates farms for its customers and invites their owners; every farm
 * sees only its own data; company support opens a farm only when assigned and while the owner allows it.
 * The farm-separation tests deliberately try to reach another farm's things by id and must get "not found".
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:company;DB_CLOSE_DELAY=-1",
        "farm.simulator.enabled=false",
        "farm.scheduler.interval-ms=3600000",
        "farm.device-key=test-device-key",
        "farm.demo-password=test-password"
})
@AutoConfigureMockMvc
@DirtiesContext
class CompanyFlowTest {

    @Autowired
    private MockMvc mvc;

    private static int farmCounter = 0;

    /** Farm B, made by the company for a second customer, with a zone, a sensor, a pump, a rule and a schedule. */
    private record FarmB(long farmId, String ownerToken, long ownerId, long zoneId, long sensorId, long pumpId, long ruleId, long taskId) {
    }

    private String admin;

    @BeforeEach
    void signInAdmin() throws Exception {
        admin = login("admin", "test-password");
    }

    @Test
    void theCompanyCreatesAFarmAndItsOwnerSetsAPasswordFromTheInviteLink() throws Exception {
        String body = send(post("/api/company/farms"), admin, null,
                "{\"name\":\"Kampot Pepper Farm\",\"location\":\"Kampot\",\"ownerName\":\"Srey Mom\",\"ownerUsername\":\"sreymom\",\"ownerContact\":\"012 345 678\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("sreymom"))
                .andExpect(jsonPath("$.farm.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.inviteToken");

        // the link shows who it is for, with no sign-in
        mvc.perform(get("/api/auth/invites/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Srey Mom"))
                .andExpect(jsonPath("$.farmName").value("Kampot Pepper Farm"));
        // a password nobody knows was set at creation: guessing does not sign in
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"sreymom\",\"password\":\"test-password\"}")).andExpect(status().isUnauthorized());

        String accepted = mvc.perform(post("/api/auth/invites/" + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pepper-farm-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String owner = JsonPath.read(accepted, "$.token");
        send(get("/api/farm"), owner, null, null)
                .andExpect(jsonPath("$.name").value("Kampot Pepper Farm"))
                .andExpect(jsonPath("$.zones.length()").value(0));
        // the link works once
        mvc.perform(post("/api/auth/invites/" + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"another-pass-1\"}")).andExpect(status().isNotFound());
        login("sreymom", "pepper-farm-1");
    }

    @Test
    void oneFarmCannotReachAnotherFarmsThingsByTheirIds() throws Exception {
        FarmB b = farmB();
        String ownerA = login("owner", "test-password");

        // reading and changing farm B's things from farm A: "not found", as if they did not exist
        send(get("/api/sensors/" + b.sensorId() + "/readings"), ownerA, null, null).andExpect(status().isNotFound());
        send(get("/api/sensors/" + b.sensorId() + "/readings.csv"), ownerA, null, null).andExpect(status().isNotFound());
        send(put("/api/sensors/" + b.sensorId()), ownerA, null, "{\"name\":\"stolen\"}").andExpect(status().isNotFound());
        send(get("/api/zones/" + b.zoneId() + "/logs"), ownerA, null, null).andExpect(status().isNotFound());
        send(post("/api/zones/" + b.zoneId() + "/sensors"), ownerA, null, "{\"type\":\"TEMPERATURE\",\"name\":\"x\"}").andExpect(status().isNotFound());
        send(delete("/api/zones/" + b.zoneId()), ownerA, null, null).andExpect(status().isNotFound());
        send(post("/api/actuators/" + b.pumpId() + "/on"), ownerA, null, "{\"durationMin\":1}").andExpect(status().isNotFound());
        send(get("/api/actuators/" + b.pumpId() + "/logs"), ownerA, null, null).andExpect(status().isNotFound());
        send(delete("/api/rules/" + b.ruleId()), ownerA, null, null).andExpect(status().isNotFound());
        send(delete("/api/tasks/" + b.taskId()), ownerA, null, null).andExpect(status().isNotFound());
        send(delete("/api/users/" + b.ownerId()), ownerA, null, null).andExpect(status().isNotFound());

        // lists show only the farm's own things
        send(get("/api/rules"), ownerA, null, null).andExpect(jsonPath("$[*].id", not(hasItem((int) b.ruleId()))));
        send(get("/api/tasks"), ownerA, null, null).andExpect(jsonPath("$[*].id", not(hasItem((int) b.taskId()))));
        send(get("/api/users"), ownerA, null, null).andExpect(jsonPath("$[*].username", not(hasItem(startsWith("ownerb")))));
        send(get("/api/farm"), ownerA, null, null).andExpect(jsonPath("$.zones[*].id", not(hasItem((int) b.zoneId()))));
        send(get("/api/farm"), b.ownerToken(), null, null)
                .andExpect(jsonPath("$.zones.length()").value(1))
                .andExpect(jsonPath("$.zones[0].id").value((int) b.zoneId()));
        send(get("/api/rules"), b.ownerToken(), null, null).andExpect(jsonPath("$.length()").value(1));

        // controller boards: farm B's board is invisible to farm A and cannot be used there
        String boardB = send(post("/api/controllers"), admin, b.farmId(), "{\"name\":\"B node\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int boardId = ((Number) JsonPath.read(boardB, "$.board.id")).intValue();
        send(get("/api/controllers"), ownerA, null, null).andExpect(jsonPath("$[*].id", not(hasItem(boardId))));
        send(get("/api/controllers/" + boardId + "/firmware"), ownerA, null, null).andExpect(status().isNotFound());
        send(delete("/api/controllers/" + boardId), ownerA, null, null).andExpect(status().isNotFound());
        send(get("/api/controllers"), b.ownerToken(), null, null).andExpect(jsonPath("$[*].id", hasItem(boardId)));

        // an alert on farm B (a board's dry reading) stays on farm B
        mvc.perform(post("/api/devices/readings").header("X-Device-Key", "test-device-key").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensorId\":" + b.sensorId() + ",\"value\":5}")).andExpect(status().isCreated());
        String alertsB = send(get("/api/alerts"), b.ownerToken(), null, null)
                .andExpect(jsonPath("$[*].zoneId", everyItem(is((int) b.zoneId()))))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                .andReturn().getResponse().getContentAsString();
        int alertB = ((List<Integer>) JsonPath.read(alertsB, "$[*].id")).get(0);
        send(get("/api/alerts"), ownerA, null, null).andExpect(jsonPath("$[*].id", not(hasItem(alertB))));
        send(post("/api/alerts/" + alertB + "/acknowledge"), ownerA, null, null).andExpect(status().isNotFound());
    }

    @Test
    void supportOpensAFarmOnlyWhenAssignedAndWhileTheOwnerAllowsIt() throws Exception {
        String body = send(post("/api/company/staff"), admin, null, "{\"username\":\"vibol.support\",\"name\":\"Vibol\",\"companyRole\":\"SUPPORT\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long staffId = ((Number) JsonPath.read(body, "$.staff.id")).longValue();
        String support = JsonPath.read(mvc.perform(post("/api/auth/invites/" + JsonPath.read(body, "$.inviteToken"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"support-pass-1\"}"))
                .andReturn().getResponse().getContentAsString(), "$.token");
        long farmA = 1;

        send(get("/api/farm"), support, farmA, null).andExpect(status().isForbidden()); // not assigned
        send(get("/api/company/farms"), support, null, null).andExpect(jsonPath("$.length()").value(0));

        send(put("/api/company/farms/" + farmA + "/support"), admin, null, "{\"staffIds\":[" + staffId + "]}")
                .andExpect(jsonPath("$.support[0].name").value("Vibol"));
        send(get("/api/farm"), support, farmA, null).andExpect(status().isOk());
        send(get("/api/company/farms"), support, null, null).andExpect(jsonPath("$.length()").value(1));
        // support works as a technician: it may repair, not delete zones or manage staff
        send(delete("/api/zones/1"), support, farmA, null).andExpect(status().isForbidden());
        send(get("/api/users"), support, farmA, null).andExpect(status().isForbidden());

        String ownerA = login("owner", "test-password");
        send(put("/api/farm/support-access"), ownerA, null, "{\"allowed\":false}").andExpect(jsonPath("$.supportAllowed").value(false));
        send(get("/api/farm"), support, farmA, null).andExpect(status().isForbidden());
        send(put("/api/farm/support-access"), ownerA, null, "{\"allowed\":true}").andExpect(status().isOk());
        send(get("/api/farm"), support, farmA, null).andExpect(status().isOk());
        // only the farm's own owner decides, not company staff acting on the farm
        send(put("/api/farm/support-access"), admin, farmA, "{\"allowed\":false}").andExpect(status().isForbidden());
    }

    @Test
    void aSuspendedFarmCannotBeUsedUntilItIsReactivated() throws Exception {
        FarmB b = farmB();
        send(put("/api/company/farms/" + b.farmId() + "/status"), admin, null, "{\"status\":\"SUSPENDED\"}")
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        // suspending signs the farm's people out; signing in again works, but the farm does not
        String again = login(username(b), "owner-b-pass-1");
        send(get("/api/auth/me"), again, null, null).andExpect(status().isOk());
        send(get("/api/farm"), again, null, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("suspended")));
        send(put("/api/company/farms/" + b.farmId() + "/status"), admin, null, "{\"status\":\"ACTIVE\"}").andExpect(status().isOk());
        send(get("/api/farm"), again, null, null).andExpect(status().isOk());
    }

    @Test
    void companyPagesAreForCompanyStaffAndCompanyStaffChooseAFarmFirst() throws Exception {
        String ownerA = login("owner", "test-password");
        send(get("/api/company/farms"), ownerA, null, null).andExpect(status().isForbidden());
        send(post("/api/company/farms"), ownerA, null,
                "{\"name\":\"x\",\"ownerName\":\"x\",\"ownerUsername\":\"xyz\"}").andExpect(status().isForbidden());
        send(get("/api/farm"), admin, null, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Choose a farm first"));
        // the admin opens any farm as its owner, to set it up before handover
        send(get("/api/farm"), admin, 1L, null).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        send(get("/api/company/farms"), admin, null, null)
                .andExpect(jsonPath("$[*].name", hasItem("Group 1 Demo Farm")))
                .andExpect(jsonPath("$[?(@.id==1)].sensors").value(hasItem(greaterThan(0))));
    }

    // ----- helpers -----

    private FarmB farmB() throws Exception {
        int n = ++farmCounter;
        String created = send(post("/api/company/farms"), admin, null,
                "{\"name\":\"Farm B" + n + "\",\"ownerName\":\"Owner B\",\"ownerUsername\":\"ownerb" + n + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long farmId = ((Number) JsonPath.read(created, "$.farm.id")).longValue();
        String owner = JsonPath.read(mvc.perform(post("/api/auth/invites/" + JsonPath.read(created, "$.inviteToken"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"owner-b-pass-1\"}"))
                .andReturn().getResponse().getContentAsString(), "$.token");
        long ownerId = ((Number) JsonPath.read(send(get("/api/auth/me"), owner, null, null).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        long zoneId = id(send(post("/api/zones"), owner, null, "{\"type\":\"CROP\",\"name\":\"B bed\",\"cropName\":\"Pepper\"}"));
        // the company admin installs the devices, working on farm B
        long sensorId = id(send(post("/api/zones/" + zoneId + "/sensors"), admin, farmId, "{\"type\":\"SOIL_MOISTURE\",\"name\":\"B soil\",\"simulated\":false}"));
        long pumpId = id(send(post("/api/zones/" + zoneId + "/actuators"), admin, farmId, "{\"type\":\"WATER_PUMP\",\"name\":\"B pump\",\"maxRunMinutes\":5}"));
        long ruleId = id(send(post("/api/rules"), owner, null, "{\"name\":\"B dry\",\"sensorId\":" + sensorId
                + ",\"condition\":\"BELOW\",\"threshold\":20,\"actuatorId\":" + pumpId + ",\"durationMin\":2,\"severity\":\"WARNING\"}"));
        long taskId = id(send(post("/api/tasks"), owner, null, "{\"name\":\"B morning\",\"actuatorId\":" + pumpId
                + ",\"runTime\":\"06:00\",\"repeatDays\":[\"MONDAY\"],\"durationMin\":2}"));
        return new FarmB(farmId, owner, ownerId, zoneId, sensorId, pumpId, ruleId, taskId);
    }

    private static String username(FarmB b) {
        return "ownerb" + farmCounter;
    }

    private long id(ResultActions result) throws Exception {
        String body = result.andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Long farmId, String json) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (farmId != null) {
            request.header("X-Farm-Id", String.valueOf(farmId));
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
