package edu.swu.agrimart.trade.order.dto;

import static org.assertj.core.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.*;

import java.util.List;

/** 验证OrderValidation的测试。 */
class OrderValidationTest {
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

    /** 验证商品标识与购买数量必须为正值。 */
    @Test
    void itemRequiresPositiveIdentityAndQuantity() {
        assertThat(validator.validate(new Item(1, 1))).isEmpty();
        assertThat(validator.validate(new Item(0, -1)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("productId", "quantity");
    }

    /** 验证结算请求必须包含商品项。 */
    @Test
    void orderRequiresNonemptyItems() {
        assertThat(validator.validate(new CreateOrder(null))).hasSize(1);
        assertThat(validator.validate(new CreateOrder(List.of()))).hasSize(1);
        var items = List.of(new Item(1, 1));
        var order = new CreateOrder(items);
        assertThat(order.items()).isEqualTo(items);
        assertThat(validator.validate(order)).isEmpty();
    }

    /** 记录当前订单项未开启级联校验的既有行为。 */
    @Test
    void documentsMissingCascadeValidationForOrderItems() {
        // Current production DTO lacks @Valid on the items collection.
        assertThat(validator.validate(new CreateOrder(List.of(new Item(0, 0))))).isEmpty();
    }
}
