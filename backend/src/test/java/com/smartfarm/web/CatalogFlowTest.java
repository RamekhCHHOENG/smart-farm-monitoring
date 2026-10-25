package com.smartfarm.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The device catalog (master data): seeded measurement types and models, devices installed from a model
 * (several readings per device), measurements and actuator kinds added without code, pictures,
 * controller boards with their own keys, and who may change the catalog.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog;DB_CLOSE_DELAY=-1",
        "farm.simulator.enabled=false",
        "farm.scheduler.interval-ms=3600000",
        "farm.device-key=test-device-key",
        "farm.demo-password=test-password"
})
@AutoConfigureMockMvc
@DirtiesContext
class CatalogFlowTest {

    private static final String KEY = "test-device-key";

    @Autowired
    private MockMvc mvc;

    @Test
    void theStarterCatalogIsThereAndExistingDevicesWereGivenAModel() throws Exception {
        String worker = login("worker");
        mvc.perform(get("/api/catalog/measurements").header("Authorization", "Bearer " + worker))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(15)))
                .andExpect(jsonPath("$[?(@.code=='CO2')].unit").value("ppm"));
        mvc.perform(get("/api/catalog/models").header("Authorization", "Bearer " + worker))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(40)))
                .andExpect(jsonPath("$[?(@.name=='DHT22 temperature & humidity sensor')].wokwiPart").value("dht22"));
        // The demo farm's soil sensor #1 now belongs to a device made from the capacitive soil sensor model
        mvc.perform(get("/api/farm").header("Authorization", "Bearer " + worker))
                .andExpect(jsonPath("$.zones[0].sensors[0].deviceId").isNumber())
                .andExpect(jsonPath("$.zones[0].devices[0].modelName").value("Capacitive soil moisture sensor v1.2"));
    }

    @Test
    void aDht22GivesTwoReadingsAndRemovingItRemovesBoth() throws Exception {
        String tech = login("technician");
        long dht = modelId(tech, "DHT22 temperature & humidity sensor");
        String body = postJson(tech, "/api/zones/2/devices", "{\"modelId\":" + dht + ",\"name\":\"House sensor 2\",\"simulated\":false}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sensorIds.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        long deviceId = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(get("/api/farm").header("Authorization", "Bearer " + tech))
                .andExpect(jsonPath("$.zones[1].sensors[*].name", hasItems("House sensor 2 · Temperature", "House sensor 2 · Humidity")));

        mvc.perform(delete("/api/farm-devices/" + deviceId).header("Authorization", "Bearer " + tech)).andExpect(status().isNoContent());
        mvc.perform(get("/api/farm").header("Authorization", "Bearer " + tech))
                .andExpect(jsonPath("$.zones[1].sensors[*].name", not(hasItem(containsString("House sensor 2")))));
    }

    @Test
    void aNewMeasurementAndANewActuatorKindWorkWithoutCode() throws Exception {
        String owner = login("owner");
        postJson(owner, "/api/catalog/measurements", "{\"name\":\"Soil salinity\",\"unit\":\"dS/m\",\"minValid\":0,\"maxValid\":20,\"typical\":1}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("SOIL_SALINITY"));
        long probe = id(postJson(owner, "/api/catalog/models", "{\"name\":\"Test salinity probe\",\"category\":\"SENSOR\","
                + "\"channels\":[{\"measurementCode\":\"SOIL_SALINITY\",\"label\":\"Salinity\"}]}"));
        long valve = id(postJson(owner, "/api/catalog/models", "{\"name\":\"Test flush valve\",\"category\":\"ACTUATOR\","
                + "\"actionVerb\":\"Flushing\",\"defaultMaxRunMinutes\":5,\"effects\":[{\"measurementCode\":\"SOIL_SALINITY\",\"direction\":-1}]}"));

        long sensorDevice = id(postJson(owner, "/api/zones/1/devices", "{\"modelId\":" + probe + ",\"name\":\"Salinity A\",\"simulated\":false}"));
        long valveDevice = id(postJson(owner, "/api/zones/1/devices", "{\"modelId\":" + valve + ",\"name\":\"Flush valve A\"}"));
        long sensorId = channel(owner, "sensors", sensorDevice);
        long valveId = channel(owner, "actuators", valveDevice);

        reading(KEY, sensorId, 5).andExpect(status().isCreated());
        reading(KEY, sensorId, 50).andExpect(status().isUnprocessableContent()); // outside 0-20 dS/m
        postJson(owner, "/api/rules", "{\"name\":\"Salty soil\",\"sensorId\":" + sensorId + ",\"condition\":\"ABOVE\",\"threshold\":4,"
                + "\"actuatorId\":" + valveId + ",\"durationMin\":5,\"severity\":\"WARNING\",\"stopAt\":2}").andExpect(status().isCreated());
        reading(KEY, sensorId, 6).andExpect(jsonPath("$.status").value("ACTION_TAKEN"))
                .andExpect(jsonPath("$.actions[0]").value(containsString("Flushing: Flush valve A started until 2dS/m")));
        mvc.perform(get("/api/farm").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.zones[0].actuators[?(@.id==" + valveId + ")].status").value("RUNNING"))
                .andExpect(jsonPath("$.zones[0].actuators[?(@.id==" + valveId + ")].type").value("GENERIC"))
                .andExpect(jsonPath("$.zones[0].sensors[?(@.id==" + sensorId + ")].unit").value("dS/m"));
    }

    @Test
    void picturesAreUploadedSignedInAndShownToEveryone() throws Exception {
        String tech = login("technician");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};
        // the shared catalog is kept by Smart Farm: a farm's technician photographs the farm's own model
        long shared = modelId(tech, "12 V diaphragm water pump");
        mvc.perform(put("/api/catalog/models/" + shared + "/photo").header("Authorization", "Bearer " + tech)
                .contentType(MediaType.IMAGE_PNG).content(png)).andExpect(status().isForbidden());
        long model = id(postJson(tech, "/api/catalog/models", "{\"name\":\"Farm's own pump\",\"category\":\"ACTUATOR\",\"actuatorType\":\"WATER_PUMP\"}"));
        mvc.perform(put("/api/catalog/models/" + model + "/photo").header("Authorization", "Bearer " + tech)
                .contentType(MediaType.IMAGE_PNG).content(png)).andExpect(status().isNoContent());
        mvc.perform(get("/api/catalog/models/" + model + "/picture")) // no login: it is an <img>
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG)).andExpect(content().bytes(png));
        mvc.perform(put("/api/catalog/models/" + model + "/photo").contentType(MediaType.IMAGE_PNG).content(png))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBoardKeyOnlyDrivesItsOwnDevices() throws Exception {
        String tech = login("technician");
        long esp32 = modelId(tech, "ESP32 DevKit V1");
        String made = postJson(tech, "/api/controllers", "{\"name\":\"House node\",\"modelId\":" + esp32 + "}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.firmware").value(containsString("DEVICE_KEY")))
                .andReturn().getResponse().getContentAsString();
        String boardKey = JsonPath.read(made, "$.key");
        long boardId = ((Number) JsonPath.read(made, "$.board.id")).longValue();

        long soilModel = modelId(tech, "Capacitive soil moisture sensor v1.2");
        long device = id(postJson(tech, "/api/zones/1/devices", "{\"modelId\":" + soilModel + ",\"name\":\"Node soil\",\"simulated\":false,"
                + "\"controllerId\":" + boardId + "}"));
        long sensorId = channel(tech, "sensors", device);

        reading(boardKey, sensorId, 40).andExpect(status().isCreated());
        reading(boardKey, 1, 40).andExpect(status().isForbidden()); // sensor #1 is not on this board
        reading(KEY, sensorId, 41).andExpect(status().isCreated()); // the farm key still works
        mvc.perform(get("/api/controllers").header("Authorization", "Bearer " + tech))
                .andExpect(jsonPath("$[0].lastSeenAt").isNotEmpty()).andExpect(jsonPath("$[0].deviceCount").value(1));
        mvc.perform(get("/api/controllers/" + boardId + "/firmware").header("Authorization", "Bearer " + tech))
                .andExpect(jsonPath("$.firmware").value(containsString("#define SOIL_SENSOR_ID " + sensorId)));

        // A new key: the old one stops working at once
        postJson(tech, "/api/controllers/" + boardId + "/key", "{}").andExpect(status().isOk());
        reading(boardKey, sensorId, 42).andExpect(status().isForbidden());
    }

    @Test
    void onlyOwnerAndTechnicianChangeTheCatalogAndModelsInUseAreRetired() throws Exception {
        String worker = login("worker");
        String owner = login("owner");
        postJson(worker, "/api/catalog/models", "{\"name\":\"Nope\",\"category\":\"SENSOR\",\"channels\":[{\"measurementCode\":\"PH\",\"label\":\"pH\"}]}")
                .andExpect(status().isForbidden());

        long soil = modelId(owner, "Capacitive soil moisture sensor v1.2");
        mvc.perform(delete("/api/catalog/models/" + soil).header("Authorization", "Bearer " + owner))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value(containsString("kept by Smart Farm")));
        String admin = login("admin");
        mvc.perform(delete("/api/catalog/models/" + soil).header("Authorization", "Bearer " + admin).header("X-Farm-Id", "1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("retire it instead")));
        long custom = id(postJson(owner, "/api/catalog/models", "{\"name\":\"Test pH pen\",\"category\":\"SENSOR\","
                + "\"channels\":[{\"measurementCode\":\"PH\",\"label\":\"pH\"}]}"));
        mvc.perform(patch("/api/catalog/models/" + custom + "/retired").header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"retired\":true}")).andExpect(jsonPath("$.retired").value(true));
        postJson(owner, "/api/zones/1/devices", "{\"modelId\":" + custom + ",\"name\":\"pH A\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("retired")));
        mvc.perform(delete("/api/catalog/models/" + custom).header("Authorization", "Bearer " + owner)).andExpect(status().isNoContent());

        // A built-in measurement keeps its range: its sensor class checks readings with it
        String list = mvc.perform(get("/api/catalog/measurements").header("Authorization", "Bearer " + owner)).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(list, "$[?(@.code=='TEMPERATURE')].id");
        mvc.perform(put("/api/catalog/measurements/" + ids.get(0)).header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Temperature\",\"unit\":\"°C\",\"minValid\":-50,\"maxValid\":60,\"typical\":29}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/catalog/measurements/" + ids.get(0)).header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Temperature\",\"unit\":\"°C\",\"minValid\":-50,\"maxValid\":60,\"typical\":29}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("built in")));
    }

    private long channel(String token, String kind, long deviceId) throws Exception {
        String farm = mvc.perform(get("/api/farm").header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(farm, "$.zones[*]." + kind + "[?(@.deviceId==" + deviceId + ")].id");
        assertThat(ids).hasSize(1);
        return ids.get(0);
    }

    private long modelId(String token, String name) throws Exception {
        String list = mvc.perform(get("/api/catalog/models").header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(list, "$[?(@.name=='" + name + "')].id");
        assertThat(ids).as(name).hasSize(1);
        return ids.get(0);
    }

    private ResultActions reading(String key, long sensorId, double value) throws Exception {
        return mvc.perform(post("/api/devices/readings").header("X-Device-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensorId\":" + sensorId + ",\"value\":" + value + "}"));
    }

    private ResultActions postJson(String token, String path, String json) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long id(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"test-password\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
