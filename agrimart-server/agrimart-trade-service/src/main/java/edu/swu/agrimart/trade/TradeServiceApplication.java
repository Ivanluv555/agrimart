package edu.swu.agrimart.trade;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 交易服务的应用启动入口。 */
@org.springframework.context.annotation.Import({
    edu.swu.agrimart.common.messaging.config.MessageStoreConfiguration.class,
    edu.swu.agrimart.common.messaging.config.RabbitMessagingConfiguration.class})
@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "AgriMart 交易服务", version = "v1"))
public class TradeServiceApplication {
    /**
     * 启动当前 Spring Boot 应用。
     *
     * @param a 应用启动参数
     */
    public static void main(String[] a) {
        SpringApplication.run(TradeServiceApplication.class, a);
    }
}
