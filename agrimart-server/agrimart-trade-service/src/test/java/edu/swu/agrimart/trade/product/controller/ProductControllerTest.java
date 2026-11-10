package edu.swu.agrimart.trade.product.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.swu.agrimart.common.auth.service.RequestAuth;
import edu.swu.agrimart.trade.product.dto.CreateProduct;
import edu.swu.agrimart.trade.product.entity.Product;
import edu.swu.agrimart.trade.product.service.ProductService;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

/** 验证提供商品公开列表及发布接口的测试。 */
class ProductControllerTest {

    /** 验证商品列表公开可读且商品发布使用可信用户身份。 */
    @Test
    void delegatesPublicListingAndAuthenticatedCreation() {
        ProductService products = mock(ProductService.class);
        var controller = new ProductController(products, new RequestAuth("gateway"));
        var request = new MockHttpServletRequest();
        request.addHeader("X-Gateway-Token", "gateway");
        request.addHeader("X-User-Id", "8");
        var body = new CreateProduct("Rice", "2.00", 5);
        var product = new Product();
        var page = new edu.swu.agrimart.common.api.dto.PageResult<>(List.of(product), 1, 1, 10, 1);
        when(products.list(1, 10)).thenReturn(page);
        when(products.create(8L, body)).thenReturn(product);
        assertThat(controller.products(1, 10).data()).isEqualTo(page);
        assertThat(controller.create(body, request).data()).isSameAs(product);
    }

    /** 验证商品发布在访问业务服务前拒绝缺失或错误网关身份。 */
    @Test
    void rejectsMissingOrIncorrectRequestAuthBeforeCreatingProduct() {
        var products = mock(ProductService.class);
        var controller = new ProductController(products, new RequestAuth("gateway"));
        var request = new MockHttpServletRequest();
        var body = new CreateProduct("Rice", "2.00", 5);
        assertThatThrownBy(() -> controller.create(body, request)).hasMessage("UNAUTHENTICATED");
        request.addHeader("X-Gateway-Token", "gateway");
        assertThatThrownBy(() -> controller.create(body, request)).hasMessage("UNAUTHENTICATED");
        verifyNoInteractions(products);
    }
}
