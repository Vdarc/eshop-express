package com.traslation.backend.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 操作审计的纯函数集合：路由→动作映射、身份/参数脱敏、UTF-8 安全截断、客户端 IP 解析。
 *
 * <p>这些逻辑不依赖 Servlet 容器与数据库，抽出来便于单元测试；拦截器只负责编排。
 */
final class OperationAuditSupport {

    /** 参数记录的占位符（图片等大字段脱敏）。 */
    static final String IMAGE_PLACEHOLDER = "[IMAGE_DATA_REMOVED]";

    /** 请求参数（含请求体）序列化后的最大长度，避免日志行过长。 */
    static final int PARAMS_MAX_BYTES = 4000;

    /**
     * 「以浏览列表为意图」的只读路由白名单：这些路由的后续页不重复留痕。
     *
     * <p>刻意只放一条，加路由时这里是唯一改动点。
     */
    private static final Set<String> PAGED_READ_ONLY_ROUTES = Set.of("/api/log/list");

    private OperationAuditSupport() {}

    /**
     * 路由与 HTTP 方法到中文动作的映射。
     *
     * <p>动作文案集中在此维护，未显式标注审计的路由返回
     * null（不写日志）。
     */
    static String action(String method, String uri) {
        if (method == null || uri == null) return null;

        if ("GET".equals(method)) {
            return switch (uri) {
                case "/api/log/list" -> "查看日志列表";
                case "/api/user/list" -> "查看用户列表";
                case "/api/merchant/applications" -> "查看商家申请列表";
                case "/api/merchant/detail" -> "查看商家申请详情";
                case "/api/translate/history" -> "查询翻译历史";
                case "/api/stat/translate" -> "查询翻译统计数据";
                case "/api/dashboard" -> "查看运营仪表盘";
                case "/api/glossary/list" -> "获取术语库列表";
                case "/api/user/profile" -> "访问个人中心";
                case "/api/merchant/info" -> "访问商家信息";
                default -> null;
            };
        }
        if ("POST".equals(method)) {
            String byPath =
                    switch (uri) {
                        case "/api/user/add" -> "管理员创建用户";
                        case "/api/user/update" -> "管理员修改用户";
                        case "/api/user/delete" -> "管理员删除用户";
                        case "/api/merchant/approve" -> "管理员批准商家申请";
                        case "/api/merchant/reject" -> "管理员拒绝商家申请";
                        case "/api/update-password" -> "用户修改自身密码";
                        case "/api/translate" -> "执行智能翻译";
                        case "/api/glossary/add" -> "添加术语";
                        case "/api/glossary/update" -> "更新术语";
                        case "/api/glossary/delete" -> "删除术语";
                        case "/api/login" -> "用户登录";
                        case "/api/product/publish" -> "商品发布";
                        case "/api/product/upload-image" -> "上传商品图片";
                        case "/api/user/upload-avatar" -> "上传用户头像";
                        case "/api/user/profile" -> "访问个人中心";
                        case "/api/merchant/info" -> "访问商家信息";
                        default -> null;
                    };
            if (byPath != null) return byPath;
            // 带路径参数的 POST 需要单独匹配：不能在 switch 的 default 直接返回 null
            if (uri.matches("/api/product/[^/]+/unpublish")) return "管理员下架商品";
            return null;
        }
        if ("PUT".equals(method) && uri.matches("/api/merchant/products/[^/]+")) return "更新商品";
        if ("DELETE".equals(method) && uri.matches("/api/merchant/products/[^/]+")) return "下架商品";
        return null;
    }

    /**
     * 分页浏览的审计噪声抑制：同一份列表的后续页不重复留痕。
     *
     * <p>只对「以浏览列表为意图」的只读接口生效，且只跳过 page &gt; 1 的请求； page
     * 缺失、非数字、&lt;=1 一律照常记录 —— 宁可多记，不可漏记。
     *
     * @param method HTTP 方法，大小写不敏感
     * @param uri 请求路径（不含 query string），允许带尾部 {@code /}
     * @param pageParam 分页参数原始值，可为 null（缺省）
     * @return true 记录日志；false 跳过
     */
    public static boolean shouldRecord(String method, String uri, String pageParam) {
        // 非 GET 一律照常记录：写操作没有「分页浏览」这回事
        if (method == null || !"GET".equalsIgnoreCase(method)) return true;
        if (uri == null) return true;

        // 容忍尾部斜杠：/api/log/list/ 与 /api/log/list 视为同一路由
        String path = uri;
        while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (!PAGED_READ_ONLY_ROUTES.contains(path)) return true;

        // page 缺失 / 空串：视为第一页，照常记录
        if (pageParam == null) return true;
        String raw = pageParam.trim();
        if (raw.isEmpty()) return true;

        int page;
        try {
            page = Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            // 解析失败（如 "abc"、超长数字）不放行跳过，宁可多记
            return true;
        }
        return page <= 1;
    }

    /**
     * 按 UTF-8 字节数截断，保证不会把多字节字符截成半个。
     *
     * <p>数据库列长度（如 varchar(255)）按字符计，而这里按字节更保守， 中文场景下不会写出超长内容。
     */
    static String trim(String value, int maxBytes) {
        if (value == null) return null;
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maxBytes)
            return value;
        int end = Math.min(value.length(), maxBytes);
        while (end > 0
                && value.substring(0, end).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        > maxBytes) end--;
        return value.substring(0, end);
    }

    /** 凭证类字段判定：命中则不记录其值。 */
    static boolean sensitive(String key) {
        return key != null
                && key.toLowerCase().matches(".*(password|passwd|token|secret|authorization).*");
    }

    /** 客户端 IP 解析，优先级： X-Forwarded-For 首个地址 → X-Real-IP → remoteAddr。 */
    static String clientIp(String forwardedFor, String realIp, String remoteAddr) {
        if (forwardedFor != null && !forwardedFor.isBlank())
            return forwardedFor.split(",", 2)[0].trim();
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return remoteAddr;
    }

    /**
     * 递归脱敏：删除凭证字段、把大体积图片/Base64 文本替换为占位符。
     *
     * <p>调用方应先 {@code deepCopy()}，避免改动业务请求体。
     */
    static JsonNode sanitize(JsonNode node) {
        if (node instanceof ObjectNode object) {
            var names = object.fieldNames();
            while (names.hasNext()) {
                String key = names.next();
                if (sensitive(key)) {
                    names.remove();
                    continue;
                }
                object.set(key, sanitize(object.get(key)));
            }
            return object;
        }
        if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) array.set(i, sanitize(array.get(i)));
            return array;
        }
        if (node.isTextual()) {
            String value = node.asText();
            if (value.length() > 1000
                    && (value.contains("data:image") || value.toLowerCase().contains("base64")))
                return TextNode.valueOf(IMAGE_PLACEHOLDER);
        }
        return node;
    }

    /** 把请求体（或普通参数）序列化为脱敏后的 JSON 串。 */
    static String paramsJson(ObjectMapper json, JsonNode body, Map<String, String[]> parameters) {
        try {
            if (body != null && body.isObject())
                return trim(json.writeValueAsString(sanitize(body.deepCopy())), PARAMS_MAX_BYTES);

            Map<String, Object> params = new LinkedHashMap<>();
            if (parameters != null)
                parameters.forEach(
                        (key, values) -> {
                            if (!sensitive(key))
                                params.put(key, values == null ? "" : String.join(",", values));
                        });
            return trim(json.writeValueAsString(params), PARAMS_MAX_BYTES);
        } catch (Exception ignored) {
            return "{}";
        }
    }
}
