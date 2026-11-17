package com.traslation.backend.service;

import com.traslation.backend.common.BusinessException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
public class EmbeddingService {
    private final RestClient client;

    public EmbeddingService(@Value("${app.embedding.base-url}") String baseUrl) {
        client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public Map<String, Object> embed(List<String> texts, String model) {
        try {
            return Objects.requireNonNull(
                    client.post()
                            .uri("/embed")
                            .body(
                                    Map.of(
                                            "texts",
                                            texts,
                                            "model",
                                            model == null ? "minilm-l12" : model))
                            .retrieve()
                            .body(Map.class));
        } catch (Exception e) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, "AI embedding service unavailable");
        }
    }

    public Map<String, Object> similarity(String query, List<String> candidates, int topK) {
        try {
            return Objects.requireNonNull(
                    client.post()
                            .uri("/similarity")
                            .body(Map.of("query", query, "candidates", candidates, "top_k", topK))
                            .retrieve()
                            .body(Map.class));
        } catch (Exception e) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, "AI similarity service unavailable");
        }
    }
}
