package edu.swu.agrimart.user.account.dto;

import jakarta.validation.constraints.*;

/**
 * 使用用户名与密码登录的请求。
 *
 * @param username 用户名
 * @param password 原始密码
 */
public record Login(@NotBlank String username, @NotBlank String password) {}
