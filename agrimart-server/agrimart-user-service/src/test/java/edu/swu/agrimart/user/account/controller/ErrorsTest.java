package edu.swu.agrimart.user.account.controller;

import static org.assertj.core.api.Assertions.*;

import edu.swu.agrimart.common.api.dto.Api;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/** 验证将业务异常转换为当前服务的统一 HTTP 错误响应的测试。 */
class ErrorsTest {
    private final Errors errors = new Errors();

    /** 验证认证失败保留业务错误码并返回未认证状态。 */
    @Test
    void authenticationErrorsPreserveErrorCodeAndReturn401() {
        verify(
                errors.auth(new SecurityException("UNAUTHENTICATED")),
                401,
                "UNAUTHENTICATED",
                "UNAUTHENTICATED");
    }

    /** 记录当前服务令牌失败与权限不足状态的区别。 */
    @Test
    void forbiddenMapsTo403() {
        verify(errors.auth(new SecurityException("FORBIDDEN")), 403, "FORBIDDEN", "FORBIDDEN");
    }

    /** 验证重复账户错误映射为冲突状态。 */
    @Test
    void duplicateAccountMapsTo409() {
        verify(
                errors.conflict(new IllegalStateException("DUPLICATE_ACCOUNT")),
                409,
                "DUPLICATE_ACCOUNT",
                "DUPLICATE_ACCOUNT");
    }

    /** 验证未知异常隐藏敏感内容并生成新的追踪标识。 */
    @Test
    void unexpectedErrorsHideSensitiveDetailsAndUseFreshTraceIds() {
        var first = errors.other(new Exception("database credentials"));
        var second = errors.other(new Exception("internal failure"));
        verify(first, 500, "internal error", "INTERNAL_ERROR");
        verify(second, 500, "internal error", "INTERNAL_ERROR");
        assertThat(first.getBody().traceId()).isNotEqualTo(second.getBody().traceId());
    }

    /**
     * 核对错误响应的状态、错误码和追踪标识。
     *
     * @param response 当前 HTTP 响应
     * @param status 业务状态
     * @param message 响应消息
     * @param code 响应状态码
     */
    private static void verify(
            ResponseEntity<Api.ErrorBody> response, int status, String message, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo(status);
        assertThat(body.message()).isEqualTo(message);
        assertThat(body.errorCode()).isEqualTo(code);
        assertThat(body.data()).isNull();
        assertThat(UUID.fromString(body.traceId()).toString()).isEqualTo(body.traceId());
    }
}
