package edu.swu.agridemo.module.review.dto;

import java.time.LocalDateTime;

public record ReviewVo(
    Long id,
    Long userId,
    String username,
    String userAvatar,
    Long productId,
    String productName,
    Long orderId,
    Integer rating,
    String content,
    String images,
    String merchantReply,
    LocalDateTime createdAt
) {}
