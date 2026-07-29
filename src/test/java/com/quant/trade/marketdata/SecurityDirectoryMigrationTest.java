package com.quant.trade.marketdata;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityDirectoryMigrationTest {

    @Test
    void v17PreservesLegacyIdsAndMapsLifecycleWithoutEditingHistory() throws Exception {
        String url = "jdbc:h2:mem:migration_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("16").load().migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO stock_basic
                        (id, canonical_symbol, symbol, name, market, delisted)
                    VALUES
                        (701, 'SH.600701', '600701', 'legacy-active', 'SH', FALSE),
                        (702, 'SH.600702', '600702', 'legacy-delisted', 'SH', TRUE)
                    """);
        }

        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT id, canonical_symbol, security_type, list_status
                     FROM stock_basic ORDER BY id
                     """)) {
            assertTrue(rows.next());
            assertEquals(701L, rows.getLong("id"));
            assertEquals("SH.600701", rows.getString("canonical_symbol"));
            assertEquals("STOCK", rows.getString("security_type"));
            assertEquals("UNKNOWN", rows.getString("list_status"));
            assertTrue(rows.next());
            assertEquals(702L, rows.getLong("id"));
            assertEquals("DELISTED", rows.getString("list_status"));
            assertFalse(rows.next());
        }
    }
}
