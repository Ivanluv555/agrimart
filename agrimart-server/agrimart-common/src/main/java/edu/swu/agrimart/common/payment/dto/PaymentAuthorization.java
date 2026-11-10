package edu.swu.agrimart.common.payment.dto;

import java.time.OffsetDateTime;

/**
 * 交易服务向支付服务返回的订单支付授权。
 *
 * @param authorizationNo 支付授权号
 * @param paymentNo 支付业务号
 * @param orderId 订单标识
 * @param buyerId 买家标识
 * @param merchantId 商家标识
 * @param amount 支付金额
 * @param currency 币种
 * @param expiresAt 到期时间
 */
public record PaymentAuthorization(
        String authorizationNo,
        String paymentNo,
        Long orderId,
        Long buyerId,
        Long merchantId,
        String amount,
        String currency,
        OffsetDateTime expiresAt) {}
