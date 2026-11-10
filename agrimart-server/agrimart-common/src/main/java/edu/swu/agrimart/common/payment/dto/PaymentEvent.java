package edu.swu.agrimart.common.payment.dto;

import java.time.OffsetDateTime;

/**
 * 供交易服务归并的支付状态事件。
 *
 * @param eventId               支付事件标识
 * @param paymentNo             支付业务号
 * @param orderId               订单标识
 * @param version               事件或数据版本
 * @param status                业务状态
 * @param amount                支付金额
 * @param currency              币种
 * @param providerTransactionNo 支付供应商交易号
 * @param paidAt                支付完成时间
 */
public record PaymentEvent(
        String eventId,
        String paymentNo,
        Long orderId,
        long version,
        String status,
        String amount,
        String currency,
        String providerTransactionNo,
        OffsetDateTime paidAt) {
}
