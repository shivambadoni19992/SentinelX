package com.sentinelx.auth.service;

/** Thrown when a user referenced by a token no longer exists. Maps to 404. */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}