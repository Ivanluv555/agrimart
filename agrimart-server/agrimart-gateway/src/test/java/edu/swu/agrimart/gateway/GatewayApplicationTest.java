package edu.swu.agrimart.gateway;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

/** 验证统一网关的应用启动入口的测试。 */
class GatewayApplicationTest {
    /** 验证启动入口将参数交给 Spring Boot。 */
    @Test
    void mainDelegatesArgumentsToSpringBoot() {
        try (var spring = mockStatic(SpringApplication.class)) {
            String[] args = { "--server.port=0" };
            GatewayApplication.main(args);
            spring.verify(() -> SpringApplication.run(GatewayApplication.class, args));
        }
    }
}
