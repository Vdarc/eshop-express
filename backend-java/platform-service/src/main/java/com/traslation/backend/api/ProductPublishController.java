package com.traslation.backend.api;

import com.traslation.backend.common.BusinessException;
import com.traslation.backend.service.ProductPublishService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.*;

@RestController
@RequestMapping("/api/product")
public class ProductPublishController {
    private final ProductPublishService publisher;
    private final JdbcTemplate db;
    private final RestClient catalog;

    public ProductPublishController(
            ProductPublishService publisher,
            JdbcTemplate db,
            @Value("${app.catalog.url:http://localhost:8082}") String catalogUrl) {
        this.publisher = publisher;
        this.db = db;
        this.catalog = RestClient.builder().baseUrl(catalogUrl).build();
    }

    @PostMapping("/publish")
    @Transactional
    public Map<String, Object> publish(
            @RequestHeader("X-User-Id") long userId,
            @RequestHeader(value = "X-User-Name", defaultValue = "anonymous") String username,
            @RequestBody Map<String, Object> body) {
        return Map.of("code", 200, "data", publisher.publish(body, userId, username));
    }

    @PostMapping("/{esin}/unpublish")
    @Transactional
    public Map<String, Object> unpublish(@PathVariable String esin) {
        var rows = db.queryForList("select id from product where esin=?", esin);
        if (rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        Object id = rows.get(0).get("id");
        db.update("delete from publish_record where product_id=?", esin);
        db.update("delete from product_image where product_id=?", id);
        db.update("delete from product where esin=?", esin);
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    catalog.delete()
                                            .uri("/internal/search/products/{esin}", esin)
                                            .retrieve()
                                            .toBodilessEntity();
                                } catch (Exception ignored) {
                                }
                            }
                        });
        return Map.of("code", 200, "data", Map.of("message", "商品下架成功"));
    }
}
