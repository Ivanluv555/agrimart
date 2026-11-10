package edu.swu.agrimart.trade.order.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 验证单商家订单、应付金额及履约状态的测试。 */
class TradeOrderTest {
    /** 验证订单默认状态、创建时间及可修改字段。 */
    @Test
    void orderDefaultsAndMutableFields() {
        OffsetDateTime before = OffsetDateTime.now();
        TradeOrder order = new TradeOrder();
        assertThat(order.getId()).isNull();
        assertThat(order.getCreatedAt()).isBetween(before, OffsetDateTime.now());
        assertThat(order.getStatus()).isEqualTo(TradeOrder.Status.PENDING_PAYMENT);
        order.setOrderNo("O-1");
        order.setBuyerId(8L);
        order.setMerchantId(7L);
        order.setTotalAmount(new BigDecimal("24.60"));
        assertThat(order.getOrderNo()).isEqualTo("O-1");
        assertThat(order.getBuyerId()).isEqualTo(8L);
        assertThat(order.getMerchantId()).isEqualTo(7L);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("24.60");
        for (var status : TradeOrder.Status.values()) {
            order.setStatus(status);
            assertThat(order.getStatus()).isEqualTo(status);
        }
    }
}
