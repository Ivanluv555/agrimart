package edu.swu.agrimart.common.merchant.dto;

/**
 * 供内部服务查询的商家资格与经营状态。
 *
 * @param merchantId 商家标识
 * @param approved 商家是否获准经营
 * @param active 商家是否处于启用状态
 * @param shopName 店铺名称
 */
public record MerchantProfile(Long merchantId, boolean approved, boolean active, String shopName) {}
