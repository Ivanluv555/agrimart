package edu.swu.agridemo.module.product.repository;

import edu.swu.agridemo.module.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByStatus(Product.ProductStatus status, Pageable pageable);

    Page<Product> findByCategory(Product.ProductCategory category, Pageable pageable);

    Page<Product> findByCategoryAndStatus(Product.ProductCategory category, Product.ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.deleted = false AND p.status = :status " +
           "AND (:category IS NULL OR p.category = :category) " +
           "AND (:keyword IS NULL OR p.name LIKE %:keyword% OR p.description LIKE %:keyword%)")
    Page<Product> searchProducts(@Param("status") Product.ProductStatus status,
                                  @Param("category") Product.ProductCategory category,
                                  @Param("keyword") String keyword,
                                  Pageable pageable);

    Page<Product> findByMerchantId(Long merchantId, Pageable pageable);
}
