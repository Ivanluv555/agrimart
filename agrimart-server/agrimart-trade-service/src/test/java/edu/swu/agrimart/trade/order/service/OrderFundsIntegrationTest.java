package edu.swu.agrimart.trade.order.service;

import static org.assertj.core.api.Assertions.*;
import edu.swu.agrimart.common.messaging.dto.FundsEvent;
import edu.swu.agrimart.common.messaging.service.MessageStore;
import edu.swu.agrimart.common.messaging.config.RabbitMessagingConfiguration.OutboxRelay;
import edu.swu.agrimart.trade.order.entity.TradeOrder;
import edu.swu.agrimart.trade.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** 覆盖取消与支付相撞、同号退款、旧消息重放及消费回滚。 */
@SpringBootTest
@Transactional
class OrderFundsIntegrationTest {
    @Autowired OrderRepository orders;
    @Autowired OrderService service;
    @Autowired OrderFundsService funds;
    @Autowired MessageStore messages;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;
    TradeOrder order() {
        TradeOrder order=new TradeOrder(); order.setOrderNo("O"+UUID.randomUUID());
        order.setBuyerId(3L); order.setMerchantId(8L); order.setTotalAmount(new BigDecimal("12.30"));
        order.setPaymentNo("P"+UUID.randomUUID()); return orders.saveAndFlush(order);
    }
    FundsEvent fact(TradeOrder order,long version,String status) {
        return FundsEvent.of(FundsEvent.Kind.PAYMENT_STATE,order.getPaymentNo(),version,order.getId(),order.getPaymentNo(),null,
                order.getTotalAmount(),status,"SUCCEEDED".equals(status)?"provider-1":null,"SUCCEEDED".equals(status)?OffsetDateTime.now():null);
    }
    @Test
    void cancellationWaitsForClosureAndLateSuccessCreatesExactlyOneRefund() {
        TradeOrder order=order();
        service.cancel(3L,order.getId()); assertThat(order.getStatus()).isEqualTo(TradeOrder.Status.CLOSING);
        assertThat(messages.pending()).anySatisfy(p -> assertThat(messages.decode(p.payload().getBytes()).kind()).isEqualTo(FundsEvent.Kind.CLOSE_PAYMENT));
        var closed=fact(order,2,"CLOSED"); funds.accept(closed);
        assertThat(order.getStatus()).isEqualTo(TradeOrder.Status.CANCELLED);
        var succeeded=fact(order,3,"SUCCEEDED"); funds.accept(succeeded); funds.accept(succeeded);
        funds.accept(fact(order,1,"PENDING"));
        assertThat(order.getStatus()).isEqualTo(TradeOrder.Status.REFUNDING);
        String refund="R"+order.getPaymentNo();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fund_refunds WHERE payment_no=?",Long.class,order.getPaymentNo())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mq_outbox WHERE routing_key='refund.execute.v1' AND payload LIKE ?",Long.class,"%"+refund+"%")).isEqualTo(1);
        funds.accept(FundsEvent.of(FundsEvent.Kind.REFUND_STATE,refund,2,order.getId(),order.getPaymentNo(),refund,
                order.getTotalAmount(),"SUCCEEDED","provider-refund",OffsetDateTime.now()));
        assertThat(order.getStatus()).isEqualTo(TradeOrder.Status.REFUNDED);
    }
    @Test
    void ordinarySuccessNeverRegressesOnStaleClosure() {
        var order=order(); funds.accept(fact(order,3,"SUCCEEDED")); funds.accept(fact(order,2,"CLOSED"));
        assertThat(order.getStatus()).isEqualTo(TradeOrder.Status.PAID);
        assertThat(order.getPaymentVersion()).isEqualTo(3);
    }
    @Test
    void rejectsWrongAmountAndPaymentBinding() {
        var order=order();
        var wrong=FundsEvent.of(FundsEvent.Kind.PAYMENT_STATE,order.getPaymentNo(),2,order.getId(),order.getPaymentNo(),null,
                new BigDecimal("0.01"),"CLOSED",null,null);
        assertThatThrownBy(() -> funds.accept(wrong)).hasMessage("PAYMENT_BINDING_OR_AMOUNT_MISMATCH");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mq_inbox WHERE event_id=?",Long.class,wrong.eventId())).isZero();
    }
    @Test
    void authorizationBindsOnceAndChecksBuyer() {
        var order=order();
        assertThatThrownBy(() -> service.authorize(order.getId(),Map.of("paymentNo",order.getPaymentNo(),"buyerId",99L)))
                .hasMessage("INVALID_PAYMENT_AUTHORIZATION");
        assertThatThrownBy(() -> service.authorize(order.getId(),Map.of("paymentNo","another-payment","buyerId",3L)))
                .hasMessage("PAYMENT_ALREADY_BOUND");
    }
    @Test
    void brokerIsOptionalAndOutboxRequiresBusinessTransaction() {
        assertThat(context.getBeansOfType(OutboxRelay.class)).isEmpty();
        assertThat(context.getBeansOfType(edu.swu.agrimart.trade.messaging.TradeFundsListener.class)).isEmpty();
    }
}
