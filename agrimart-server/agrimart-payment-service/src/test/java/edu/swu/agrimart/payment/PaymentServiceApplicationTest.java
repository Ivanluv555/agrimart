package edu.swu.agrimart.payment;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

/** 验证支付服务的应用启动入口的测试。 */
class PaymentServiceApplicationTest {
    /** 验证服务启动入口委托给 Spring Boot。 */
    @Test
    void mainDelegatesToSpringBoot() {
        try (var mocked = mockStatic(SpringApplication.class)) {
            String[] args = {"--test"};
            PaymentServiceApplication.main(args);
            mocked.verify(() -> SpringApplication.run(PaymentServiceApplication.class, args));
        }
    }
}
