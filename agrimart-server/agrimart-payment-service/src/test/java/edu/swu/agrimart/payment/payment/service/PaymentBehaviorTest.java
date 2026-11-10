package edu.swu.agrimart.payment.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import edu.swu.agrimart.common.messaging.service.MessageStore;
import edu.swu.agrimart.payment.payment.dto.CreatePayment;
import edu.swu.agrimart.payment.payment.entity.Payment;
import edu.swu.agrimart.payment.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;

/** 已有支付复用时仍需检查所属买家。 */
class PaymentBehaviorTest {
    @Test
    void reusesOwnedPaymentAndRejectsOtherBuyer() {
        var repository=mock(PaymentRepository.class);
        var client=mock(TradeAuthorizationClient.class);
        var payment=new Payment(); payment.setBuyerId(2L);
        when(repository.findByOrderId(7L)).thenReturn(Optional.of(payment));
        var logic=new PaymentService(repository,true,mock(MessageStore.class),client,jdbc());
        assertThat(logic.create(2L,7L,new CreatePayment(null))).isSameAs(payment);
        assertThatThrownBy(() -> logic.create(3L,7L,new CreatePayment(null))).hasMessage("RESOURCE_NOT_FOUND");
        assertThatThrownBy(() -> logic.get("missing")).hasMessage("RESOURCE_NOT_FOUND");
        verifyNoInteractions(client);
    }
    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Long.class),anyString())).thenReturn(0L);
        return jdbc;
    }
}
