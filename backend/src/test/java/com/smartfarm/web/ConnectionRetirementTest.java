package com.smartfarm.web;

import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test: the connection pool replaces its connections every 30 minutes. With H2 2.4,
 * check constraints created on a new database stayed tied to the connection that created them,
 * so after the first replacement every write to sensors, zones and actuators failed with
 * "Check constraint invalid ... database has been closed". Fixed by H2 2.5.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:retire;DB_CLOSE_DELAY=-1",
        "farm.simulator.enabled=false",
        "farm.scheduler.interval-ms=3600000",
        "farm.device-key=test-device-key",
        "farm.demo-password=test-password"
})
@AutoConfigureMockMvc
@DirtiesContext
class ConnectionRetirementTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource dataSource;

    @Test
    void writesStillWorkAfterThePoolReplacesItsConnections() throws Exception {
        // Do what the pool does after 30 minutes, right now: close every connection, then open new ones
        ((HikariDataSource) dataSource).getHikariPoolMXBean().softEvictConnections();
        Thread.sleep(500);

        // A device reading updates the sensor row (last seen), which checks the sensor table's constraint
        mvc.perform(post("/api/devices/readings").header("X-Device-Key", "test-device-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sensorId\":1,\"value\":42.0}"))
                .andExpect(status().isCreated());

        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"owner\",\"password\":\"test-password\"}")).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        mvc.perform(post("/api/zones").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"POULTRY\",\"name\":\"After retirement\",\"birdCount\":10}"))
                .andExpect(status().isCreated());
    }
}
