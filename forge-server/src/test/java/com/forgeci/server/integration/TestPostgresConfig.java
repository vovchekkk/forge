package com.forgeci.server.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class TestPostgresConfig {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("forge")
            .withUsername("forge")
            .withPassword("forge");

    static {
        POSTGRES.start();
    }

    @Bean
    @Primary
    public PostgreSQLContainer<?> postgresContainer() {
        return POSTGRES;
    }
}