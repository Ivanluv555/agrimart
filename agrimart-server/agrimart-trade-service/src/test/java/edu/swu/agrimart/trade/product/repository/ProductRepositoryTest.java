package edu.swu.agrimart.trade.product.repository;

import static org.assertj.core.api.Assertions.*;

import edu.swu.agrimart.trade.product.entity.Product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 验证商品持久化仓储，支持按可售状态查询的测试。 */
@SpringBootTest
@Transactional
class ProductRepositoryTest {

    @Autowired ProductRepository products;

    /** 验证商品仓储仅返回指定销售状态的商品。 */
    @Test
    void filtersProductsByStatus() {
        Product onSale = product("Rice", Product.Status.ON_SALE);
        products.saveAndFlush(onSale);
        products.saveAndFlush(product("Tea", Product.Status.OFF_SALE));
        assertThat(products.findByStatus(Product.Status.ON_SALE, org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .extracting(Product::getName)
                .containsExactly("Rice");
    }

    /**
     * 构造测试使用的商品。
     *
     * @param name 商品名称
     * @param status 业务状态
     * @return 测试所需的样例数据或配置对象
     */
    private Product product(String name, Product.Status status) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(new BigDecimal("12.50"));
        p.setStock(4);
        p.setMerchantId(7L);
        p.setStatus(status);
        return p;
    }
}
