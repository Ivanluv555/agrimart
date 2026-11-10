package edu.swu.agrimart.trade.support.config;

import edu.swu.agrimart.common.auth.service.RequestAuth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

/** 交易业务所需的网关身份解析配置。 */
@Configuration
public class IdentityConfig {
    /**
     * 创建供商品和订单共用的可信身份解析器。
     *
     * @param token 访问令牌或待验证的调用凭据
     * @return 网关身份解析器
     */
    @Bean
    RequestAuth requestAuth(
            @Value("${agrimart.gateway-token:dev-gateway-token}") String token) {
        return new RequestAuth(token);
    }
}
