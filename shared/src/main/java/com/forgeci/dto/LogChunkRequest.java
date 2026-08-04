package com.forgeci.dto;

import java.util.List;

public record LogChunkRequest(
        List<String> lines) {
}