package edu.swu.agridemo.module.user.dto;

import edu.swu.agridemo.module.user.entity.User;

import java.time.LocalDateTime;

public record UserVo(
    Long id,
    String username,
    String realName,
    String phone,
    String email,
    User.UserRole role,
    User.UserStatus status,
    String address,
    String avatar,
    String description,
    LocalDateTime createdAt
) {}
