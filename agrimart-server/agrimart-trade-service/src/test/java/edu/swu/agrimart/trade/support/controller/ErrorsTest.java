package edu.swu.agrimart.trade.support.controller;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.*;

/** 验证将业务异常转换为当前服务的统一 HTTP 错误响应的测试。 */
class ErrorsTest {
    /** 验证交易领域异常映射到既有 HTTP 状态。 */
    @Test
    void errorsMapDomainFailures() {
        Errors errors = new Errors();
        assertThat(errors.auth(new SecurityException("UNAUTHENTICATED")).getStatusCode().value())
                .isEqualTo(401);
        assertThat(errors.auth(new SecurityException("FORBIDDEN")).getStatusCode().value()).isEqualTo(403);
        assertThat(errors.missing(new NoSuchElementException()).getStatusCode().value())
                .isEqualTo(404);
        assertThat(errors.conflict(new IllegalStateException()).getStatusCode().value())
                .isEqualTo(409);
    }
}
