package edu.swu.agrimart.payment.payment.controller;

import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.common.auth.service.RequestAuth;
import edu.swu.agrimart.payment.payment.dto.CreatePayment;
import edu.swu.agrimart.payment.payment.service.PaymentService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 提供支付创建、查询、同步及内部关单接口。 */
@RestController
@RequestMapping
public class PaymentController {
    final PaymentService logic;
    final RequestAuth identity;
    final String serviceToken;

    /**
     * 创建提供支付创建、查询、同步及内部关单接口。
     *
     * @param l 业务服务
     * @param t 调用凭据
     * @param serviceToken 内部服务调用凭据
     */
    PaymentController(
            PaymentService l, @Value("${agrimart.gateway-token:dev-gateway-token}") String t,
            @Value("${agrimart.service-token:dev-service-token}") String serviceToken) {
        logic = l;
        identity = new RequestAuth(t);
        this.serviceToken = serviceToken;
    }

    /**
     * 验证网关凭据并解析可信用户标识。
     *
     * @param r 当前 HTTP 请求
     * @return 接口响应数据
     */
    private Long user(HttpServletRequest r) {
        return identity.user(r);
    }

    /**
     * 为当前买家的订单创建支付记录。
     *
     * @param id 记录标识
     * @param x 当前业务请求
     * @param r 当前 HTTP 请求
     * @return 接口响应数据
     */
    @PostMapping("/orders/{id}/payments")
    Api.Result<?> create(
            @PathVariable Long id, @RequestBody CreatePayment x, HttpServletRequest r) {
        return Api.Result.ok(logic.create(user(r), id, x));
    }

    /**
     * 查询指定支付记录并校验其买家归属。
     *
     * @param no 业务编号
     * @param r 当前 HTTP 请求
     * @return 包含本人支付记录的统一响应
     */
    @GetMapping("/payments/{no}")
    Api.Result<?> get(@PathVariable String no, HttpServletRequest r) {
        Long buyer = user(r);
        var p = logic.get(no);
        if (!p.getBuyerId().equals(buyer)) throw new NoSuchElementException("RESOURCE_NOT_FOUND");
        return Api.Result.ok(p);
    }

    /**
     * 返回当前支付记录并保留异步同步的响应约定。
     *
     * @param no 业务编号
     * @param r 当前 HTTP 请求
     * @return 接口响应数据
     */
    @PostMapping("/payments/{no}/sync")
    ResponseEntity<Api.Result<?>> sync(@PathVariable String no, HttpServletRequest r) {
        return ResponseEntity.accepted().body(get(no, r));
    }

    /**
     * 关闭指定支付记录并提交状态变更。
     *
     * @param no 业务编号
     * @param request 当前内部请求
     * @return 接口响应数据
     */
    @PostMapping("/internal/payments/{no}/close")
    Api.Result<?> close(@PathVariable String no, HttpServletRequest request) {
        RequestAuth.requireService(request, serviceToken);
        logic.close(no);
        return Api.Result.ok();
    }

    /**
     * 返回支付记录的当前状态。
     *
     * @param no 业务编号
     * @param request 当前内部请求
     * @return 接口响应数据
     */
    @GetMapping("/internal/payments/{no}")
    Api.Result<?> state(@PathVariable String no, HttpServletRequest request) {
        RequestAuth.requireService(request, serviceToken);
        return Api.Result.ok(logic.state(no));
    }
}
