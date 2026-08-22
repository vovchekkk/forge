package com.forgeci.server.integration;

import com.forgeci.server.application.RunnerService;
import com.forgeci.server.entity.UserEntity;
import com.forgeci.server.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RunnerService runnerService;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgresConfig.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", TestPostgresConfig.POSTGRES::getUsername);
        registry.add("spring.datasource.password", TestPostgresConfig.POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE job_logs, jobs, pipeline_runs, runners, pipelines, projects,
                           refresh_tokens, audit_logs, users
                RESTART IDENTITY CASCADE
                """);
    }

    
    protected UUID createUser(String email) {
        UserEntity user = new UserEntity(email, passwordEncoder.encode("test-password-123"));
        return userRepository.save(user).getId();
    }

    
    protected UUID createRunner(UUID ownerId, String name) {
        RunnerService.RegistrationIssue issue = runnerService.createCredential(ownerId, name);
        runnerService.register(name, issue.registrationToken());
        return issue.runner().getId();
    }
}