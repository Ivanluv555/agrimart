package edu.swu.agrimart.trade.product.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

/** 验证商品信息、可售状态及库存数量的测试。 */
class ProductTest {
    /** 验证商品默认状态与可修改字段。 */
    @Test
    void productDefaultsAndMutableFields() {
        Product product = new Product();
        assertThat(product.getId()).isNull();
        assertThat(product.getStatus()).isEqualTo(Product.Status.ON_SALE);
        product.setName("Rice");
        product.setPrice(new BigDecimal("12.30"));
        product.setStock(8);
        product.setMerchantId(7L);
        assertThat(product.getName()).isEqualTo("Rice");
        assertThat(product.getPrice()).isEqualByComparingTo("12.30");
        assertThat(product.getStock()).isEqualTo(8);
        assertThat(product.getMerchantId()).isEqualTo(7L);
        for (var status : Product.Status.values()) {
            product.setStatus(status);
            assertThat(product.getStatus()).isEqualTo(status);
        }
    }
}
