package edu.swu.agrimart.common.merchant.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 验证供内部服务查询的商家资格与经营状态的测试。 */
class MerchantProfileTest {
    /** 验证商家批准标志与启用标志保持独立。 */
    @Test
    void merchantProfilePreservesIndependentApprovalAndActiveFlags() {
        var profile = new MerchantProfile(8L, true, false, "shop");
        assertEquals(8L, profile.merchantId());
        assertTrue(profile.approved());
        assertFalse(profile.active());
        assertEquals("shop", profile.shopName());
    }
}
