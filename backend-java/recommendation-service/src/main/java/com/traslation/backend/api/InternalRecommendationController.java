package com.traslation.backend.api;

import com.traslation.backend.service.RecommendationCacheService;
import com.traslation.backend.service.RecommendationService;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/recommendation")
public class InternalRecommendationController {
    private final RecommendationService recommendations;
    private final RecommendationCacheService cache;

    public InternalRecommendationController(
            RecommendationService recommendations, RecommendationCacheService cache) {
        this.recommendations = recommendations;
        this.cache = cache;
    }

    @GetMapping("/health/redis")
    public Map<String, Object> redisHealth() {
        Map<String, Object> state = cache.state();
        if (state.get("available") == null) cache.available();
        return cache.state();
    }

    @PostMapping("/cache/clear")
    public Map<String, Object> clearCache(@RequestBody(required = false) Map<String, String> body) {
        String country = body == null ? null : body.get("country");
        String category = body == null ? null : body.get("category");
        return Map.of("deleted", recommendations.clearCache(country, category));
    }
}
