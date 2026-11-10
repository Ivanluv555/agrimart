package edu.swu.agrimart.user.account.dto;

import jakarta.validation.constraints.*;

/**
 * 农户账户注册请求，包含凭据及联系资料。
 *
 * @param username 用户名
 * @param password 原始密码
 * @param realName 真实姓名
 * @param phone 手机号码
 * @param email 电子邮箱
 */
public record Register(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 50) String realName,
        @Pattern(regexp = "^1[3-9][0-9]{9}$") String phone,
        @Email @Size(max = 100) String email) {}
