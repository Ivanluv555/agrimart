package edu.swu.agrimart.payment.payment.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import edu.swu.agrimart.payment.payment.service.PaymentService;
import edu.swu.agrimart.payment.payment.service.RefundService;
import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/** 回调必须对原始字节验签，拒绝改写、过期及未知供应商。 */
class SandboxCallbackControllerTest {
    final String secret="test-only-callback-secret";
    String signature(String timestamp,byte[] body) throws Exception {
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(mac.doFinal(body));
    }
    @Test
    void verifiesRawBodyAndRejectsTamperingAndReplay() throws Exception {
        var payments=mock(PaymentService.class);var refunds=mock(RefundService.class);
        var controller=new SandboxCallbackController(payments,refunds,secret);
        byte[] body="{\"paymentNo\":\"P1\",\"amount\":\"12.30\",\"currency\":\"CNY\",\"providerReference\":\"provider-1\",\"completedAt\":\"2026-11-10T00:00:00Z\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp=Long.toString(Instant.now().getEpochSecond());String signature=signature(timestamp,body);
        controller.payment("sandbox",timestamp,signature,body);
        verify(payments).callback(eq("P1"),eq(new java.math.BigDecimal("12.30")),eq("provider-1"),any());
        clearInvocations(payments);
        byte[] changed=new String(body,StandardCharsets.UTF_8).replace("12.30","99.00").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> controller.payment("sandbox",timestamp,signature,changed)).isInstanceOf(SecurityException.class);
        String old=Long.toString(Instant.now().minusSeconds(301).getEpochSecond());String oldSignature=signature(old,body);
        assertThatThrownBy(() -> controller.payment("sandbox",old,oldSignature,body)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> controller.payment("unknown",timestamp,signature,body)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(payments,refunds);
    }
}
