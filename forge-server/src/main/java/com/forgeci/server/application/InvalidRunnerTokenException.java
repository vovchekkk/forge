package com.forgeci.server.application;

public class InvalidRunnerTokenException extends RuntimeException {
    public InvalidRunnerTokenException(String message) {
        super(message);
    }
}