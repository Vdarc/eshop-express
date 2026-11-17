package com.traslation.backend.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Aggregates the same Redis and Elasticsearch diagnostics exposed by Flask. */
@RestController
public class DependencyHealthController {
    private final RestClient recommendation;
    private final RestClient catalog;

    public DependencyHealthController(
            RestClient.Builder builder,
            @Value("${app.recommendation.url:http://localhost:8084}") String recommendationUrl,
            @Value("${app.catalog.url:http://localhost:8082}") String catalogUrl) {
        recommendation = builder.clone().baseUrl(recommendationUrl).build();
        catalog = builder.clone().baseUrl(catalogUrl).build();
    }

    @GetMapping("/api/health/deps")
    public Map<String, Object> dependencies() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put(
                    "redis",
                    recommendation
                            .get()
                            .uri("/internal/recommendation/health/redis")
                            .retrieve()
                            .body(Map.class));
        } catch (Exception e) {
            data.put(
                    "redis",
                    Map.of(
                            "configured",
                            false,
                            "available",
                            false,
                            "error",
                            Objects.toString(e.getMessage(), "Redis diagnostics unavailable")));
        }
        try {
            data.put(
                    "elasticsearch",
                    catalog.get().uri("/internal/health/elasticsearch").retrieve().body(Map.class));
        } catch (Exception e) {
            data.put(
                    "elasticsearch",
                    Map.of(
                            "available",
                            false,
                            "error",
                            Objects.toString(
                                    e.getMessage(), "Elasticsearch diagnostics unavailable")));
        }
        return Map.of("code", 200, "data", data);
    }
}
