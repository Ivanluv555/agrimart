package edu.swu.agrimart.payment.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.swu.agrimart.payment.payment.dto.CreatePayment;
import edu.swu.agrimart.payment.payment.entity.Payment;
import edu.swu.agrimart.payment.payment.exception.ProviderMissing;
import edu.swu.agrimart.payment.payment.repository.PaymentRepository;

import org.junit.jupiter.api.Test;

/** 验证处理支付记录创建、复用、查询与关闭的测试。 */
class PaymentServiceTest {
    /** 验证供应商未配置时不会访问支付仓储。 */
    @Test
    void disabledProviderDoesNotAccessRepository() {
        PaymentRepository repository = mock(PaymentRepository.class);
        assertThatThrownBy(
                        () ->
                                new PaymentService(repository, false)
                                        .create(1L, 2L, new CreatePayment(null)))
                .isInstanceOf(ProviderMissing.class);
        verifyNoInteractions(repository);
    }

    /** 验证缺省金额兼容现有零值行为，并为不同支付生成唯一支付号。 */
    @Test
    void absentAmountDefaultsToZeroAndGeneratesUniquePaymentNumbers() {
        PaymentRepository repository = mock(PaymentRepository.class);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        PaymentService logic = new PaymentService(repository, true);
        Payment first = logic.create(1L, 2L, new CreatePayment(null));
        Payment second = logic.create(1L, 3L, new CreatePayment(null));
        assertThat(first.getAmount()).isEqualByComparingTo("0.00");
        assertThat(first.getOrderId()).isEqualTo(2L);
        assertThat(first.getBuyerId()).isEqualTo(1L);
        assertThat(first.getStatus()).isEqualTo(Payment.Status.PENDING);
        assertThat(first.getPaymentNo())
                .matches("P[0-9a-f]{32}")
                .isNotEqualTo(second.getPaymentNo());
    }

    /** 验证无效金额在保存支付前被拒绝。 */
    @Test
    void malformedAmountIsRejectedBeforeSave() {
        PaymentRepository repository = mock(PaymentRepository.class);
        assertThatThrownBy(
                        () ->
                                new PaymentService(repository, true)
                                        .create(1L, 2L, new CreatePayment("bad")))
                .isInstanceOf(NumberFormatException.class);
        verify(repository, never()).save(any());
    }
}
