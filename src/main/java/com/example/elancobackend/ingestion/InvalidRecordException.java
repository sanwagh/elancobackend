package com.example.elancobackend.ingestion;

// Thrown by SightingCleaner when an upstream record can't be used. The message is stored
// as the rejection reason in rejected_records.
public class InvalidRecordException extends RuntimeException {

    public InvalidRecordException(String message) {
        super(message);
    }
}
