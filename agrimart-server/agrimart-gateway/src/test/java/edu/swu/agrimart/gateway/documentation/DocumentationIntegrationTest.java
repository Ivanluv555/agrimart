package edu.swu.agrimart.gateway.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** 验证 Swagger 页面、文档代理及调试地址能通过实际 HTTP 链路协同工作。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentationIntegrationTest {
    private static HttpServer upstream;
    private static final AtomicReference<String> forwardedPath = new AtomicReference<>();
    @Autowired
    Environment environment;

    /**
     * 启动仅供测试使用的业务文档和注册接口。
     * @param registry 测试属性注册器
     * @throws IOException 测试 HTTP 服务启动失败
     */
    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/v3/api-docs", exchange -> {
            byte[] body = """
                    {"openapi":"3.1.0","info":{"title":"用户服务","version":"v1"},
                     "servers":[{"url":"http://private-service:8081"}],
                     "paths":{"/users/register":{"post":{"responses":{"200":{"description":"OK"}}}},
                              "/users/login":{"post":{"responses":{"200":{"description":"OK"}}}},
                              "/users/me":{"get":{"responses":{"200":{"description":"OK"}}}},
                              "/internal/auth/introspections":{"post":{}}},
                     "components":{"schemas":{"Register":{"type":"object"}}}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.createContext("/users/register", exchange -> {
            forwardedPath.set(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"registered\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
        registry.add("agrimart.routes.user", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    /** 释放测试 HTTP 服务。 */
    @AfterAll
    static void stopUpstream() {
        upstream.stop(0);
    }

    /** 验证 UI 页面及配置同时暴露三个服务文档入口。 */
    @Test
    void servesUiAndThreeServiceChoices() {
        String html = client().get().uri("/swagger-ui/index.html").retrieve().body(String.class);
        assertThat(html).contains("Swagger UI");
        String config = client().get().uri("/v3/api-docs/swagger-config").retrieve().body(String.class);
        assertThat(config).contains("/docs/user", "/docs/trade", "/docs/payment");
        String initializer = client().get().uri("/swagger-ui/swagger-initializer.js")
                .retrieve().body(String.class);
        assertThat(initializer).contains("/v3/api-docs/swagger-config");
    }

    /** 验证文档保留模型、移除内部接口，并正确声明网关地址及认证。 */
    @Test
    void adaptsServiceDocumentAndTryItOutUsesGateway() {
        Map<?, ?> document = client().get().uri("/docs/user").retrieve().body(Map.class);
        assertThat(document).isNotNull();
        assertThat(document.get("servers")).isEqualTo(List.of(Map.of("url", "/api")));
        Map<?, ?> paths = (Map<?, ?>) document.get("paths");
        assertThat(paths.containsKey("/internal/auth/introspections")).isFalse();
        assertThat(paths.containsKey("/users/me")).isTrue();
        Map<?, ?> register = (Map<?, ?>) ((Map<?, ?>) paths.get("/users/register")).get("post");
        assertThat(register.get("security")).isEqualTo(List.of());
        assertThat(document.get("security")).isEqualTo(List.of(Map.of("bearerAuth", List.of())));
        Map<?, ?> components = (Map<?, ?>) document.get("components");
        assertThat(((Map<?, ?>) components.get("schemas")).containsKey("Register")).isTrue();
        Map<?, ?> scheme = (Map<?, ?>) ((Map<?, ?>) components.get("securitySchemes")).get("bearerAuth");
        assertThat(scheme.get("scheme")).isEqualTo("bearer");

        String apiBase = (String) ((Map<?, ?>) ((List<?>) document.get("servers")).getFirst()).get("url");
        String result = client().post().uri(apiBase + "/users/register")
                .contentType(MediaType.APPLICATION_JSON).body("{}")
                .retrieve().body(String.class);
        assertThat(result).contains("registered");
        assertThat(forwardedPath.get()).isEqualTo("/users/register");
    }

    /** 验证未知服务不会被当成任意代理目标。 */
    @Test
    void unknownServiceReturnsNotFound() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client().get().uri("/docs/unknown").retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    /**
     * 创建访问随机网关端口的客户端。
     * @return 测试 HTTP 客户端
     */
    private RestClient client() {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        return RestClient.create("http://localhost:" + port);
    }
}
