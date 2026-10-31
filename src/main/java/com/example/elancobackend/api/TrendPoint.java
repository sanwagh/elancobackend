package com.example.elancobackend.api;

// One bucket of the trend. period is the Monday date for weeks ("2025-03-03") or "2025-03" for months.
public record TrendPoint(String period, long count) {
}
