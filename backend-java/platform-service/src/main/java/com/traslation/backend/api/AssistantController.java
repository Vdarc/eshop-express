package com.traslation.backend.api;

import com.traslation.backend.common.BusinessException;
import com.traslation.backend.service.RagAssistantService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final RagAssistantService assistant;

    public AssistantController(RagAssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        String message = first(request.get("message"), request.get("text"));
        String session = first(request.get("session_id"), "user_" + userId);
        try {
            return ResponseEntity.ok(
                    Map.of("code", 200, "data", assistant.answer(message, session)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", 500, "msg", "客服服务暂时不可用"));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(@RequestHeader("X-User-Id") String userId) {
        try {
            return ResponseEntity.ok(Map.of("code", 200, "data", assistant.stats()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", 500, "msg", Objects.toString(e.getMessage(), "客服状态读取失败")));
        }
    }

    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuild(
            @RequestHeader("X-Role-Keys") String roleKeys,
            @RequestBody(required = false) Map<String, Object> body) {
        requireAdmin(roleKeys);
        try {
            String which = body == null ? "all" : Objects.toString(body.get("which"), "all");
            return ResponseEntity.ok(
                    Map.of("code", 200, "data", assistant.rebuild(which), "msg", "索引重建完成"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", 500, "msg", Objects.toString(e.getMessage(), "客服索引重建失败")));
        }
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> process(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        String message = first(request.get("text"), request.get("message"));
        try {
            Map<String, Object> result = assistant.answer(message, "user_" + userId);
            return ResponseEntity.ok(
                    Map.of(
                            "code",
                            200,
                            "data",
                            Map.of(
                                    "response",
                                    result.get("reply"),
                                    "translation",
                                    "",
                                    "sources",
                                    result.get("sources"),
                                    "need_human",
                                    result.get("need_human"))));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", 500, "msg", "客服服务暂时不可用"));
        }
    }

    private static void requireAdmin(String roleKeys) {
        if (roleKeys == null
                || java.util.Arrays.stream(roleKeys.split(",")).noneMatch("admin"::equals))
            throw new BusinessException(HttpStatus.FORBIDDEN, "无管理员权限");
    }

    private static String first(Object first, Object second) {
        String value = Objects.toString(first, "");
        return value.isBlank() ? Objects.toString(second, "") : value;
    }
}
