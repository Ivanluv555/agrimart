package edu.swu.agrimart.payment.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import edu.swu.agrimart.common.messaging.dto.FundsEvent;
import edu.swu.agrimart.common.messaging.service.MessageStore;
import edu.swu.agrimart.common.payment.dto.PaymentAuthorization;
import edu.swu.agrimart.payment.payment.dto.CreatePayment;
import edu.swu.agrimart.payment.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 消费与回调使用真实本地事务和 H2，验证退款不重复、关闭不覆盖已支付事实。 */
@SpringBootTest(properties={"payment.provider.configured=true","spring.datasource.url=jdbc:h2:mem:funds-payment;DB_CLOSE_DELAY=-1"})
@Transactional
class PaymentFundsIntegrationTest {
    @Autowired PaymentService payments;
    @Autowired RefundService refunds;
    @Autowired PaymentCommandService commands;
    @Autowired PaymentRepository repository;
    @Autowired MessageStore messages;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TradeAuthorizationClient authorization;
    @Test
    void successWinsCloseAndRefundUsesSameNumberForever() {
        when(authorization.authorize(eq(17L),eq(3L),anyString())).thenAnswer(call -> new PaymentAuthorization(
                "A1",call.getArgument(2),17L,3L,8L,"12.30","CNY",OffsetDateTime.now().plusMinutes(10)));
        var payment=payments.create(3L,17L,new CreatePayment(null));
        payments.callback(payment.getPaymentNo(),payment.getAmount(),"provider-paid",OffsetDateTime.now());
        var close=FundsEvent.of(FundsEvent.Kind.CLOSE_PAYMENT,payment.getPaymentNo(),1,17L,payment.getPaymentNo(),null,payment.getAmount(),null,null,null);
        commands.accept(close); commands.accept(close);
        assertThat(payments.state(payment.getPaymentNo()).status()).isEqualTo("SUCCEEDED");
        String no="R"+payment.getPaymentNo();
        var refund=FundsEvent.of(FundsEvent.Kind.EXECUTE_REFUND,no,1,17L,payment.getPaymentNo(),no,payment.getAmount(),null,null,null);
        when(authorization.authorizeRefund(no)).thenReturn(new edu.swu.agrimart.common.payment.dto.RefundState(
                no,payment.getPaymentNo(),17L,"12.30","CNY","PROCESSING",0,null,null));
        commands.accept(refund); commands.accept(refund);
        assertThat(refunds.get(no).status()).isEqualTo("PROCESSING");
        refunds.callback(no,payment.getAmount(),"provider-refunded",OffsetDateTime.now());
        commands.accept(FundsEvent.of(FundsEvent.Kind.EXECUTE_REFUND,no,2,17L,payment.getPaymentNo(),no,payment.getAmount(),null,null,null));
        assertThat(refunds.get(no).status()).isEqualTo("SUCCEEDED");
        assertThat(refunds.get(no).version()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fund_refunds WHERE refund_no=?",Long.class,no)).isEqualTo(1);
    }
    @Test
    void missingPaymentClosurePersistsTombstoneAndPreventsRecreation() {
        String no="P"+UUID.nameUUIDFromBytes("agrimart-order:18".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-","");
        commands.accept(FundsEvent.of(FundsEvent.Kind.CLOSE_PAYMENT,no,1,18L,no,null,new BigDecimal("12.30"),null,null,null));
        assertThat(payments.state(no).status()).isEqualTo("CLOSED");
        assertThatThrownBy(() -> payments.create(3L,18L,new CreatePayment(null))).hasMessage("PAYMENT_ALREADY_CLOSED");
        verifyNoInteractions(authorization);
    }
    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void outboxAppendWithoutLocalTransactionIsRejected() {
        var event=FundsEvent.of(FundsEvent.Kind.CLOSE_PAYMENT,"P-no-tx",1,99L,"P-no-tx",null,new BigDecimal("12.30"),null,null,null);
        assertThatThrownBy(() -> messages.append(event)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
}
