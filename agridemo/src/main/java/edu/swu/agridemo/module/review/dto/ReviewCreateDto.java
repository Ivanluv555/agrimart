package edu.swu.agridemo.module.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReviewCreateDto(
    @NotNull(message = "订单ID不能为空")
    Long orderId,

    @NotNull(message = "商品ID不能为空")
    Long productId,

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    Integer rating,

    String content,
    String images
) {}
