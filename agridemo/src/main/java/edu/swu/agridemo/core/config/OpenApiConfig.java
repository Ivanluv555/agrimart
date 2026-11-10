package edu.swu.agridemo.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("农惠达 API")
                        .version("dev")
                        .description("请输入文本")
                        .contact(new Contact()
                                .name("Ivan Horn")
                                .email("ivanluv555@outlook.com")));
    }
}
