package com.traslation.backend.api;

import com.traslation.backend.common.Result;
import com.traslation.backend.service.TranslationService;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stat/translate")
public class TranslationStatController {
    private final TranslationService service;

    public TranslationStatController(TranslationService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> stats(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "30") int days) {
        // 统计接口只返回 {code, data}，不带 msg
        return Map.of("code", 200, "data", service.stats(date, days));
    }

    @PostMapping("/generate-report")
    public Result<Map<String, Object>> report(
            @RequestBody(required = false) Map<String, Object> body) {
        boolean created = service.generateReport(body == null ? null : (String) body.get("date"));
        return Result.ok(Map.of("success", created, "msg", created ? "报表生成成功" : "报表已存在或生成失败"));
    }
}
