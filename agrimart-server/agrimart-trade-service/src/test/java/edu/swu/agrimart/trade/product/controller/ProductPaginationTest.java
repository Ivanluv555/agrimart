package edu.swu.agrimart.trade.product.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.swu.agrimart.trade.product.entity.Product;
import edu.swu.agrimart.trade.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 验证商品分页的真实查询、响应结构及参数边界。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductPaginationTest {
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;

    /**
     * 验证过滤下架商品、稳定排序及跨页总数，超出末页仍返回空记录。
     * @throws Exception HTTP 测试失败
     */
    @Test
    void paginatesOnSaleProductsWithStableOrdering() throws Exception {
        products.deleteAll();
        var time = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        Product first = products.saveAndFlush(product("first", time));
        Product second = products.saveAndFlush(product("second", time));
        Product newest = products.saveAndFlush(product("newest", time.plusDays(1)));
        Product hidden = product("hidden", time.plusDays(2));
        hidden.setStatus(Product.Status.OFF_SALE);
        products.saveAndFlush(hidden);
        assertThat(second.getId()).isGreaterThan(first.getId());
        mvc.perform(get("/products").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.data.records[1].id").value(second.getId()))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        mvc.perform(get("/products").param("page", "2").param("size", "2"))
                .andExpect(jsonPath("$.data.records[0].id").value(first.getId()))
                .andExpect(jsonPath("$.data.records.length()").value(1));
        mvc.perform(get("/products").param("page", "3").param("size", "2"))
                .andExpect(jsonPath("$.data.records").isEmpty())
                .andExpect(jsonPath("$.data.total").value(3));
    }

    /**
     * 验证无参数时使用页码 1、每页 10 条，空列表总页数为 0。
     * @throws Exception HTTP 测试失败
     */
    @Test
    void defaultsAndEmptyPageFollowContract() throws Exception {
        products.deleteAll();
        mvc.perform(get("/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isEmpty())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    /**
     * 验证页码和页大小越界或类型错误返回 400。
     * @param query 分页查询参数
     * @throws Exception HTTP 测试失败
     */
    @ParameterizedTest
    @ValueSource(strings = {"page=0", "page=1000001", "size=0", "size=101", "page=abc"})
    void rejectsInvalidPagination(String query) throws Exception {
        mvc.perform(get("/products?" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    /**
     * 创建指定时间的可售商品。
     * @param name 商品名
     * @param time 创建时间
     * @return 未保存的商品
     */
    private Product product(String name, OffsetDateTime time) {
        Product product = new Product();
        product.setName(name);
        product.setMerchantId(7L);
        product.setPrice(new BigDecimal("1.00"));
        product.setStock(10);
        ReflectionTestUtils.setField(product, "createdAt", time);
        return product;
    }
}
