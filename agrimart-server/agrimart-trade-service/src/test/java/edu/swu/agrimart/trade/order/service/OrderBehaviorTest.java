package edu.swu.agrimart.trade.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.swu.agrimart.trade.order.dto.CreateOrder;
import edu.swu.agrimart.trade.order.dto.Item;
import edu.swu.agrimart.trade.order.entity.TradeOrder;
import edu.swu.agrimart.trade.order.repository.OrderRepository;
import edu.swu.agrimart.trade.product.entity.Product;
import edu.swu.agrimart.trade.product.repository.ProductRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

/** 验证结算金额、库存与单商家限制的测试。 */
class OrderBehaviorTest {
    /** 验证结算按服务端价格计算金额、扣减库存并保存订单。 */
    @Test
    void checkoutDeductsInventoryAndPersistsCalculatedOrder() {
        ProductRepository products = mock(ProductRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        Product p = new Product();
        p.setName("Rice");
        p.setPrice(new BigDecimal("2.50"));
        p.setStock(5);
        p.setMerchantId(8L);
        when(products.findById(1L)).thenReturn(Optional.of(p));
        when(orders.save(any())).thenAnswer(i -> i.getArgument(0));
        OrderService logic = new OrderService(products, orders, mock(edu.swu.agrimart.common.messaging.service.MessageStore.class), mock(OrderFundsService.class));
        TradeOrder result = logic.order(3L, new CreateOrder(List.of(new Item(1, 2))));
        assertThat(p.getStock()).isEqualTo(3);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("5.00");
        assertThat(result.getBuyerId()).isEqualTo(3L);
    }

    /** 验证结算拒绝缺失商品、库存不足和跨商家商品。 */
    @Test
    void checkoutRejectsMissingInsufficientAndMixedMerchantProducts() {
        ProductRepository products = mock(ProductRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        OrderService logic = new OrderService(products, orders, mock(edu.swu.agrimart.common.messaging.service.MessageStore.class), mock(OrderFundsService.class));
        assertThatThrownBy(() -> logic.order(1L, new CreateOrder(List.of(new Item(9, 1)))))
                .isInstanceOf(NoSuchElementException.class);
        Product shortStock = product(1L, 2);
        when(products.findById(1L)).thenReturn(Optional.of(shortStock));
        assertThatThrownBy(() -> logic.order(1L, new CreateOrder(List.of(new Item(1, 3)))))
                .hasMessage("STOCK_INSUFFICIENT");
        Product other = product(2L, 5);
        other.setMerchantId(99L);
        when(products.findById(2L)).thenReturn(Optional.of(other));
        assertThatThrownBy(
                        () ->
                                logic.order(
                                        1L,
                                        new CreateOrder(List.of(new Item(1, 1), new Item(2, 1)))))
                .hasMessage("MULTI_MERCHANT_NOT_SUPPORTED");
    }

    /**
     * 构造测试使用的商品。
     *
     * @param merchant 当前商家标识
     * @param stock 可用库存
     * @return 测试所需的样例数据或配置对象
     */
    private Product product(Long merchant, int stock) {
        Product p = new Product();
        p.setName("P");
        p.setPrice(BigDecimal.ONE);
        p.setStock(stock);
        p.setMerchantId(merchant);
        return p;
    }
}
