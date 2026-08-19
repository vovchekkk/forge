package com.forgeci.server.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "forge.scheduler.interval=60s",
        "forge.runner.offline-check-interval=60s"
})
@Import(TestPostgresConfig.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgresConfig.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", TestPostgresConfig.POSTGRES::getUsername);
        registry.add("spring.datasource.password", TestPostgresConfig.POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE job_logs, jobs, pipeline_runs, runners, pipelines, projects
                RESTART IDENTITY CASCADE
                """);
    }
}