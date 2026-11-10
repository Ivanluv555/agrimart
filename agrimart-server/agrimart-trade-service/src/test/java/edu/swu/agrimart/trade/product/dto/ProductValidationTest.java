package edu.swu.agrimart.trade.product.dto;

import static org.assertj.core.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.*;

/** 验证ProductValidation的测试。 */
class ProductValidationTest {
    static ValidatorFactory factory;
    static Validator validator;

    /** 创建测试使用的 Bean Validation 校验器。 */
    @BeforeAll
    static void open() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /** 释放测试校验器资源。 */
    @AfterAll
    static void close() {
        factory.close();
    }

    /** 验证商品初始库存允许既定上下界。 */
    @Test
    void productAllowsStockBoundaries() {
        assertThat(validator.validate(new CreateProduct("Rice", "1.00", 0))).isEmpty();
        assertThat(validator.validate(new CreateProduct("Rice", "1.00", 1000000))).isEmpty();
    }

    /** 验证商品请求拒绝空白字段和超界库存。 */
    @Test
    void productRejectsBlankFieldsAndOutOfRangeStock() {
        assertThat(validator.validate(new CreateProduct(" ", "", -1)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("name", "price", "stock");
        assertThat(validator.validate(new CreateProduct("Rice", "1", 1000001)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("stock");
    }
}
