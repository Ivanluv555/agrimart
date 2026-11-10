package edu.swu.agrimart.payment.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import edu.swu.agrimart.common.messaging.service.MessageStore;
import edu.swu.agrimart.common.payment.dto.PaymentAuthorization;
import edu.swu.agrimart.payment.payment.dto.CreatePayment;
import edu.swu.agrimart.payment.payment.entity.Payment;
import edu.swu.agrimart.payment.payment.exception.ProviderMissing;
import edu.swu.agrimart.payment.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;

/** 创建支付必须采用交易授权金额，并拒绝伪造金额和归属。 */
class PaymentServiceTest {
    @Test
    void disabledProviderDoesNotAccessRepository() {
        var repository=mock(PaymentRepository.class);
        var client=mock(TradeAuthorizationClient.class);
        var messages=mock(MessageStore.class);
        assertThatThrownBy(() -> new PaymentService(repository,false,messages,client,jdbc()).create(1L,2L,new CreatePayment(null)))
                .isInstanceOf(ProviderMissing.class);
        verifyNoInteractions(repository,client,messages);
    }
    @Test
    void usesAuthorizedAmountAndStableNumberAcrossRetries() {
        var repository=mock(PaymentRepository.class);
        var client=mock(TradeAuthorizationClient.class);
        var messages=mock(MessageStore.class);
        when(client.authorize(eq(2L),eq(1L),anyString())).thenAnswer(call -> new PaymentAuthorization(
                "A1",call.getArgument(2),2L,1L,8L,"12.30","CNY",OffsetDateTime.now().plusMinutes(10)));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var logic=new PaymentService(repository,true,messages,client,jdbc());
        Payment first=logic.create(1L,2L,new CreatePayment(null));
        Payment second=logic.create(1L,2L,new CreatePayment("12.30"));
        assertThat(first.getAmount()).isEqualByComparingTo("12.30");
        assertThat(first.getPaymentNo()).matches("P[0-9a-f]{32}").isEqualTo(second.getPaymentNo());
        verify(messages,times(2)).append(any());
    }
    @Test
    void rejectsClientAmountBeforeSaving() {
        var repository=mock(PaymentRepository.class);
        var client=mock(TradeAuthorizationClient.class);
        var messages=mock(MessageStore.class);
        when(client.authorize(eq(2L),eq(1L),anyString())).thenAnswer(call -> new PaymentAuthorization(
                "A1",call.getArgument(2),2L,1L,8L,"12.30","CNY",OffsetDateTime.now().plusMinutes(10)));
        assertThatThrownBy(() -> new PaymentService(repository,true,messages,client,jdbc()).create(1L,2L,new CreatePayment("0.01")))
                .hasMessage("PAYMENT_AMOUNT_MISMATCH");
        verify(repository,never()).save(any()); verify(messages,never()).append(any());
    }
    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Long.class),anyString())).thenReturn(0L);
        return jdbc;
    }
}
