package com.example.elancobackend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableResilientMethods // turns on @Retryable
@EnableScheduling // turns on @Scheduled
@EnableConfigurationProperties(UpstreamProperties.class)
public class AppConfig {

    // Without timeouts a hung upstream would block us forever, so both are set explicitly.
    @Bean
    RestClient upstreamRestClient(UpstreamProperties props) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(props.readTimeout());
        return RestClient.builder().baseUrl(props.baseUrl()).requestFactory(requestFactory).build();
    }
}
