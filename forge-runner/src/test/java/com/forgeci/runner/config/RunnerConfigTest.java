package com.forgeci.runner.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgeci.runner.client.ServerApiClient;
import com.forgeci.runner.docker.DockerExecutor;
import com.forgeci.runner.git.GitCheckout;
import com.forgeci.runner.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


class RunnerConfigTest {

    @Configuration
    static class TestConfig {

        @Bean
        ForgeRunnerProperties properties() {
            return new ForgeRunnerProperties(
                    new ForgeRunnerProperties.Server("http://localhost:8080", "test-token"),
                    new ForgeRunnerProperties.Runner("test-runner", null, null),
                    null);
        }
    }

    private AnnotationConfigApplicationContext newContext() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(TestConfig.class, DockerConfig.class, RunnerConfig.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    void dockerExecutorBeanIsRegistered() {
        try (AnnotationConfigApplicationContext ctx = newContext()) {
            assertThat(ctx.getBean(DockerExecutor.class)).isNotNull();
        }
    }

    @Test
    void gitCheckoutBeanIsRegistered() {
        try (AnnotationConfigApplicationContext ctx = newContext()) {
            assertThat(ctx.getBean(GitCheckout.class)).isNotNull();
        }
    }

    @Test
    void workspaceManagerBeanIsRegistered() {
        try (AnnotationConfigApplicationContext ctx = newContext()) {
            assertThat(ctx.getBean(WorkspaceManager.class)).isNotNull();
        }
    }

    @Test
    void serverApiClientBeanIsRegistered() {
        try (AnnotationConfigApplicationContext ctx = newContext()) {
            assertThat(ctx.getBean(ServerApiClient.class)).isNotNull();
        }
    }
}
