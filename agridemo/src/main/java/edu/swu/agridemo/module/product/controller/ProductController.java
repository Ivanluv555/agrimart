package edu.swu.agridemo.module.product.controller;

import edu.swu.agridemo.core.result.PageResult;
import edu.swu.agridemo.core.result.Result;
import edu.swu.agridemo.module.product.dto.ProductCreateDto;
import edu.swu.agridemo.module.product.dto.ProductVo;
import edu.swu.agridemo.module.product.entity.Product;
import edu.swu.agridemo.module.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@Tag(name = "商品管理", description = "商品CRUD、搜索、筛选")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @Operation(summary = "创建商品")
    public Result<ProductVo> createProduct(
            @Parameter(description = "商家ID") @RequestParam Long merchantId,
            @Valid @RequestBody ProductCreateDto dto) {
        return Result.success(productService.createProduct(merchantId, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取商品详情")
    public Result<ProductVo> getProductById(@PathVariable Long id) {
        return Result.success(productService.getProductById(id));
    }

    @GetMapping
    @Operation(summary = "搜索商品")
    public Result<PageResult<ProductVo>> searchProducts(
            @Parameter(description = "商品分类") @RequestParam(required = false) Product.ProductCategory category,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(productService.searchProducts(category, keyword, page, size));
    }

    @GetMapping("/merchant/{merchantId}")
    @Operation(summary = "获取商家的商品列表")
    public Result<PageResult<ProductVo>> getProductsByMerchant(
            @PathVariable Long merchantId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(productService.getProductsByMerchant(merchantId, page, size));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新商品状态")
    public Result<Void> updateProductStatus(
            @PathVariable Long id,
            @RequestParam Product.ProductStatus status) {
        productService.updateProductStatus(id, status);
        return Result.success();
    }

    @PutMapping("/{id}/stock")
    @Operation(summary = "更新商品库存")
    public Result<Void> updateStock(
            @PathVariable Long id,
            @RequestParam Integer quantity) {
        productService.updateStock(id, quantity);
        return Result.success();
    }
}
