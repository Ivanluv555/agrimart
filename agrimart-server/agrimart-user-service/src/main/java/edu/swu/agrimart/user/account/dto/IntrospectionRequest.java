package edu.swu.agrimart.user.account.dto;

/**
 * 内部令牌校验请求。
 *
 * @param accessToken 访问令牌
 */
public record IntrospectionRequest(String accessToken) {
}
