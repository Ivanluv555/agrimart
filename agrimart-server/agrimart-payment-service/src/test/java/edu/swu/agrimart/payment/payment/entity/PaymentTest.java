package edu.swu.agrimart.payment.payment.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 验证订单对应的支付记录与当前资金状态的测试。 */
class PaymentTest {
    /** 验证支付默认状态、有效期及可修改字段。 */
    @Test
    void paymentDefaultsExpiryAndMutableFields() {
        OffsetDateTime before = OffsetDateTime.now().plusMinutes(30);
        Payment payment = new Payment();
        assertThat(payment.getStatus()).isEqualTo(Payment.Status.INITIATING);
        assertThat(payment.getExpiresAt()).isBetween(before, OffsetDateTime.now().plusMinutes(30));
        payment.setPaymentNo("P-1");
        payment.setOrderId(2L);
        payment.setBuyerId(8L);
        payment.setAmount(new BigDecimal("24.60"));
        assertThat(payment.getPaymentNo()).isEqualTo("P-1");
        assertThat(payment.getOrderId()).isEqualTo(2L);
        assertThat(payment.getBuyerId()).isEqualTo(8L);
        assertThat(payment.getAmount()).isEqualByComparingTo("24.60");
        for (var status : Payment.Status.values()) {
            payment.setStatus(status);
            assertThat(payment.getStatus()).isEqualTo(status);
        }
    }
}
