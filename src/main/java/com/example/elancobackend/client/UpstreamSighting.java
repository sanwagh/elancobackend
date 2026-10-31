package com.example.elancobackend.client;

// One record exactly as the Elanco API sends it. Every field is a String on purpose:
// parsing and validation happen in ingestion, so one bad value can't break the whole download.
public record UpstreamSighting(String id, String date, String location, String species, String latinName) {
}
