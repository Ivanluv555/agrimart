package edu.swu.agrimart.common.payment.dto;

import java.time.OffsetDateTime;

/** P04 权威退款执行状态，交易服务只维护此状态的投影。 */
public record RefundState(String refundNo, String paymentNo, Long orderId, String amount,
        String currency, String status, long version, String providerRefundNo, OffsetDateTime refundedAt) {}
