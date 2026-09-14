package edu.swu.agridemo.module.order.controller;

import edu.swu.agridemo.core.result.PageResult;
import edu.swu.agridemo.core.result.Result;
import edu.swu.agridemo.module.order.dto.OrderCreateDto;
import edu.swu.agridemo.module.order.dto.OrderVo;
import edu.swu.agridemo.module.order.entity.Order;
import edu.swu.agridemo.module.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@Tag(name = "订单管理", description = "订单创建、查询、状态管理")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "创建订单")
    public Result<OrderVo> createOrder(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Valid @RequestBody OrderCreateDto dto) {
        return Result.success(orderService.createOrder(userId, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取订单详情")
    public Result<OrderVo> getOrderById(@PathVariable Long id) {
        return Result.success(orderService.getOrderById(id));
    }

    @GetMapping("/no/{orderNo}")
    @Operation(summary = "根据订单号获取订单")
    public Result<OrderVo> getOrderByOrderNo(@PathVariable String orderNo) {
        return Result.success(orderService.getOrderByOrderNo(orderNo));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户订单列表")
    public Result<PageResult<OrderVo>> getUserOrders(
            @PathVariable Long userId,
            @Parameter(description = "订单状态") @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(orderService.getUserOrders(userId, status, page, size));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新订单状态")
    public Result<Void> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam Order.OrderStatus status) {
        orderService.updateOrderStatus(id, status);
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消订单")
    public Result<Void> cancelOrder(
            @PathVariable Long id,
            @RequestParam Long userId) {
        orderService.cancelOrder(id, userId);
        return Result.success();
    }
}
