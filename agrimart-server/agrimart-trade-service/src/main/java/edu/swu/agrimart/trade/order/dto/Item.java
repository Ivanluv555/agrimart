package edu.swu.agrimart.trade.order.dto;

import jakarta.validation.constraints.Positive;

/**
 * 订单中的商品标识及购买数量。
 *
 * @param productId 商品标识
 * @param quantity 购买数量
 */
public record Item(@Positive long productId, @Positive int quantity) {}
