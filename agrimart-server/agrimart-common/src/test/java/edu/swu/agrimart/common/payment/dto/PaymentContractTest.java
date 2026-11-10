package edu.swu.agrimart.common.payment.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

/** 验证支付授权与事件协议字段的测试。 */
class PaymentContractTest {
    /** 验证支付授权协议保留所有授权字段。 */
    @Test
    void authorizationPreservesAllPaymentFields() {
        var expiry = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        var auth = new PaymentAuthorization("auth", "payment", 1L, 2L, 3L, "12.50", "CNY", expiry);
        assertEquals("auth", auth.authorizationNo());
        assertEquals("payment", auth.paymentNo());
        assertEquals(1L, auth.orderId());
        assertEquals(2L, auth.buyerId());
        assertEquals(3L, auth.merchantId());
        assertEquals("12.50", auth.amount());
        assertEquals("CNY", auth.currency());
        assertEquals(expiry, auth.expiresAt());
    }

    /** 验证支付事件协议保留供应商字段和事件版本。 */
    @Test
    void eventPreservesAllProviderFieldsAndVersion() {
        var paid = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        var event =
                new PaymentEvent(
                        "event", "payment", 1L, 4L, "SUCCESS", "12.50", "CNY", "txn", paid);
        assertEquals("event", event.eventId());
        assertEquals("payment", event.paymentNo());
        assertEquals(1L, event.orderId());
        assertEquals(4L, event.version());
        assertEquals("SUCCESS", event.status());
        assertEquals("12.50", event.amount());
        assertEquals("CNY", event.currency());
        assertEquals("txn", event.providerTransactionNo());
        assertEquals(paid, event.paidAt());
    }
}
