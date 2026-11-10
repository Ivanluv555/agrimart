package edu.swu.agrimart.trade.product.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.swu.agrimart.trade.product.dto.CreateProduct;
import edu.swu.agrimart.trade.product.entity.Product;
import edu.swu.agrimart.trade.product.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

/** 验证处理商品上架与在售商品查询的测试。 */
class ProductServiceTest {
    ProductRepository products;
    ProductService logic;

    /** 初始化当前测试所需的替身、校验器或样例数据。 */
    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        logic = new ProductService(products);
    }

    /** 验证商品列表只查询在售商品。 */
    @Test
    void listsOnlyOnSaleProducts() {
        var expected = List.of(new Product());
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id"));
        when(products.findByStatus(Product.Status.ON_SALE, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(expected, pageable, 1));
        assertThat(logic.list(1, 10).records()).isEqualTo(expected);
        verify(products).findByStatus(Product.Status.ON_SALE, pageable);
    }

    /** 验证商品发布保存商家归属、价格与库存。 */
    @Test
    void createsProductWithMerchantPriceAndStock() {
        when(products.save(any())).thenAnswer(call -> call.getArgument(0));
        Product result = logic.create(7L, new CreateProduct("Rice", "12.30", 8));
        assertThat(result.getName()).isEqualTo("Rice");
        assertThat(result.getPrice()).isEqualByComparingTo("12.30");
        assertThat(result.getStock()).isEqualTo(8);
        assertThat(result.getMerchantId()).isEqualTo(7L);
        assertThat(result.getStatus()).isEqualTo(Product.Status.ON_SALE);
        verify(products).save(result);
    }

    /** 验证无法解析的价格不会保存商品。 */
    @Test
    void invalidPriceDoesNotSave() {
        assertThatThrownBy(() -> logic.create(7L, new CreateProduct("Rice", "abc", 1)))
                .isInstanceOf(NumberFormatException.class);
        verifyNoInteractions(products);
    }
}
