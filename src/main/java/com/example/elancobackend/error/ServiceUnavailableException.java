package com.example.elancobackend.error;

// We can't answer yet because no data has ever been ingested. Becomes a 503.
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
