package edu.swu.agrimart.payment.payment.controller;

import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.payment.payment.service.PaymentService;
import edu.swu.agrimart.payment.payment.service.RefundService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/** 本地 sandbox 协议：HMAC-SHA256(timestamp + '.' + 原始请求体)，有效窗口 5 分钟。 */
@RestController
public class SandboxCallbackController {
    private final PaymentService payments;
    private final RefundService refunds;
    private final String secret;
    private final JsonMapper json=JsonMapper.builder().build();
    public SandboxCallbackController(PaymentService payments, RefundService refunds,
            @Value("${payment.sandbox.callback-secret:}") String secret) {
        this.payments=payments; this.refunds=refunds; this.secret=secret;
    }
    public record Callback(String paymentNo, String refundNo, String amount, String currency,
            String providerReference, OffsetDateTime completedAt) {}
    @PostMapping("/payment-callbacks/{provider}/payments")
    public Api.Result<?> payment(@PathVariable String provider, @RequestHeader("X-Sandbox-Timestamp") String timestamp,
            @RequestHeader("X-Sandbox-Signature") String signature, @RequestBody byte[] body) {
        Callback callback=verified(provider,timestamp,signature,body);
        payments.callback(callback.paymentNo(),new BigDecimal(callback.amount()),callback.providerReference(),callback.completedAt());
        return Api.Result.ok();
    }
    @PostMapping("/payment-callbacks/{provider}/refunds")
    public Api.Result<?> refund(@PathVariable String provider, @RequestHeader("X-Sandbox-Timestamp") String timestamp,
            @RequestHeader("X-Sandbox-Signature") String signature, @RequestBody byte[] body) {
        Callback callback=verified(provider,timestamp,signature,body);
        refunds.callback(callback.refundNo(),new BigDecimal(callback.amount()),callback.providerReference(),callback.completedAt());
        return Api.Result.ok();
    }
    private Callback verified(String provider, String timestamp, String signature, byte[] body) {
        if (!"sandbox".equals(provider) || secret.isBlank() || body.length>16384) throw new SecurityException("UNAUTHENTICATED");
        try {
            long seconds=Long.parseLong(timestamp);
            long now=Instant.now().getEpochSecond();
            if (seconds < now-300 || seconds > now+300) throw new SecurityException("UNAUTHENTICATED");
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(mac.doFinal(body),HexFormat.of().parseHex(signature))) throw new SecurityException("UNAUTHENTICATED");
        } catch (java.security.GeneralSecurityException | IllegalArgumentException failure) {
            throw new SecurityException("UNAUTHENTICATED");
        }
        Callback callback=json.readValue(body,Callback.class);
        if (!"CNY".equals(callback.currency()) || callback.amount()==null
                || !callback.amount().matches("[0-9]{1,8}\\.[0-9]{2}")) throw new IllegalArgumentException("INVALID_CALLBACK");
        return callback;
    }
}
