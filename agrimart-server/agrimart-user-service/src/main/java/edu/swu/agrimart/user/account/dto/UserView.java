package edu.swu.agrimart.user.account.dto;

import java.time.OffsetDateTime;

/**
 * 不包含密码摘要的用户公开资料。
 *
 * @param id        记录标识
 * @param username  用户名
 * @param realName  真实姓名
 * @param phone     手机号码
 * @param email     电子邮箱
 * @param role      账户角色
 * @param status    业务状态
 * @param createdAt 创建时间
 */
public record UserView(
        Long id,
        String username,
        String realName,
        String phone,
        String email,
        String role,
        String status,
        OffsetDateTime createdAt) {
}
