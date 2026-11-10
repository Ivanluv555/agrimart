package edu.swu.agrimart.payment.payment.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.swu.agrimart.payment.payment.entity.Payment;
import edu.swu.agrimart.payment.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 验证支付内部接口在访问业务前鉴权，同步接口校验买家归属。 */
class PaymentSecurityTest {
    /**
     * 验证查询与关单均拒绝缺失、错误或用户类型的凭据。
     * @param token 请求服务凭据
     * @throws Exception HTTP 测试失败
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"wrong", "gateway", " service", "SERVICE"})
    void internalEndpointsRequireServiceCredential(String token) throws Exception {
        var logic = mock(PaymentService.class);
        var mvc = mvc(logic);
        var state = get("/internal/payments/P1").header("Authorization", "Bearer user-token");
        var close = post("/internal/payments/P1/close").header("X-Gateway-Token", "gateway");
        if (token != null) {
            state.header("X-Service-Token", token);
            close.header("X-Service-Token", token);
        }
        mvc.perform(state).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        mvc.perform(close).andExpect(status().isUnauthorized());
        verifyNoInteractions(logic);
    }

    /**
     * 验证合法内部凭据可查询及关单。
     * @throws Exception HTTP 测试失败
     */
    @Test
    void validServiceCredentialAllowsInternalOperations() throws Exception {
        var logic = mock(PaymentService.class);
        when(logic.get("P1")).thenReturn(new Payment());
        var mvc = mvc(logic);
        mvc.perform(get("/internal/payments/P1").header("X-Service-Token", "service"))
                .andExpect(status().isOk());
        mvc.perform(post("/internal/payments/P1/close").header("X-Service-Token", "service"))
                .andExpect(status().isOk());
        verify(logic).close("P1");
    }

    /**
     * 验证同步接口不向其他买家或匿名请求泄露支付记录。
     * @throws Exception HTTP 测试失败
     */
    @Test
    void syncRequiresIdentityAndPaymentOwnership() throws Exception {
        var logic = mock(PaymentService.class);
        var mvc = mvc(logic);
        mvc.perform(post("/payments/P1/sync")).andExpect(status().isUnauthorized());
        verifyNoInteractions(logic);
        Payment payment = new Payment();
        payment.setBuyerId(7L);
        when(logic.get("P1")).thenReturn(payment);
        mvc.perform(post("/payments/P1/sync").header("X-Gateway-Token", "gateway")
                        .header("X-User-Id", "8"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/payments/P1/sync").header("X-Gateway-Token", "gateway")
                        .header("X-User-Id", "7"))
                .andExpect(status().isAccepted());
    }

    /**
     * 创建含实际异常映射的 MVC 测试入口。
     * @param logic 模拟支付逻辑
     * @return MVC 测试客户端
     */
    private MockMvc mvc(PaymentService logic) {
        return MockMvcBuilders.standaloneSetup(new PaymentController(logic, "gateway", "service"))
                .setControllerAdvice(new Errors()).build();
    }
}
