package com.smartfarm.config;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A farm database made before the heater, mister, light and tank valve existed has
 * CHECK (actuator_type IN ('FAN','FEEDER','WATER_PUMP')) and refuses them. The upgrade removes that check.
 */
class SchemaUpgradeTest {

    @Test
    void oldTypeChecksAreDroppedSoNewKindsCanBeSaved() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:oldschema;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE sensor (id BIGINT PRIMARY KEY, sensor_type VARCHAR(31) NOT NULL "
                    + "CHECK (sensor_type IN ('FEED_LEVEL','SOIL_MOISTURE','TEMPERATURE')))");
            sql.execute("CREATE TABLE actuator (id BIGINT PRIMARY KEY, actuator_type VARCHAR(31) NOT NULL "
                    + "CHECK (actuator_type IN ('FAN','FEEDER','WATER_PUMP')))");
            sql.execute("CREATE TABLE zone (id BIGINT PRIMARY KEY, zone_type VARCHAR(31) NOT NULL "
                    + "CHECK (zone_type IN ('CROP','POULTRY')))");
            sql.execute("INSERT INTO actuator VALUES (1, 'FAN')");

            assertThatThrownBy(() -> sql.execute("INSERT INTO actuator VALUES (2, 'HEATER')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.execute("INSERT INTO sensor VALUES (1, 'HUMIDITY')")).isInstanceOf(SQLException.class);

            new SchemaUpgrade(dataSource).run(null);

            sql.execute("INSERT INTO actuator VALUES (2, 'HEATER')");
            sql.execute("INSERT INTO sensor VALUES (1, 'HUMIDITY')");
            // Existing rows survive, and an unrelated check stays in place
            sql.execute("INSERT INTO actuator VALUES (3, 'FAN')");
            assertThatThrownBy(() -> sql.execute("INSERT INTO zone VALUES (1, 'POND')")).isInstanceOf(SQLException.class);

            // Running it again on an upgraded database changes nothing and does not fail
            assertThatCode(() -> new SchemaUpgrade(dataSource).run(null)).doesNotThrowAnyException();
        }
    }
}
