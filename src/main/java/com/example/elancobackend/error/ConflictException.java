package com.example.elancobackend.error;

// The request clashes with the current state (e.g. a refresh is already running). Becomes a 409.
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
