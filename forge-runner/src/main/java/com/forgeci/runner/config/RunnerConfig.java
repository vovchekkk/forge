package com.forgeci.runner.config;

import com.forgeci.runner.client.ServerApiClient;
import com.forgeci.runner.docker.DockerExecutor;
import com.forgeci.runner.git.GitCheckout;
import com.forgeci.runner.workspace.WorkspaceManager;
import com.github.dockerjava.api.DockerClient;
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
    public GitCheckout gitCheckout(ForgeRunnerProperties properties) {
        String username = properties.git() == null ? null : properties.git().username();
        String password = properties.git() == null ? null : properties.git().password();
        if (username != null && password != null) {
            return new GitCheckout(username, password);
        }
        return new GitCheckout();
    }

    @Bean
    public WorkspaceManager workspaceManager(ForgeRunnerProperties properties) {
        Path root = properties.runner().workspace() == null
                ? Path.of(System.getProperty("java.io.tmpdir"), "forge-runner")
                : Path.of(properties.runner().workspace());
        return new WorkspaceManager(root);
    }

    @Bean
    public DockerExecutor dockerExecutor(DockerClient dockerClient, ForgeRunnerProperties properties) {
        String volume = properties.runner().volume();
        return new DockerExecutor(dockerClient, volume);
    }
}