package edu.swu.agrimart.payment.payment.controller;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.*;

/** 验证支付业务异常映射为统一错误响应的测试。 */
class ErrorsTest {
    /** 验证供应商缺失、认证失败及支付不存在的错误映射。 */
    @Test
    void errorsMapProviderSecurityAndMissingPayment() {
        Errors errors = new Errors();
        assertThat(errors.providerMissing().getStatusCode().value()).isEqualTo(503);
        assertThat(errors.auth(new SecurityException("UNAUTHENTICATED")).getStatusCode().value())
                .isEqualTo(401);
        assertThat(errors.auth(new SecurityException("FORBIDDEN")).getStatusCode().value()).isEqualTo(403);
        assertThat(errors.missing(new NoSuchElementException()).getStatusCode().value())
                .isEqualTo(404);
    }
}
