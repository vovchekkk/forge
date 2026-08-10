package com.forgeci.server.application;

import java.util.List;

public class InvalidPipelineException extends RuntimeException {
    private final List<String> errors;

    public InvalidPipelineException(List<String> errors) {
        super("Invalid pipeline configuration: " + String.join("; ", errors));
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}