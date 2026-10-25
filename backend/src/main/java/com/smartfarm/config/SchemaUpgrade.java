package com.smartfarm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Lets a database created by an older version accept the newer kinds of sensor and actuator.
 *
 * When Hibernate creates the sensor and actuator tables it adds {@code CHECK (sensor_type IN (...))}
 * with the kinds that existed at that moment, and {@code ddl-auto: update} never changes it. A farm
 * that already has data would refuse a heater or a humidity sensor with a constraint error.
 * The check only repeats what the Java code already guarantees (an unknown kind cannot be created),
 * so it is dropped. Safe to run on every start: with nothing to drop it does nothing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaUpgrade implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrade.class);

    private static final String[][] TYPE_COLUMNS = {{"sensor", "sensor_type"}, {"actuator", "actuator_type"}};

    private final DataSource dataSource;

    public SchemaUpgrade(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            for (String[] tableAndColumn : TYPE_COLUMNS) {
                String table = tableAndColumn[0];
                String column = tableAndColumn[1];
                switch (product) {
                    case "H2" -> dropH2Checks(connection, table, column);
                    case "PostgreSQL" -> execute(connection, "ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + table + "_" + column + "_check");
                    default -> log.debug("No type-check upgrade for {}", product);
                }
            }
            // Company accounts belong to no farm: older databases made app_user.farm_id NOT NULL.
            if (!tableExists(connection, "app_user")) {
                return; // a brand-new database: Hibernate creates the table right after, already nullable
            }
            switch (product) {
                case "H2" -> execute(connection, "ALTER TABLE app_user ALTER COLUMN farm_id SET NULL");
                case "PostgreSQL" -> execute(connection, "ALTER TABLE app_user ALTER COLUMN farm_id DROP NOT NULL");
                default -> log.debug("No farm_id upgrade for {}", product);
            }
        }
    }

    private void dropH2Checks(Connection connection, String table, String column) throws SQLException {
        for (String name : h2CheckNames(connection, table, column)) {
            execute(connection, "ALTER TABLE " + table + " DROP CONSTRAINT \"" + name + "\"");
            log.info("Dropped the old type check {} on {} so new device kinds can be saved", name, table);
        }
    }

    private List<String> h2CheckNames(Connection connection, String table, String column) throws SQLException {
        String sql = """
                SELECT tc.CONSTRAINT_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                  ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                WHERE tc.CONSTRAINT_TYPE = 'CHECK' AND UPPER(tc.TABLE_NAME) = ? AND UPPER(cc.CHECK_CLAUSE) LIKE ?
                """;
        List<String> names = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table.toUpperCase());
            statement.setString(2, "%" + column.toUpperCase() + "%");
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    names.add(rows.getString(1));
                }
            }
        }
        return names;
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        for (String name : new String[] {table, table.toUpperCase()}) {
            try (ResultSet rows = connection.getMetaData().getTables(null, null, name, null)) {
                if (rows.next()) {
                    return true;
                }
            }
        }
        return false;
    }

        private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
