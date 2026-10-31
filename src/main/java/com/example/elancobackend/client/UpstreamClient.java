package com.example.elancobackend.client;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class UpstreamClient {

    private final RestClient restClient;

    public UpstreamClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Retryable(
            includes = {HttpServerErrorException.class, ResourceAccessException.class},
            maxRetriesString = "${upstream.retry.max-retries}",
            delayString = "${upstream.retry.delay-ms}",
            multiplierString = "${upstream.retry.multiplier}")
    public List<UpstreamSighting> fetchAllSightings() {
        UpstreamSighting[] body = restClient.get()
                .uri("/tick-sightings")
                .retrieve()
                .body(UpstreamSighting[].class);
        return body == null ? List.of() : List.of(body);
    }
}
