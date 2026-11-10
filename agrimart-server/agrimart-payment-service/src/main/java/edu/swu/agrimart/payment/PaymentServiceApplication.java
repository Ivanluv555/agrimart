package edu.swu.agrimart.payment;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 支付服务的应用启动入口。 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "AgriMart 支付服务", version = "v1"))
public class PaymentServiceApplication {
    /**
     * 启动当前 Spring Boot 应用。
     *
     * @param a 应用启动参数
     */
    public static void main(String[] a) {
        SpringApplication.run(PaymentServiceApplication.class, a);
    }
}
