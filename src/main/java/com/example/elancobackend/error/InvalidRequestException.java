package com.example.elancobackend.error;

// The caller sent something unusable (bad parameter). Becomes a 400.
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
