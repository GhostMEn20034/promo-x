package com.promox.core.testfixtures.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;


@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.3-alpine")
            .withDatabaseName("promox_test")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    /**
     * Only the connection details are a bean, not the container: Spring stops a container bean when its context
     * closes, and this one is shared by every cached test context. The context closed first at shutdown would
     * stop it under the others, which then hang on closing. Testcontainers removes it once the JVM exits.
     */
    @Bean
    JdbcConnectionDetails postgresConnectionDetails() {
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return POSTGRES.getUsername();
            }

            @Override
            public String getPassword() {
                return POSTGRES.getPassword();
            }

            @Override
            public String getJdbcUrl() {
                return POSTGRES.getJdbcUrl();
            }
        };
    }
}
