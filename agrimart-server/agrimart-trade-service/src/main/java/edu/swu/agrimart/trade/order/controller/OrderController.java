package edu.swu.agrimart.trade.order.controller;

import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.common.auth.service.RequestAuth;
import edu.swu.agrimart.common.payment.dto.PaymentEvent;
import edu.swu.agrimart.trade.order.dto.CreateOrder;
import edu.swu.agrimart.trade.order.entity.TradeOrder;
import edu.swu.agrimart.trade.order.service.OrderService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 提供结算、本人订单及内部支付协作接口。 */
@RestController
public class OrderController {
    private final OrderService orders;
    private final RequestAuth identity;
    private final String serviceToken;

    /**
     * 创建提供结算、本人订单及内部支付协作接口。
     *
     * @param orders 订单业务依赖
     * @param identity 测试输入或业务依赖
     * @param serviceToken 内部调用凭据
     */
    public OrderController(
            OrderService orders,
            RequestAuth identity,
            @Value("${agrimart.service-token:dev-service-token}") String serviceToken) {
        this.orders = orders;
        this.identity = identity;
        this.serviceToken = serviceToken;
    }

    /**
     * 按服务端商品价格结算单商家订单并扣减库存。
     *
     * @param request 请求参数或请求上下文
     * @param servletRequest 当前 HTTP 请求
     * @return 接口响应数据
     */
    @PostMapping("/checkouts")
    public Api.Result<?> order(
            @Valid @RequestBody CreateOrder request, HttpServletRequest servletRequest) {
        return Api.Result.ok(orders.order(identity.user(servletRequest), request));
    }

    /**
     * 查询指定订单并校验其买家归属。
     *
     * @param id 记录标识
     * @param request 请求参数或请求上下文
     * @return 包含本人订单的统一响应
     */
    @GetMapping("/orders/{id}")
    public Api.Result<?> get(@PathVariable Long id, HttpServletRequest request) {
        TradeOrder order = orders.get(id);
        if (!order.getBuyerId().equals(identity.user(request)))
            throw new NoSuchElementException("RESOURCE_NOT_FOUND");
        return Api.Result.ok(order);
    }

    /**
     * 校验订单归属及状态并取消待支付订单。
     *
     * @param id 记录标识
     * @param request 请求参数或请求上下文
     * @return 接口响应数据
     */
    @PostMapping("/orders/{id}/cancel")
    public Api.Result<?> cancel(@PathVariable Long id, HttpServletRequest request) {
        return Api.Result.ok(orders.cancel(identity.user(request), id));
    }

    /**
     * 为待支付订单生成包含可信金额的支付授权。
     *
     * @param id 记录标识
     * @param body 请求业务数据
     * @param request 请求参数或请求上下文
     * @return 接口响应数据
     */
    @PostMapping("/internal/orders/{id}/payment-authorizations")
    public Api.Result<?> authorize(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        requireService(request);
        return Api.Result.ok(orders.authorize(id, body));
    }

    /**
     * 应用已支持的支付成功或关闭事件。
     *
     * @param id 记录标识
     * @param event 测试输入或业务依赖
     * @param request 请求参数或请求上下文
     * @return 接口响应数据
     */
    @PostMapping("/internal/orders/{id}/payment-events")
    public Api.Result<?> event(
            @PathVariable Long id, @RequestBody PaymentEvent event, HttpServletRequest request) {
        requireService(request);
        orders.event(id, event);
        return Api.Result.ok();
    }

    /**
     * 校验内部订单调用凭据。
     *
     * @param request 请求参数或请求上下文
     */
    private void requireService(HttpServletRequest request) {
        RequestAuth.requireService(request, serviceToken);
    }
}
