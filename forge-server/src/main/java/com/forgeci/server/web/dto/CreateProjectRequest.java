package com.forgeci.server.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record CreateProjectRequest(
        @NotBlank String name,
        @NotBlank String repositoryUrl,
        String repositoryBranch) {
}