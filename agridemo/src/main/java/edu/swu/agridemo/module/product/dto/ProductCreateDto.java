package edu.swu.agridemo.module.product.dto;

import edu.swu.agridemo.module.product.entity.Product;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductCreateDto(
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 200, message = "商品名称长度不超过200个字符")
    String name,

    String description,

    @NotNull(message = "商品分类不能为空")
    Product.ProductCategory category,

    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.01", message = "商品价格必须大于0")
    BigDecimal price,

    @NotNull(message = "库存数量不能为空")
    @Min(value = 0, message = "库存数量不能为负")
    Integer stock,

    String unit,
    String brand,
    String specification,
    String images,
    String usageGuide
) {}
