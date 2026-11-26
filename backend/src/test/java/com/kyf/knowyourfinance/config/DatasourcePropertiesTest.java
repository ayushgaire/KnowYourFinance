package com.kyf.knowyourfinance.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards against a database address or login being committed again as a
 * default value. Each setting must come from the environment only.
 */
class DatasourcePropertiesTest {

    @Test
    void databaseSettingsHaveNoCommittedDefaults() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(in, "application.properties should be on the classpath");
            properties.load(in);
        }

        assertEquals("${SPRING_DATASOURCE_URL}", properties.getProperty("spring.datasource.url"));
        assertEquals("${SPRING_DATASOURCE_USERNAME}", properties.getProperty("spring.datasource.username"));
        assertEquals("${SPRING_DATASOURCE_PASSWORD}", properties.getProperty("spring.datasource.password"));
    }
}
