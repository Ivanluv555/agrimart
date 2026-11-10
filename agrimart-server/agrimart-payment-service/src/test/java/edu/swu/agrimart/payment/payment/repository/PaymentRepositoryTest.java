package edu.swu.agrimart.payment.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import edu.swu.agrimart.payment.payment.entity.Payment;
import edu.swu.agrimart.payment.payment.service.PaymentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

/** 验证支付持久化仓储，支持按支付号和订单标识查询的测试。 */
@org.springframework.transaction.annotation.Transactional
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "payment.provider.configured=true",
            "spring.datasource.url=jdbc:h2:mem:payment;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "agrimart.gateway-token=test-token"
        })
class PaymentRepositoryTest {
    @Autowired PaymentRepository payments;
    @Autowired PaymentService service;

    /** 验证服务关单在独立事务提交后，重新读取支付记录仍保留关闭状态。 */
    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void paymentClosureSurvivesPersistenceContextClear() {
        Payment payment = new Payment();
        payment.setPaymentNo("P-close-persistence");
        payment.setOrderId(88L);
        payment.setBuyerId(5L);
        payment.setAmount(new BigDecimal("19.90"));
        payments.saveAndFlush(payment);
        try {
            service.close(payment.getPaymentNo());
            assertThat(payments.findByPaymentNo(payment.getPaymentNo()))
                    .get()
                    .extracting(Payment::getStatus)
                    .isEqualTo(Payment.Status.CLOSED);
        } finally {
            payments.delete(payments.findByPaymentNo(payment.getPaymentNo()).orElseThrow());
        }
    }

    /** 验证支付记录可按支付号与订单查询，未知键返回空结果。 */
    @Test
    void findsPersistedPaymentByNumberAndOrderAndReturnsEmptyForUnknownKeys() {
        Payment p = new Payment();
        p.setPaymentNo("P-1");
        p.setBuyerId(5L);
        p.setOrderId(10L);
        p.setAmount(new BigDecimal("19.90"));
        payments.saveAndFlush(p);
        assertThat(payments.findByPaymentNo("P-1"))
                .get()
                .extracting(Payment::getOrderId)
                .isEqualTo(10L);
        assertThat(payments.findByOrderId(10L))
                .get()
                .extracting(Payment::getPaymentNo)
                .isEqualTo("P-1");
        assertThat(payments.findByPaymentNo("absent")).isEmpty();
        assertThat(payments.findByOrderId(99L)).isEmpty();
    }
}
