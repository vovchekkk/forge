package com.forgeci.server.application;

public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}