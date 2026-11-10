package edu.swu.agrimart.trade;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

/** 验证交易服务的应用启动入口的测试。 */
class TradeServiceApplicationTest {
    /** 验证服务启动入口委托给 Spring Boot。 */
    @Test
    void mainDelegatesToSpringBoot() {
        try (var mocked = mockStatic(SpringApplication.class)) {
            String[] args = {"--test"};
            TradeServiceApplication.main(args);
            mocked.verify(() -> SpringApplication.run(TradeServiceApplication.class, args));
        }
    }
}
