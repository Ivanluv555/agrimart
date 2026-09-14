package edu.swu.agridemo.module.product.dto;

import edu.swu.agridemo.module.product.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductVo(
    Long id,
    String name,
    String description,
    Product.ProductCategory category,
    BigDecimal price,
    Integer stock,
    String unit,
    String brand,
    String specification,
    String images,
    Product.ProductStatus status,
    Long merchantId,
    String merchantName,
    Integer salesCount,
    BigDecimal rating,
    String usageGuide,
    LocalDateTime createdAt
) {}
