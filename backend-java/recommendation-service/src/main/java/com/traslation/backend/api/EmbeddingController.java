package com.traslation.backend.api;

import com.traslation.backend.service.EmbeddingService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/ai")
public class EmbeddingController {
    private final EmbeddingService service;

    public EmbeddingController(EmbeddingService service) {
        this.service = service;
    }

    public record EmbedRequest(@NotEmpty @Size(max = 256) List<String> texts, String model) {}

    public record SimilarityRequest(
            @jakarta.validation.constraints.NotBlank String query,
            @NotEmpty @Size(max = 1000) List<String> candidates,
            Integer top_k) {}

    @PostMapping("/embed")
    public Map<String, Object> embed(@Valid @RequestBody EmbedRequest request) {
        return service.embed(request.texts(), request.model());
    }

    @PostMapping("/similarity")
    public Map<String, Object> similarity(@Valid @RequestBody SimilarityRequest request) {
        return service.similarity(
                request.query(),
                request.candidates(),
                request.top_k() == null ? 10 : request.top_k());
    }
}
