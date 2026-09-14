package edu.swu.agridemo.module.order.dto;

import edu.swu.agridemo.module.order.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderVo(
    Long id,
    String orderNo,
    Long userId,
    String username,
    List<OrderItemVo> orderItems,
    BigDecimal totalAmount,
    Order.OrderStatus status,
    String shippingAddress,
    String receiverName,
    String receiverPhone,
    String remark,
    String trackingNumber,
    LocalDateTime createdAt
) {}
