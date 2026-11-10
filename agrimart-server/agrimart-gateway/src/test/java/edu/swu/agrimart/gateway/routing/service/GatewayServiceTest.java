package edu.swu.agrimart.gateway.routing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/** 验证网关路由、身份校验及可信身份转发的测试。 */
class GatewayServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 创建使用测试服务地址和凭据的网关控制器。
     *
     * @param builder HTTP 客户端构建器
     * @return 测试所需的样例数据或配置对象
     */
    private GatewayService gateway(RestClient.Builder builder) {
        return new GatewayService(
                builder,
                MAPPER,
                "http://user.test",
                "http://trade.test",
                "http://payment.test",
                "service-secret",
                "gateway-secret");
    }

    /** 验证网关健康接口返回可用状态。 */
    @Test
    void healthBeanReturnsUp() {
        assertThat(gateway(RestClient.builder()).health()).containsEntry("status", "UP");
    }

    /**
     * 验证公开注册请求路由至用户服务并转发请求内容。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void publicRegisterRoutesToUserAndForwardsBodyQueryAndHeaders() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayService app = gateway(builder);
        MockHttpServletRequest request = request("POST", "/api/users/register", "invite=abc",
                "{\"username\":\"alice\"}");
        request.addHeader("X-Request-Id", "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        server.expect(requestTo("http://user.test/users/register?invite=abc"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Gateway-Token", "gateway-secret"))
                .andExpect(header("X-Request-Id", "req-1"))
                .andExpect(content().json("{\"username\":\"alice\"}"))
                .andRespond(withSuccess("{\"created\":true}", MediaType.APPLICATION_JSON));

        app.proxy(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"created\":true}");
        server.verify();
    }

    /**
     * 验证网关校验会话并使用可信身份覆盖客户端身份头。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void authenticatedGetIntrospectsTokenForwardsIdentityAndStripsClientIdentityHeaders()
            throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayService app = gateway(builder);
        MockHttpServletRequest request = request("GET", "/api/orders/42", "view=full", "");
        request.addHeader("Authorization", "Bearer client-token");
        request.addHeader("X-User-Id", "forged");
        request.addHeader("X-User-Role", "forged-role");
        MockHttpServletResponse response = new MockHttpServletResponse();
        server.expect(requestTo("http://user.test/internal/auth/introspections"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", "service-secret"))
                .andExpect(content().json("{\"accessToken\":\"client-token\"}"))
                .andRespond(
                        withSuccess(
                                "{\"data\":{\"userId\":\"u-7\",\"role\":\"BUYER\"}}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://trade.test/orders/42?view=full"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Gateway-Token", "gateway-secret"))
                .andExpect(header("X-User-Id", "u-7"))
                .andExpect(header("X-User-Role", "BUYER"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("order", MediaType.TEXT_PLAIN));

        app.proxy(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("order");
        server.verify();
    }

    /**
     * 验证订单支付路径优先路由到支付服务。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void orderPaymentRouteUsesPaymentService() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayService app = gateway(builder);
        MockHttpServletRequest request = request("GET", "/api/orders/42/payments", null, "");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        server.expect(requestTo("http://user.test/internal/auth/introspections"))
                .andRespond(
                        withSuccess(
                                "{\"data\":{\"userId\":\"u\",\"role\":\"R\"}}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://payment.test/orders/42/payments"))
                .andRespond(withSuccess());

        app.proxy(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        server.verify();
    }

    /** 验证缺少凭据时返回完整 401 错误响应。 */
    @Test
    void missingBearerReturns401() throws Exception {
        GatewayService app = gateway(RestClient.builder());
        MockHttpServletRequest request = request("GET", "/api/orders/42", null, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> app.proxy(request, response));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHENTICATED", "traceId");
    }

    /** 验证认证服务不可用时返回 503。 */
    @Test
    void introspectionOutageReturns503() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayService app = gateway(builder);
        MockHttpServletRequest request = request("GET", "/api/orders/42", null, "");
        request.addHeader("Authorization", "Bearer bad");
        server.expect(requestTo("http://user.test/internal/auth/introspections"))
                .andRespond(withServerError());

        MockHttpServletResponse response = new MockHttpServletResponse();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> app.proxy(request, response));
        assertThat(response.getStatus()).isEqualTo(503);
        server.verify();
    }

    /**
     * 验证网关拒绝公开访问内部服务路径。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void internalRouteIsRejectedWithoutCallingDependency() throws Exception {
        GatewayService app = gateway(RestClient.builder());
        MockHttpServletResponse response = new MockHttpServletResponse();
        app.proxy(request("GET", "/api/internal/health", null, ""), response);
        assertThat(response.getStatus()).isEqualTo(404);
    }

    /**
     * 验证下游 HTTP 错误保留原状态。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void downstreamErrorsPreserveStatus() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayService app = gateway(builder);
        server.expect(requestTo("http://user.test/users/register")).andRespond(withServerError());

        MockHttpServletResponse response = new MockHttpServletResponse();
        app.proxy(request("POST", "/api/users/register", null, "{}"), response);
        assertThat(response.getStatus()).isEqualTo(500);
        server.verify();
    }

    /**
     * 验证带上下文路径的受保护请求仍按业务正确路由。
     *
     * @param path   当前请求路径
     * @param target 预期的目标服务地址
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "/api/payments/P1, http://payment.test/payments/P1",
            "/api/payment-callbacks/provider, http://payment.test/payment-callbacks/provider",
            "/api/users/me, http://user.test/users/me",
            "/api/merchant-applications, http://user.test/merchant-applications",
            "/api/admin/merchant-applications, http://user.test/admin/merchant-applications",
            "/api/checkouts, http://trade.test/checkouts"
    })
    void routesAuthenticatedRequestsWithContextPath(String path, String target) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayService app = gateway(builder);
        MockHttpServletRequest request = request("GET", "/app" + path, null, "");
        request.setContextPath("/app");
        request.addHeader("Authorization", "Bearer token");
        request.addHeader("X-Gateway-Token", "forged");
        server.expect(requestTo("http://user.test/internal/auth/introspections"))
                .andRespond(
                        withSuccess(
                                "{\"data\":{\"userId\":7,\"role\":\"FARMER\"}}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo(target))
                .andExpect(header("X-Gateway-Token", "gateway-secret"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NO_CONTENT));
        MockHttpServletResponse response = new MockHttpServletResponse();
        app.proxy(request, response);
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentAsByteArray()).isEmpty();
        server.verify();
    }

    /**
     * 验证游客操作不触发会话校验，并且不能伪造内部调用身份。
     * 
     * @param method HTTP 方法
     * @param path   网关请求路径
     * @param target 预期下游地址
     * @throws Exception 请求转发失败
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "GET, /api/products, http://trade.test/products",
            "GET, /api/products/7, http://trade.test/products/7",
            "GET, /api/products/merchant/9, http://trade.test/products/merchant/9",
            "GET, /api/reviews/product/7, http://trade.test/reviews/product/7",
            "POST, /api/payment-callbacks/sandbox/payments, http://payment.test/payment-callbacks/sandbox/payments",
            "POST, /api/payment-callbacks/sandbox/refunds, http://payment.test/payment-callbacks/sandbox/refunds"
    })
    void publicOperationsBypassLoginAndStripUntrustedIdentity(String method, String path, String target)
            throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MockHttpServletRequest request = request(method, path, "size=2", "raw=callback%2Bsignature");
        request.setContentType("application/x-www-form-urlencoded");
        request.addHeader("X-Service-Token", "forged-service");
        request.addHeader("X-User-Id", "forged-user");
        request.addHeader("X-User-Role", "ADMIN");
        server.expect(requestTo(target + "?size=2"))
                .andExpect(headerDoesNotExist("X-Service-Token"))
                .andExpect(headerDoesNotExist("X-User-Id"))
                .andExpect(headerDoesNotExist("X-User-Role"))
                .andExpect(header("Content-Type", "application/x-www-form-urlencoded"))
                .andExpect(content().string("raw=callback%2Bsignature"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        MockHttpServletResponse response = new MockHttpServletResponse();
        gateway(builder).proxy(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        server.verify();
    }

    /**
     * 验证游客放行不会扩展到写操作或其他同前缀路径。
     * 
     * @param method HTTP 方法
     * @param path   网关请求路径
     * @throws Exception 请求处理失败
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "POST, /api/products", "DELETE, /api/products/7", "GET, /api/products/mine",
            "GET, /api/payment-callbacks/sandbox/payments", "POST, /api/payment-callbacks/sandbox/anything",
            "GET, /api/users/login", "GET, /api/orders/7"
    })
    void publicWhitelistDoesNotAllowOtherOperations(String method, String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        gateway(RestClient.builder()).proxy(request(method, path, null, ""), response);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    /**
     * 验证下游的 403、404 和 409 及其业务错误体不会被网关改写。
     * 
     * @param status 下游 HTTP 状态
     * @throws Exception 请求处理失败
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = { 403, 404, 409 })
    void forwardsDownstreamBusinessErrors(int status) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://user.test/users/login"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorCode\":\"BUSINESS_ERROR\"}"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        gateway(builder).proxy(request("POST", "/api/users/login", null, "{}"), response);
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentAsString()).contains("BUSINESS_ERROR");
        server.verify();
    }

    /**
     * 验证用户服务继续收到认证后的原始 Bearer 令牌。
     * 
     * @throws Exception 请求处理失败
     */
    @Test
    void userProfileRetainsValidatedBearerToken() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://user.test/internal/auth/introspections"))
                .andRespond(withSuccess("{\"data\":{\"userId\":7,\"role\":\"FARMER\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://user.test/users/me"))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        var request = request("GET", "/api/users/me", null, "");
        request.addHeader("Authorization", "Bearer access-token");
        var response = new MockHttpServletResponse();
        gateway(builder).proxy(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        server.verify();
    }

    /**
     * 验证认证服务连接失败属于依赖错误，而非用户凭据无效。
     * 
     * @throws Exception 请求处理失败
     */
    @Test
    void authenticationConnectionFailureReturns503() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://user.test/internal/auth/introspections"))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("timeout");
                });
        var request = request("GET", "/api/orders/7", null, "");
        request.addHeader("Authorization", "Bearer access-token");
        var response = new MockHttpServletResponse();
        gateway(builder).proxy(request, response);
        assertThat(response.getStatus()).isEqualTo(503);
        server.verify();
    }

    /**
     * 构造测试使用的HTTP 请求。
     *
     * @param method 测试输入或业务依赖
     * @param uri    测试输入或业务依赖
     * @param query  测试输入或业务依赖
     * @param body   请求业务数据
     * @return 测试所需的样例数据或配置对象
     */
    private static MockHttpServletRequest request(
            String method, String uri, String query, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setQueryString(query);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        return request;
    }
}
