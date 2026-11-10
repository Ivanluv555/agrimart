package edu.swu.agrimart.trade.product.dto;

import jakarta.validation.constraints.*;

/**
 * 商品发布请求，包含名称、单价及初始库存。
 *
 * @param name 商品名称
 * @param price 商品单价
 * @param stock 可用库存
 */
public record CreateProduct(
        @NotBlank String name, @NotBlank String price, @Min(0) @Max(1000000) int stock) {}
