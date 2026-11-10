package edu.swu.agrimart.user.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 验证实际 HTTP 文档端点能生成本服务接口及请求模型。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiDocumentTest {
    @Autowired
    Environment environment;

    /** 验证 OpenAPI 自动配置、控制器扫描及模型生成完整接通。 */
    @Test
    void exposesServiceDocumentAndSchemas() {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        Map<?, ?> document = RestClient.create().get()
                .uri("http://localhost:" + port + "/v3/api-docs")
                .retrieve().body(Map.class);
        assertThat(document).isNotNull();
        assertThat(document.get("openapi").toString()).startsWith("3.");
        assertThat(((Map<?, ?>) document.get("info")).get("title")).isEqualTo("AgriMart 用户服务");
        assertThat(((Map<?, ?>) document.get("paths")).containsKey("/users/login")).isTrue();
        Map<?, ?> components = (Map<?, ?>) document.get("components");
        assertThat((Map<?, ?>) components.get("schemas")).isNotEmpty();
    }
}
