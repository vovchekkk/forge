package com.forgeci.server.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePipelineRequest(
        @NotBlank String config) {
}