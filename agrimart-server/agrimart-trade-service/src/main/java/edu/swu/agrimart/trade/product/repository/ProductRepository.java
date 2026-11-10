package edu.swu.agrimart.trade.product.repository;

import edu.swu.agrimart.trade.product.entity.Product;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 商品持久化仓储，支持按可售状态查询。 */
public interface ProductRepository extends JpaRepository<Product, Long> {
    /**
     * 按销售状态查找商品。
     *
     * @param status 业务状态
     * @param pageable 分页及排序条件
     * @return 满足销售状态条件的商品列表
     */
    Page<Product> findByStatus(Product.Status status, Pageable pageable);
}
