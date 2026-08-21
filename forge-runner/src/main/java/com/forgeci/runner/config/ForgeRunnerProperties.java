package com.forgeci.runner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forge")
public record ForgeRunnerProperties(
        Server server,
        Runner runner,
        Git git) {

    public record Server(
            String url,
            String token) {}

    public record Runner(
            String name,
            String workspace,
            String volume) {}

    public record Git(
            String username,
            String password) {}
}