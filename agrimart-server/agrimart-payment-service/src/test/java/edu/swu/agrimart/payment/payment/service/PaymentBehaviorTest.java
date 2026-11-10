package edu.swu.agrimart.payment.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.swu.agrimart.payment.payment.dto.CreatePayment;
import edu.swu.agrimart.payment.payment.entity.Payment;
import edu.swu.agrimart.payment.payment.exception.ProviderMissing;
import edu.swu.agrimart.payment.payment.repository.PaymentRepository;

import org.junit.jupiter.api.Test;

import java.util.*;

/** 验证支付创建、订单复用与异常分支的测试。 */
class PaymentBehaviorTest {
    /** 验证同一订单复用支付记录并保持待支付状态。 */
    @Test
    void createsAndReusesPendingPaymentByOrder() {
        PaymentRepository repo = mock(PaymentRepository.class);
        PaymentService logic = new PaymentService(repo, true);
        when(repo.findByOrderId(7L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        Payment p = logic.create(2L, 7L, new CreatePayment("12.30"));
        assertThat(p.getBuyerId()).isEqualTo(2L);
        assertThat(p.getAmount()).isEqualByComparingTo("12.30");
        assertThat(p.getStatus()).isEqualTo(Payment.Status.PENDING);
        Payment existing = new Payment();
        existing.setPaymentNo("old");
        when(repo.findByOrderId(8L)).thenReturn(Optional.of(existing));
        assertThat(logic.create(2L, 8L, new CreatePayment(null))).isSameAs(existing);
        verify(repo, never()).save(argThat(x -> x.getOrderId().equals(8L)));
    }

    /** 验证未配置供应商和未知支付记录均被拒绝。 */
    @Test
    void rejectsMissingProviderAndUnknownPayment() {
        PaymentRepository repo = mock(PaymentRepository.class);
        assertThatThrownBy(
                        () ->
                                new PaymentService(repo, false)
                                        .create(1L, 2L, new CreatePayment(null)))
                .isInstanceOf(ProviderMissing.class);
        PaymentService logic = new PaymentService(repo, true);
        when(repo.findByPaymentNo("none")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> logic.get("none")).isInstanceOf(NoSuchElementException.class);
    }
}
