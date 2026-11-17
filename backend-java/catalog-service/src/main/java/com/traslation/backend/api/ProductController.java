package com.traslation.backend.api;

import com.traslation.backend.canal.CanalCdcWorker;
import com.traslation.backend.common.BusinessException;
import com.traslation.backend.search.ProductSearchIndex;
import com.traslation.backend.service.ProductQueryService;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class ProductController {
    private final ProductQueryService products;
    private final ProductSearchIndex searchIndex;
    private final CanalCdcWorker canal;

    public ProductController(
            ProductQueryService products, ProductSearchIndex searchIndex, CanalCdcWorker canal) {
        this.products = products;
        this.searchIndex = searchIndex;
        this.canal = canal;
    }

    @GetMapping("/api/products")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int per_page,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "zh") String language) {
        return products.list(page, per_page, search, category, country, language);
    }

    @GetMapping("/api/product/{esin}")
    public Map<String, Object> one(
            @PathVariable String esin,
            @RequestParam(defaultValue = "zh") String language,
            @RequestParam(required = false) String country) {
        var product = products.byEsin(esin, language, country);
        if (product == null) throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        // Flask 的详情接口返回 {code, data}，与列表接口的裸结构不同
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("data", product);
        return body;
    }

    @GetMapping("/api/search/status")
    public Map<String, Object> searchStatus() {
        // 保持与 Flask 完全一致的字段集合；Canal 状态另走内部接口（Flask 无此字段）
        return Map.of("code", 200, "data", searchIndex.status());
    }

    /** Canal 同步状态：Java 侧新增能力，放在内部接口，不影响 /api/search/status 的契约。 */
    @GetMapping("/internal/search/canal")
    public Map<String, Object> canalStatus() {
        return Map.of("code", 200, "data", canal.status());
    }

    @GetMapping("/internal/health/elasticsearch")
    public Map<String, Object> elasticsearchHealth() {
        return searchIndex.elasticsearchHealth();
    }

    @GetMapping("/api/search/suggest")
    public Map<String, Object> suggest(
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "8") int size) {
        var suggestions = q.isBlank() ? List.<String>of() : searchIndex.suggest(q, size);
        return Map.of(
                "code",
                200,
                "data",
                suggestions.isEmpty() && !q.isBlank() ? products.suggest(q, size) : suggestions);
    }

    @PostMapping("/internal/search/reindex")
    public Map<String, Object> reindex() {
        return searchIndex.rebuild();
    }

    @PostMapping("/internal/search/products/{esin}")
    public Map<String, Object> indexProduct(@PathVariable String esin) {
        return Map.of("ok", searchIndex.indexProduct(esin));
    }

    @DeleteMapping("/internal/search/products/{esin}")
    public Map<String, Object> deleteProductIndex(@PathVariable String esin) {
        return Map.of("ok", searchIndex.deleteProduct(esin));
    }

    @GetMapping("/api/merchant/products")
    public Map<String, Object> merchantProducts(
            @RequestHeader("X-User-Id") long userId,
            @RequestParam(required = false) String search) {
        return Map.of("code", 200, "data", products.merchantProducts(userId, search));
    }

    @PutMapping("/api/merchant/products/{esin}")
    public Map<String, Object> updateMerchantProduct(
            @RequestHeader("X-User-Id") long userId,
            @PathVariable String esin,
            @RequestBody Map<String, Object> body) {
        if (!products.owns(userId, esin))
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在或无权操作");
        products.updateMerchantProduct(userId, esin, body);
        return Map.of("code", 200, "message", "商品更新成功");
    }

    @DeleteMapping("/api/merchant/products/{esin}")
    @Transactional
    public Map<String, Object> unpublishMerchantProduct(
            @RequestHeader("X-User-Id") long userId, @PathVariable String esin) {
        if (!products.owns(userId, esin))
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在或无权操作");
        products.unpublishMerchantProduct(userId, esin);
        return Map.of("code", 200, "message", "商品下架成功");
    }
}
