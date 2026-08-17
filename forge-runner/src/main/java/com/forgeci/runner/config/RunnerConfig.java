package com.forgeci.runner.config;

import com.forgeci.runner.client.ServerApiClient;
import com.forgeci.runner.git.GitCheckout;
import com.forgeci.runner.workspace.WorkspaceManager;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RunnerConfig {

    @Bean
    public ServerApiClient serverApiClient(ForgeRunnerProperties properties) {
        return new ServerApiClient(properties);
    }

    @Bean
    public GitCheckout gitCheckout() {
        return new GitCheckout();
    }

    @Bean
    public WorkspaceManager workspaceManager(ForgeRunnerProperties properties) {
        Path root = properties.runner().workspace() == null
                ? Path.of(System.getProperty("java.io.tmpdir"), "forge-runner")
                : Path.of(properties.runner().workspace());
        return new WorkspaceManager(root);
    }
}