package edu.swu.agrimart.payment.payment.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.swu.agrimart.payment.payment.dto.CreatePayment;
import edu.swu.agrimart.payment.payment.entity.Payment;
import edu.swu.agrimart.payment.payment.service.PaymentService;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.*;

/** 验证支付接口身份校验、生命周期委托及响应约定的测试。 */
class PaymentControllerTest {
    /** 验证支付接口的身份检查、查询、同步及关单委托。 */
    @Test
    void controllerChecksUserAndSupportsPaymentLifecycleEndpoints() {
        PaymentService logic = mock(PaymentService.class);
        PaymentController c = new PaymentController(logic, "token", "service");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Service-Token")).thenReturn("service");
        when(req.getHeader("X-Gateway-Token")).thenReturn("token");
        when(req.getHeader("X-User-Id")).thenReturn("4");
        Payment p = new Payment();
        p.setBuyerId(4L);
        p.setPaymentNo("P1");
        when(logic.create(4L, 7L, new CreatePayment(null))).thenReturn(p);
        assertThat(c.create(7L, new CreatePayment(null), req).data()).isSameAs(p);
        when(logic.get("P1")).thenReturn(p);
        assertThat(c.get("P1", req).data()).isSameAs(p);
        ResponseEntity<?> accepted = c.sync("P1", req);
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        c.close("P1", req);
        verify(logic).close("P1");
        c.state("P1", req);
        verify(logic).state("P1");
        when(req.getHeader("X-User-Id")).thenReturn("5");
        assertThatThrownBy(() -> c.get("P1", req)).isInstanceOf(NoSuchElementException.class);
    }
}
