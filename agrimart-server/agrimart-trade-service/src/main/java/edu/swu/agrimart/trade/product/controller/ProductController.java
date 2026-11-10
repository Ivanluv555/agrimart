package edu.swu.agrimart.trade.product.controller;

import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.common.api.dto.PageResult;
import edu.swu.agrimart.trade.product.entity.Product;
import edu.swu.agrimart.common.auth.service.RequestAuth;
import edu.swu.agrimart.trade.product.dto.CreateProduct;
import edu.swu.agrimart.trade.product.service.ProductService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import org.springframework.web.bind.annotation.*;

/** 提供商品公开列表及发布接口。 */
@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService products;
    private final RequestAuth identity;

    /**
     * 创建提供商品公开列表及发布接口。
     *
     * @param products 商品业务依赖
     * @param identity 测试输入或业务依赖
     */
    public ProductController(ProductService products, RequestAuth identity) {
        this.products = products;
        this.identity = identity;
    }

    /**
     * 返回在售商品列表。
     *
     * @param page 从 1 开始的页码
     * @param size 每页记录条数
     * @return 接口响应数据
     */
    @GetMapping
    public Api.Result<PageResult<Product>> products(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Api.Result.ok(products.list(page, size));
    }

    /**
     * 发布当前商家的商品。
     *
     * @param request 请求参数或请求上下文
     * @param servletRequest 当前 HTTP 请求
     * @return 接口响应数据
     */
    @PostMapping
    public Api.Result<?> create(
            @Valid @RequestBody CreateProduct request, HttpServletRequest servletRequest) {
        return Api.Result.ok(products.create(identity.user(servletRequest), request));
    }
}
