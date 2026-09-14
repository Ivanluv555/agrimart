package edu.swu.agridemo.module.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemDto(
    @NotNull(message = "商品ID不能为空")
    Long productId,

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    Integer quantity
) {}
