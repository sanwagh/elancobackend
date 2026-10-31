package com.example.elancobackend.error;

// Asked for something that doesn't exist (e.g. unknown sighting id). Becomes a 404.
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
