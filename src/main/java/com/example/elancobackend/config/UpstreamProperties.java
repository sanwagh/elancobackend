package com.example.elancobackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// Bound from the "upstream" section of application.yml.
@ConfigurationProperties(prefix = "upstream")
public record UpstreamProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
