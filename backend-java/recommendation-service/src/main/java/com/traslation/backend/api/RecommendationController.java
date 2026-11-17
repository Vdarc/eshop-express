package com.traslation.backend.api;

import com.traslation.backend.service.RecommendationService;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/recommend")
public class RecommendationController {
    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @PostMapping
    public Map<String, Object> recommend(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        String country = Objects.toString(request.get("country"), "沙特");
        String category = Objects.toString(request.get("category"), "clothes");
        String keyword = Objects.toString(request.get("keyword"), "").trim();
        return Map.of("code", 200, "data", recommendations.recommend(country, category, keyword));
    }
}
