package edu.swu.agrimart.user;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 用户服务的应用启动入口。 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "AgriMart 用户服务", version = "v1"))
public class UserServiceApplication {
    /**
     * 启动当前 Spring Boot 应用。
     *
     * @param args 应用启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
