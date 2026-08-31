package com.sentinelx.auth.service;

/** Thrown when supplied credentials do not match any known account. Maps to 401. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}