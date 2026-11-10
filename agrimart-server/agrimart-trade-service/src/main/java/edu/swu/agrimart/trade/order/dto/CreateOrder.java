package edu.swu.agrimart.trade.order.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 结算请求，包含待购买的商品及数量。
 *
 * @param items 待购买的商品项
 */
public record CreateOrder(@NotEmpty List<Item> items) {}
