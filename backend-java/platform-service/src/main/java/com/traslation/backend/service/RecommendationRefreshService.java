package com.traslation.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
public class RecommendationRefreshService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final RestClient recommendation;

    public RecommendationRefreshService(
            JdbcTemplate db,
            ObjectMapper json,
            @Value("${app.recommendation.url:http://localhost:8084}") String url) {
        this.db = db;
        this.json = json;
        recommendation = RestClient.builder().baseUrl(url).build();
    }

    @Async("taskExecutor")
    public void refresh(String esin, long userId, String country, String category, String keyword) {
        try {
            recommendation
                    .post()
                    .uri("/internal/recommendation/cache/clear")
                    .retrieve()
                    .toBodilessEntity();
            Map<?, ?> envelope =
                    recommendation
                            .post()
                            .uri("/api/recommend")
                            .body(
                                    Map.of(
                                            "country",
                                            country,
                                            "category",
                                            category,
                                            "keyword",
                                            keyword))
                            .retrieve()
                            .body(Map.class);
            Object data = envelope == null ? null : envelope.get("data");
            db.update(
                    "update publish_record set recommendation_data=? where product_id=? and"
                            + " user_id=?",
                    json.writeValueAsString(data == null ? Map.of() : data),
                    esin,
                    userId);
        } catch (Exception ignored) {
        }
    }
}
