package edu.swu.agrimart.common.identity.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

/** 验证身份协议字段的测试。 */
class IdentityContractTest {
    /** 验证身份协议保留用户身份、角色、到期时间和访问令牌。 */
    @Test
    void authenticationRecordsPreserveIdentityAndToken() {
        var expiry = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        var context = new AuthContext(7L, "buyer", "BUYER", expiry);
        assertEquals(7L, context.userId());
        assertEquals("buyer", context.username());
        assertEquals("BUYER", context.role());
        assertEquals(expiry, context.expiresAt());
        assertEquals("token", new IntrospectionRequest("token").accessToken());
    }
}
