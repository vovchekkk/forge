package com.forgeci.server.application;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}