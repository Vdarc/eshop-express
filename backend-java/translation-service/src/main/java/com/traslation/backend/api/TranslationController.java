package com.traslation.backend.api;

import com.traslation.backend.common.BusinessException;
import com.traslation.backend.common.Result;
import com.traslation.backend.service.TranslationService;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/translate")
@Validated
public class TranslationController {
    private final TranslationService service;

    public TranslationController(TranslationService service) {
        this.service = service;
    }

    @PostMapping
    public Result<Map<String, Object>> translate(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Name", defaultValue = "anonymous") String username,
            @RequestBody Map<String, Object> body) {
        String text = String.valueOf(body.getOrDefault("text", "")).trim();
        if (text.isBlank())
            throw new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST, "文本不能为空");
        return Result.ok(
                service.translate(
                        text,
                        String.valueOf(body.getOrDefault("target_lang", "en")),
                        String.valueOf(body.getOrDefault("category", "all")),
                        String.valueOf(body.getOrDefault("country", "沙特")),
                        userId,
                        username));
    }

    @GetMapping("/history")
    public Result<Map<String, Object>> history(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(service.history(userId, page, size));
    }
}
