package edu.swu.agrimart.trade.order.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.swu.agrimart.common.auth.service.RequestAuth;
import edu.swu.agrimart.common.payment.dto.PaymentAuthorization;
import edu.swu.agrimart.common.payment.dto.PaymentEvent;
import edu.swu.agrimart.trade.order.dto.CreateOrder;
import edu.swu.agrimart.trade.order.dto.Item;
import edu.swu.agrimart.trade.order.entity.TradeOrder;
import edu.swu.agrimart.trade.order.service.OrderService;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.*;

/** 验证提供结算、本人订单及内部支付协作接口的测试。 */
class OrderControllerTest {
    OrderService logic;
    OrderController controller;
    MockHttpServletRequest request;

    /** 初始化当前测试所需的替身、校验器或样例数据。 */
    @BeforeEach
    void setUp() {
        logic = mock(OrderService.class);
        controller = new OrderController(logic, new RequestAuth("gateway"), "service");
        request = new MockHttpServletRequest();
        request.addHeader("X-Gateway-Token", "gateway");
        request.addHeader("X-User-Id", "8");
        request.addHeader("X-Service-Token", "service");
    }

    /** 验证结算、本人订单查询及取消均委托给订单服务。 */
    @Test
    void delegatesOrderEndpoints() {
        TradeOrder order = order();
        var checkout = new CreateOrder(List.of(new Item(1, 2)));
        when(logic.order(8L, checkout)).thenReturn(order);
        when(logic.get(1L)).thenReturn(order);
        when(logic.cancel(8L, 1L)).thenReturn(order);
        assertThat(controller.order(checkout, request).data()).isSameAs(order);
        assertThat(controller.get(1L, request).data()).isSameAs(order);
        assertThat(controller.cancel(1L, request).data()).isSameAs(order);
        verify(logic).order(8L, checkout);
        verify(logic).cancel(8L, 1L);
    }

    /** 验证缺失可信身份时不会创建订单。 */
    @Test
    void rejectsMissingIdentityBeforeCreatingOrder() {
        request.removeHeader("X-User-Id");
        assertThatThrownBy(() -> controller.order(new CreateOrder(List.of()), request))
                .isInstanceOf(SecurityException.class)
                .hasMessage("UNAUTHENTICATED");
        verifyNoInteractions(logic);
    }

    /** 验证本人订单接口隐藏其他买家的订单。 */
    @Test
    void hidesAnotherBuyersOrder() {
        TradeOrder order = order();
        order.setBuyerId(9L);
        when(logic.get(1L)).thenReturn(order);
        assertThatThrownBy(() -> controller.get(1L, request)).hasMessage("RESOURCE_NOT_FOUND");
    }

    /** 验证内部订单接口在查询前拒绝错误服务令牌。 */
    @Test
    void internalEndpointsRejectWrongServiceTokenBeforeLookup() {
        request.removeHeader("X-Service-Token");
        assertThatThrownBy(() -> controller.authorize(1L, Map.of(), request))
                .hasMessage("UNAUTHENTICATED");
        assertThatThrownBy(() -> controller.event(1L, event("SUCCEEDED"), request))
                .hasMessage("UNAUTHENTICATED");
        verifyNoInteractions(logic);
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
        return new PaymentEvent("E1", "P1", 1L, 1, status, "12.30", "CNY", null, null);
    }

    /** 验证内部接口将支付授权和支付事件委托给订单服务。 */
    @Test
    void delegatesAuthorizationAndEventsToOrderService() {
        var authorization =
                new PaymentAuthorization(
                        "A1", "P-1", 1L, 8L, 7L, "12.30", "CNY", java.time.OffsetDateTime.now());
        when(logic.authorize(1L, Map.of("paymentNo", "P-1"))).thenReturn(authorization);
        assertThat(controller.authorize(1L, Map.of("paymentNo", "P-1"), request).data())
                .isSameAs(authorization);
        var event = event("SUCCEEDED");
        assertThat(controller.event(1L, event, request).data()).isNull();
        verify(logic).event(1L, event);
    }
}
