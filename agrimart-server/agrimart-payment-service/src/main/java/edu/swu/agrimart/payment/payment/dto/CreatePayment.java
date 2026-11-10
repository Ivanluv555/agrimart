package edu.swu.agrimart.payment.payment.dto;

/**
 * 支付创建请求，保留现有金额字段契约。
 *
 * @param amount 支付金额
 */
public record CreatePayment(String amount) {}
