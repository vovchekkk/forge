package com.forgeci.e2e;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class E2ePostgresConfig {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("forge")
            .withUsername("forge")
            .withPassword("forge");

    static {
        POSTGRES.start();
    }

    private E2ePostgresConfig() {
    }
}