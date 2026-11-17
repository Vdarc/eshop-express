package com.traslation.backend.gateway;

import java.util.Set;

/**
 * 网关的公开路径与管理员路径判定。
 *
 * <p>抽成纯静态规则便于单测覆盖：鉴权规则一旦回归就是安全事故，而此前这些分支只能靠启动网关做端到端验证。
 */
public final class GatewayAccessRules {

    /** 免 JWT 的公开写接口（仅 POST）。 */
    private static final Set<String> PUBLIC_POST =
            Set.of("/api/login", "/api/register", "/api/register/merchant");

    /** 需要 admin 角色的接口。 */
    private static final Set<String> ADMIN_PATHS =
            Set.of(
                    "/api/merchant/applications",
                    "/api/merchant/approve",
                    "/api/merchant/reject",
                    "/api/merchant/detail",
                    "/api/assistant/rebuild",
                    "/api/user/add",
                    "/api/user/update",
                    "/api/user/delete");

    private static final String PRODUCT_PREFIX = "/api/product/";

    private GatewayAccessRules() {}

    /**
     * 是否无需登录即可访问。
     *
     * @param method HTTP 方法名（如 GET / POST）
     * @param path 请求路径
     */
    public static boolean isPublic(String method, String path) {
        if (path == null) return false;
        if (path.equals("/actuator/health") || path.startsWith("/uploads/")) return true;
        if ("POST".equals(method) && PUBLIC_POST.contains(path)) return true;
        // 商品列表与搜索建议公开，方便未登录浏览
        if ("GET".equals(method)
                && (path.equals("/api/products")
                        || path.startsWith("/api/products/")
                        || path.equals("/api/search/suggest"))) return true;
        // GET /api/product/{esin} 公开；带子路径的（如 /unpublish）不公开
        return "GET".equals(method)
                && path.startsWith(PRODUCT_PREFIX)
                && !path.substring(PRODUCT_PREFIX.length()).contains("/");
    }

    /** 是否需要 admin 角色（在已登录基础上追加判断）。 */
    public static boolean requiresAdmin(String path) {
        if (path == null) return false;
        if (ADMIN_PATHS.contains(path)) return true;
        return path.startsWith(PRODUCT_PREFIX) && path.endsWith("/unpublish");
    }

    /**
     * 从 JWT 载荷解析用户 ID。
     *
     * <p>迁移期两种签名并存：Java 的 {@code JwtService} 把用户 ID 放在标准 claim {@code sub}， Flask 的 {@code
     * generate_token} 放在自定义 claim {@code user_id}。只认 {@code sub} 会让灰度期 Flask 签发的 token
     * 打到网关时丢失身份，下游缺 {@code X-User-Id} 直接 500。
     *
     * @return 用户 ID；两种 claim 都没有时返回 null
     */
    public static String resolveUserId(io.jsonwebtoken.Claims claims) {
        if (claims == null) return null;
        String subject = claims.getSubject();
        if (subject != null && !subject.isBlank()) return subject;
        Object legacy = claims.get("user_id");
        if (legacy == null) return null;
        String value = String.valueOf(legacy);
        return value.isBlank() ? null : value;
    }
}
