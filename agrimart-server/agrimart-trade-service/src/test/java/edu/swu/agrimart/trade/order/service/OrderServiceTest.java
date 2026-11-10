package edu.swu.agrimart.trade.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.swu.agrimart.common.payment.dto.PaymentAuthorization;
import edu.swu.agrimart.common.payment.dto.PaymentEvent;
import edu.swu.agrimart.trade.order.dto.CreateOrder;
import edu.swu.agrimart.trade.order.dto.Item;
import edu.swu.agrimart.trade.order.entity.TradeOrder;
import edu.swu.agrimart.trade.order.repository.OrderRepository;
import edu.swu.agrimart.trade.product.entity.Product;
import edu.swu.agrimart.trade.product.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;

/** 验证处理单商家结算、库存扣减、订单取消及支付事件的测试。 */
class OrderServiceTest {
    ProductRepository products;
    OrderRepository orders;
    OrderService logic;

    /** 初始化当前测试所需的替身、校验器或样例数据。 */
    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        orders = mock(OrderRepository.class);
        logic = new OrderService(products, orders, mock(edu.swu.agrimart.common.messaging.service.MessageStore.class), mock(OrderFundsService.class));
    }

    /** 验证重复商品项合并并允许恰好耗尽库存。 */
    @Test
    void mergesRepeatedItemsAndAllowsExactStockBoundary() {
        Product product = new Product();
        product.setStock(5);
        product.setMerchantId(7L);
        product.setPrice(new BigDecimal("2.35"));
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
        TradeOrder result =
                logic.order(8L, new CreateOrder(List.of(new Item(1, 2), new Item(1, 3))));
        assertThat(product.getStock()).isZero();
        assertThat(result.getTotalAmount()).isEqualByComparingTo("11.75");
        assertThat(result.getMerchantId()).isEqualTo(7L);
        assertThat(result.getBuyerId()).isEqualTo(8L);
        assertThat(result.getStatus()).isEqualTo(TradeOrder.Status.PENDING_PAYMENT);
        assertThat(result.getOrderNo()).matches("O[0-9a-f]{32}");
        verify(products).findById(1L);
        verify(products).save(product);
        verify(orders).save(result);
    }

    /**
     * 验证不可售商品不会触发库存或订单写入。
     *
     * @param status 业务状态
     */
    @ParameterizedTest
    @EnumSource(
            value = Product.Status.class,
            names = {"OFF_SALE", "OUT_OF_STOCK"})
    void rejectsUnavailableProductWithoutSaving(Product.Status status) {
        Product product = new Product();
        product.setStatus(status);
        product.setStock(10);
        when(products.findById(1L)).thenReturn(Optional.of(product));
        assertThatThrownBy(() -> logic.order(8L, new CreateOrder(List.of(new Item(1, 1)))))
                .hasMessage("STOCK_INSUFFICIENT");
        verify(products, never()).save(any());
        verifyNoInteractions(orders);
    }

    /** 验证订单查询返回记录或报告资源不存在。 */
    @Test
    void getReturnsOrderOrReportsMissingResource() {
        TradeOrder order = new TradeOrder();
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        assertThat(logic.get(1L)).isSameAs(order);
        assertThatThrownBy(() -> logic.get(2L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("RESOURCE_NOT_FOUND");
    }

    /** 验证买家只能取消本人待支付订单。 */
    @Test
    void buyerCanCancelPendingOrderButNotAnotherBuyersOrder() {
        TradeOrder order = new TradeOrder();
        order.setBuyerId(8L);
        when(orders.lock(1L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> logic.cancel(9L, 1L)).hasMessage("RESOURCE_NOT_FOUND");
        assertThat(order.getStatus()).isEqualTo(TradeOrder.Status.PENDING_PAYMENT);
        assertThat(logic.cancel(8L, 1L)).isSameAs(order);
        assertThat(order.getStatus()).isEqualTo(TradeOrder.Status.CANCELLED);
    }

    /**
     * 验证非待支付订单拒绝取消。
     *
     * @param status 业务状态
     */
    @ParameterizedTest
    @EnumSource(
            value = TradeOrder.Status.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "PENDING_PAYMENT")
    void cannotCancelNonPendingOrder(TradeOrder.Status status) {
        TradeOrder order = new TradeOrder();
        order.setBuyerId(8L);
        order.setStatus(status);
        when(orders.lock(1L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> logic.cancel(8L, 1L)).hasMessage("STATE_CONFLICT");
        assertThat(order.getStatus()).isEqualTo(status);
    }

    /** 验证支付授权包含订单归属、支付号、金额与币种。 */
    @Test
    void authorizationCarriesOrderPaymentAndCurrency() {
        when(orders.lock(1L)).thenReturn(Optional.of(order()));
        PaymentAuthorization result =
                (PaymentAuthorization) logic.authorize(1L, Map.of("paymentNo", "P-1", "buyerId",8L));
        assertThat(result.authorizationNo()).startsWith("A");
        assertThat(result.paymentNo()).isEqualTo("P-1");
        assertThat(result.orderId()).isEqualTo(1L);
        assertThat(result.buyerId()).isEqualTo(8L);
        assertThat(result.merchantId()).isEqualTo(7L);
        assertThat(result.amount()).isEqualTo("12.30");
        assertThat(result.currency()).isEqualTo("CNY");
        assertThat(result.expiresAt()).isAfter(java.time.OffsetDateTime.now());
    }

    /** 验证非待支付订单拒绝签发支付授权。 */
    @Test
    void rejectsNonPendingAuthorization() {
        TradeOrder order = order();
        order.setStatus(TradeOrder.Status.PAID);
        when(orders.lock(1L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> logic.authorize(1L, Map.of("paymentNo","P-1","buyerId",8L))).hasMessage("STATE_CONFLICT");
    }

    /**
     * 验证支付事件只处理成功与关闭两种已支持状态。
     *
     * @param status 业务状态
     */
    @ParameterizedTest
    @ValueSource(strings = {"SUCCEEDED", "CLOSED", "PENDING"})
    void eventsApplyOnlyRecognizedStatuses(String status) {
        logic.event(1L, event(status));
        verify(logic.funds).accept(argThat(e -> e.status().equals(status) && e.orderId().equals(1L)));

    }

    /**
     * 构造测试订单及其归属和金额。
     *
     * @return 测试所需的样例数据或配置对象
     */
    private TradeOrder order() {
        TradeOrder order = new TradeOrder();
        order.setBuyerId(8L);
        order.setMerchantId(7L);
        order.setTotalAmount(new BigDecimal("12.30"));
        return order;
    }

    /**
     * 构造指定状态的测试支付事件。
     *
     * @param status 业务状态
     * @return 测试所需的样例数据或配置对象
     */
    private PaymentEvent event(String status) {
        return new PaymentEvent("E1", "P1", 1L, 1, status, "12.30", "CNY", "provider-1", java.time.OffsetDateTime.now());
    }
}
