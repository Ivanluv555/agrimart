package edu.swu.agrimart.common.messaging.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** v1 资金消息契约。传标识、金额与事实，不传 JPA 实体或订单快照。 */
public record FundsEvent(int schemaVersion, String eventId, Kind kind, String aggregateId,
        long version, OffsetDateTime occurredAt, Long orderId, String paymentNo,
        String refundNo, String amount, String currency, String status,
        String providerReference, OffsetDateTime completedAt) {
    public enum Kind {
        PAYMENT_STATE("payment.state.v1"), CLOSE_PAYMENT("payment.close.v1"),
        EXECUTE_REFUND("refund.execute.v1"), REFUND_STATE("refund.state.v1");

        private final String routingKey;

        Kind(String routingKey) {
            this.routingKey = routingKey;
        }

        public String routingKey() {
            return routingKey;
        }
    }

    public FundsEvent {
        if (schemaVersion != 1 || eventId == null || eventId.isBlank() || eventId.length() > 100
                || kind == null || aggregateId == null || aggregateId.isBlank() || aggregateId.length() > 100
                || version < 1 || occurredAt == null || orderId == null || orderId < 1
                || paymentNo == null || paymentNo.isBlank() || paymentNo.length() > 64
                || !"CNY".equals(currency) || amount == null || !amount.matches("[0-9]{1,8}\\.[0-9]{2}")
                || new BigDecimal(amount).signum() <= 0)
            throw new IllegalArgumentException("INVALID_FUNDS_EVENT");
        boolean refund = kind == Kind.EXECUTE_REFUND || kind == Kind.REFUND_STATE;
        if (refund && (refundNo == null || refundNo.isBlank() || refundNo.length() > 64))
            throw new IllegalArgumentException("INVALID_REFUND_NO");
        if (!aggregateId.equals(refund ? refundNo : paymentNo))
            throw new IllegalArgumentException("INVALID_AGGREGATE");
        if (kind == Kind.PAYMENT_STATE
                && !java.util.Set.of("PENDING", "SUCCEEDED", "CLOSED", "FAILED", "UNKNOWN").contains(status))
            throw new IllegalArgumentException("INVALID_PAYMENT_STATE");
        if (kind == Kind.REFUND_STATE
                && !java.util.Set.of("PROCESSING", "SUCCEEDED", "FAILED", "UNKNOWN").contains(status))
            throw new IllegalArgumentException("INVALID_REFUND_STATE");
        if ((kind == Kind.PAYMENT_STATE || kind == Kind.REFUND_STATE) && "SUCCEEDED".equals(status)
                && (providerReference == null || providerReference.isBlank() || completedAt == null))
            throw new IllegalArgumentException("MISSING_PROVIDER_FACT");
        occurredAt = occurredAt.withOffsetSameInstant(java.time.ZoneOffset.UTC);
        if (completedAt != null)
            completedAt = completedAt.withOffsetSameInstant(java.time.ZoneOffset.UTC);
    }

    public static FundsEvent of(Kind kind, String aggregate, long version, Long order,
            String payment, String refund, BigDecimal amount, String status,
            String reference, OffsetDateTime completed) {
        return new FundsEvent(1, UUID.randomUUID().toString(), kind, aggregate, version,
                OffsetDateTime.now(), order, payment, refund, amount.setScale(2).toPlainString(),
                "CNY", status, reference, completed);
    }
}
