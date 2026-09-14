package edu.swu.agridemo.module.user.dto;

import edu.swu.agridemo.module.user.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRegisterDto(
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度为3-50个字符")
    String username,

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度为6-20个字符")
    String password,

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名长度不超过50个字符")
    String realName,

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    String phone,

    String email,

    User.UserRole role,

    String address
) {
    public UserRegisterDto {
        if (role == null) {
            role = User.UserRole.FARMER;
        }
    }
}
