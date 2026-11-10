package edu.swu.agrimart.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 统一网关的应用启动入口。 */
@SpringBootApplication
public class GatewayApplication {
    /**
     * 启动当前 Spring Boot 应用。
     *
     * @param args 应用启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
