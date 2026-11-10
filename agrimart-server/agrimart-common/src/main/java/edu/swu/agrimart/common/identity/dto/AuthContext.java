package edu.swu.agrimart.common.identity.dto;

import java.time.OffsetDateTime;

/**
 * 内部令牌校验返回的身份上下文。
 *
 * @param userId 用户标识
 * @param username 用户名
 * @param role 账户角色
 * @param expiresAt 到期时间
 */
public record AuthContext(Long userId, String username, String role, OffsetDateTime expiresAt) {}
