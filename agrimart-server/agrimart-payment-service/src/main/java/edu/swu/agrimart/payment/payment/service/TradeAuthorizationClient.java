package edu.swu.agrimart.payment.payment.service;

import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.common.payment.dto.PaymentAuthorization;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.Map;

/** 支付只通过 HTTP 读取交易服务签发的可信授权，不读取交易库。 */
@Component
public class TradeAuthorizationClient {
    private final RestClient http;
    public TradeAuthorizationClient(@Value("${agrimart.trade-url:http://localhost:8082}") String url,
            @Value("${agrimart.service-token:dev-service-token}") String token) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        http = RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Service-Token",token).build();
    }
    public PaymentAuthorization authorize(Long order, Long buyer, String payment) {
        var result = http.post().uri("/internal/orders/{id}/payment-authorizations",order)
                .body(Map.of("paymentNo",payment,"buyerId",buyer)).retrieve()
                .body(new ParameterizedTypeReference<Api.Result<PaymentAuthorization>>() {});
        if (result == null || result.data() == null) throw new IllegalStateException("AUTHORIZATION_UNAVAILABLE");
        return result.data();
    }
    /** T03 读取交易库中的自动补偿授权，消息内容不能代替退款授权。 */
    public edu.swu.agrimart.common.payment.dto.RefundState authorizeRefund(String no) {
        var result=http.get().uri("/internal/refunds/{no}/execution-authorization",no).retrieve()
                .body(new ParameterizedTypeReference<Api.Result<edu.swu.agrimart.common.payment.dto.RefundState>>() {});
        if (result==null || result.data()==null) throw new IllegalStateException("REFUND_AUTHORIZATION_UNAVAILABLE");
        return result.data();
    }
}
