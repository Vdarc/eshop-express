package com.traslation.backend.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traslation.backend.common.id.SnowflakeIdGenerator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

/** Records the operations that are explicitly marked for auditing. */
public final class OperationAuditInterceptor implements HandlerInterceptor {
    private static final String START_ATTRIBUTE =
            OperationAuditInterceptor.class.getName() + ".start";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SnowflakeIdGenerator idGenerator;

    public OperationAuditInterceptor(
            JdbcTemplate jdbc, ObjectMapper json, SnowflakeIdGenerator idGenerator) {
        this.jdbc = jdbc;
        this.json = json;
        this.idGenerator = idGenerator;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_ATTRIBUTE, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        String action = action(request);
        if (action == null) return;
        // 分页噪声抑制：日志列表的后续页不重复留痕。
        // 只对 GET 取 page 参数——对 POST 调 getParameter 会提前解析请求体。
        String pageParam =
                "GET".equalsIgnoreCase(request.getMethod()) ? request.getParameter("page") : null;
        if (!OperationAuditSupport.shouldRecord(
                request.getMethod(), request.getRequestURI(), pageParam)) return;
        if (action.equals("用户登录") && response.getStatus() >= 400) return;
        try {
            long started =
                    request.getAttribute(START_ATTRIBUTE) instanceof Long v ? v : System.nanoTime();
            long elapsed = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
            JsonNode body = jsonBody(request);
            Long userId = parseLong(request.getHeader("X-User-Id"));
            String username = trim(request.getHeader("X-User-Name"), 50);
            // 网关取不到 claim 时会写入字符串 "null"，这里按缺失处理，避免日志出现脏用户名
            if (username != null
                    && (username.isBlank()
                            || "null".equalsIgnoreCase(username)
                            || "undefined".equalsIgnoreCase(username))) username = null;
            if (username == null) username = text(body, "username");
            if (userId == null) userId = parseLong(text(body, "user_id"));
            if (userId == null && username != null) {
                try {
                    userId =
                            jdbc.queryForObject(
                                    "select user_id from sys_users where username=? or email=?"
                                            + " limit 1",
                                    Long.class,
                                    username,
                                    username);
                } catch (Exception ignored) {
                }
            }
            if (username == null && userId != null) {
                try {
                    username =
                            jdbc.queryForObject(
                                    "select username from sys_users where user_id=?",
                                    String.class,
                                    userId);
                } catch (Exception ignored) {
                }
            }
            if (username == null) username = "anonymous";
            int status = exception == null && response.getStatus() < 400 ? 1 : 0;
            String error =
                    exception == null
                            ? (status == 0 ? "HTTP " + response.getStatus() : null)
                            : exception.getMessage();
            jdbc.update(
                    "insert into"
                            + " sys_oper_log(id,user_id,username,action,method,params,ip,status,"
                            + "error_msg,cost_time,create_time)"
                            + " values(?,?,?,?,?,?,?,?,?,?,now())",
                    idGenerator.nextId(),
                    userId,
                    trim(username, 50),
                    trim(action, 255),
                    request.getMethod(),
                    safeParams(request, body),
                    trim(clientIp(request), 50),
                    status,
                    trim(error, 500),
                    Math.min(elapsed, Integer.MAX_VALUE));
        } catch (Exception ignored) {
            /* auditing must not affect the API response */
        }
    }

    private JsonNode jsonBody(HttpServletRequest request) {
        // 请求经过 Spring Security / 网关过滤链后会被再包一层，
        // 直接 instanceof 判断拿不到缓存体，必须沿包装链取原生请求
        ContentCachingRequestWrapper wrapped =
                WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapped == null) return null;
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("json")) return null;
        byte[] bytes = wrapped.getContentAsByteArray();
        if (bytes.length == 0) return null;
        try {
            return json.readTree(bytes);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safeParams(HttpServletRequest request, JsonNode body) {
        // 具体的脱敏与截断规则在 OperationAuditSupport 中，便于单测
        return OperationAuditSupport.paramsJson(json, body, request.getParameterMap());
    }

    private static String text(JsonNode body, String key) {
        JsonNode value = body == null ? null : body.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String action(HttpServletRequest r) {
        return OperationAuditSupport.action(r.getMethod(), r.getRequestURI());
    }

    private static String clientIp(HttpServletRequest r) {
        return OperationAuditSupport.clientIp(
                r.getHeader("X-Forwarded-For"), r.getHeader("X-Real-IP"), r.getRemoteAddr());
    }

    private static String trim(String value, int max) {
        return OperationAuditSupport.trim(value, max);
    }
}
