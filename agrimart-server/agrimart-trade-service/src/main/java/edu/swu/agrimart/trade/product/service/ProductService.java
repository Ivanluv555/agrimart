package edu.swu.agrimart.trade.product.service;

import edu.swu.agrimart.trade.product.dto.CreateProduct;
import edu.swu.agrimart.trade.product.entity.Product;
import edu.swu.agrimart.trade.product.repository.ProductRepository;
import edu.swu.agrimart.common.api.dto.PageResult;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 处理商品上架与在售商品查询。 */
@Service
public class ProductService {
    private final ProductRepository products;

    /**
     * 创建商品发布与查询服务。
     *
     * @param products 商品业务依赖
     */
    public ProductService(ProductRepository products) {
        this.products = products;
    }

    /**
     * 查询当前在售商品。
     *
     * @param page 从 1 开始的页码，最大 1000000
     * @param size 每页条数，范围 1 到 100
     * @return 按创建时间及 ID 倒序的商品分页
     */
    public PageResult<Product> list(int page, int size) {
        if (page < 1 || page > 1000000 || size < 1 || size > 100)
            throw new IllegalArgumentException("VALIDATION_ERROR");
        var result = products.findByStatus(Product.Status.ON_SALE,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PageResult<>(result.getContent(), result.getTotalElements(), page, size, result.getTotalPages());
    }

    /**
     * 创建并保存商家商品。
     *
     * @param merchant 当前商家标识
     * @param request 请求参数或请求上下文
     * @return 已持久化的商品
     */
    @Transactional
    public Product create(Long merchant, CreateProduct request) {
        Product product = new Product();
        product.setMerchantId(merchant);
        product.setName(request.name());
        product.setPrice(new BigDecimal(request.price()));
        product.setStock(request.stock());
        return products.save(product);
    }
}
