package com.example.elancobackend.error;

import java.time.Instant;

// The one JSON shape every error uses.
public record ErrorResponse(int status, String error, String message, String path, Instant timestamp) {
}
