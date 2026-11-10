package edu.swu.agrimart.trade.order.repository;

import static org.assertj.core.api.Assertions.*;

import edu.swu.agrimart.trade.order.entity.TradeOrder;
import edu.swu.agrimart.trade.order.service.OrderService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 验证订单持久化仓储，支持按业务订单号查询的测试。 */
@SpringBootTest
@Transactional
class OrderRepositoryTest {

    @Autowired OrderRepository orders;
    @Autowired OrderService service;

    /** 验证支付事件在独立事务提交后，重新读取订单仍保留支付完成状态。 */
    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void paymentEventSurvivesPersistenceContextClear() {
        TradeOrder order = new TradeOrder();
        order.setOrderNo("O-event-persistence");
        order.setBuyerId(9L);
        order.setPaymentNo("P-persistence");
        order.setMerchantId(7L);
        order.setTotalAmount(new BigDecimal("25.00"));
        orders.saveAndFlush(order);
        try {
            service.event(
                    order.getId(),
                    new edu.swu.agrimart.common.payment.dto.PaymentEvent(
                            "E-persistence",
                            "P-persistence",
                            order.getId(),
                            1,
                            "SUCCEEDED",
                            "25.00",
                            "CNY",
                            "provider-persistence",
                            java.time.OffsetDateTime.now()));
            assertThat(orders.findById(order.getId()))
                    .get()
                    .extracting(TradeOrder::getStatus)
                    .isEqualTo(TradeOrder.Status.PAID);
        } finally {
            orders.deleteById(order.getId());
        }
    }

    /** 验证订单保存后可按业务订单号查询。 */
    @Test
    void persistsOrderAndFindsItByBusinessNumber() {
        TradeOrder order = new TradeOrder();
        order.setOrderNo("O-test");
        order.setBuyerId(9L);
        order.setPaymentNo("P-persistence");
        order.setMerchantId(7L);
        order.setTotalAmount(new BigDecimal("25.00"));
        orders.saveAndFlush(order);
        assertThat(orders.findByOrderNo("O-test"))
                .get()
                .extracting(TradeOrder::getBuyerId)
                .isEqualTo(9L);
        assertThat(orders.findByOrderNo("missing")).isEmpty();
    }
}
