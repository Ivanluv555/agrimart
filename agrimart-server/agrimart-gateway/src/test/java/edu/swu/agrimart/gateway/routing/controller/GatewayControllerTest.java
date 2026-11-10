package edu.swu.agrimart.gateway.routing.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 验证网关默认配置、服务依赖注入及实际 HTTP 健康接口能够正常启动。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayControllerTest {
    @Autowired
    Environment environment;

    /** 验证无需设置额外环境变量即可启动网关并返回健康响应。 */
    @Test
    void defaultConfigurationStartsAndServesHealth() {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        Map<?, ?> body = RestClient.create()
                .get()
                .uri("http://localhost:" + port + "/actuator/health")
                .retrieve()
                .body(Map.class);
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("UP");
    }
}
