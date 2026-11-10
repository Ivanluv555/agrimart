package edu.swu.agrimart.common.auth.service;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 统一解析用户令牌、校验内部服务凭据及可信网关身份。 */
public final class RequestAuth {
    private final String gatewayToken;

    /**
     * 创建可信网关身份解析器。
     * @param gatewayToken 预期的网关调用凭据
     */
    public RequestAuth(String gatewayToken) {
        this.gatewayToken = gatewayToken;
    }

    /**
     * 校验非空凭据，使用 MessageDigest 的字节比较。
     * @param actual 请求中的凭据
     * @param expected 预期凭据
     * @return 两个非空凭据是否完全一致
     */
    public static boolean matches(String actual, String expected) {
        return actual != null && !actual.isBlank() && expected != null && !expected.isBlank()
                && MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 要求有效内部服务凭据；用户令牌或网关身份不能代替服务凭据。
     * @param request 当前请求
     * @param expected 预期服务凭据
     */
    public static void requireService(HttpServletRequest request, String expected) {
        if (!matches(request.getHeader("X-Service-Token"), expected))
            throw new SecurityException("UNAUTHENTICATED");
    }

    /**
     * 读取 Bearer 令牌，不接受空白令牌。
     * @param request 当前请求
     * @return Bearer 令牌，缺失或格式不正确时为 null
     */
    public static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        return token.isBlank() ? null : token;
    }

    /**
     * 按 HTTP 方法及精确路径匹配契约中无需用户登录的操作。
     * @param method HTTP 方法
     * @param path 不含网关 /api 前缀的业务路径
     * @return 是否无需用户令牌；回调仍必须由供应商适配器验签
     */
    public static boolean publicOperation(String method, String path) {
        if ("POST".equals(method)) {
            return path.equals("/users/register") || path.equals("/users/login")
                    || path.matches("/payment-callbacks/[A-Za-z0-9_-]+/(payments|refunds)");
        }
        return "GET".equals(method) && (path.equals("/products")
                || path.matches("/products/[1-9][0-9]*")
                || path.matches("/products/merchant/[1-9][0-9]*")
                || path.matches("/reviews/product/[1-9][0-9]*"));
    }

    /**
     * 校验网关凭据后解析正整数用户标识。
     * @param request 当前请求
     * @return 可信用户标识
     */
    public Long user(HttpServletRequest request) {
        if (!matches(request.getHeader("X-Gateway-Token"), gatewayToken))
            throw new SecurityException("UNAUTHENTICATED");
        try {
            long id = Long.parseLong(request.getHeader("X-User-Id"));
            if (id <= 0 || id > 9007199254740991L) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new SecurityException("UNAUTHENTICATED");
        }
    }

    /**
     * 区分认证失败与已经认证但权限不足。
     * @param failure 认证或授权异常
     * @return 对应 HTTP 状态码
     */
    public static int status(SecurityException failure) {
        return "FORBIDDEN".equals(failure.getMessage())
                || "MERCHANT_NOT_APPROVED".equals(failure.getMessage()) ? 403 : 401;
    }
}
