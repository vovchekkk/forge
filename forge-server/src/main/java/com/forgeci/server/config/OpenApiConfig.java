package com.forgeci.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI forgeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Forge CI Server API")
                .description("Control plane for Forge CI. The server decides what should run; runners execute jobs in Docker.")
                .version("v1"));
    }
}