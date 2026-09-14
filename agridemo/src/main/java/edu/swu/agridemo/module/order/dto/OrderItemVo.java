package edu.swu.agridemo.module.order.dto;

import java.math.BigDecimal;

public record OrderItemVo(
    Long id,
    Long productId,
    String productName,
    BigDecimal price,
    Integer quantity,
    BigDecimal subtotal,
    String productImage
) {}
