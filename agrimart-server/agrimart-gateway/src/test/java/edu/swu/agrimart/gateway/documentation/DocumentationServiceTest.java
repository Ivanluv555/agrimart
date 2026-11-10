package edu.swu.agrimart.gateway.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import edu.swu.agrimart.gateway.documentation.service.DocumentationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** 验证文档路由及依赖不可用时的响应，不依赖实际业务服务。 */
class DocumentationServiceTest {
    /**
     * 验证三个服务使用各自的文档地址，并支持网关上下文前缀。
     * @param service 服务标识
     */
    @ParameterizedTest
    @ValueSource(strings = {"user", "trade", "payment"})
    void routesEachDocumentToItsService(String service) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer upstream = MockRestServiceServer.bindTo(builder).build();
        upstream.expect(requestTo("http://" + service + ".test/v3/api-docs"))
                .andRespond(withSuccess("{\"openapi\":\"3.1.0\",\"paths\":{}}", MediaType.APPLICATION_JSON));
        var document = service(builder).document(service, "/agrimart/api");
        assertThat(document.get("servers")).isEqualTo(java.util.List.of(java.util.Map.of("url", "/agrimart/api")));
        upstream.verify();
    }

    /** 验证单个业务服务不可用时返回明确的 503。 */
    @Test
    void unavailableServiceReturns503() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer upstream = MockRestServiceServer.bindTo(builder).build();
        upstream.expect(requestTo("http://user.test/v3/api-docs")).andRespond(withServerError());
        assertThatThrownBy(() -> service(builder).document("user", "/api"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        upstream.verify();
    }

    /** 验证缺少路径对象的响应不会被作为正常文档返回。 */
    @Test
    void invalidDocumentReturns502() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer upstream = MockRestServiceServer.bindTo(builder).build();
        upstream.expect(requestTo("http://user.test/v3/api-docs"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service(builder).document("user", "/api"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        upstream.verify();
    }

    /**
     * 创建使用固定测试路由的文档服务。
     * @param builder 绑定模拟上游的客户端构建器
     * @return 文档服务
     */
    private DocumentationService service(RestClient.Builder builder) {
        return new DocumentationService(builder, "http://user.test", "http://trade.test", "http://payment.test");
    }
}
