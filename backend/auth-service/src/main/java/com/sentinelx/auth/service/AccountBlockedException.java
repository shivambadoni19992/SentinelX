package com.sentinelx.auth.service;

/** Thrown when an account's status forbids authentication or access. Maps to 403. */
public class AccountBlockedException extends RuntimeException {
    public AccountBlockedException(String message) {
        super(message);
    }
}