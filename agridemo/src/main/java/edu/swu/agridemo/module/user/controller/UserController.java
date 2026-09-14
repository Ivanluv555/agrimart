package edu.swu.agridemo.module.user.controller;

import edu.swu.agridemo.core.result.Result;
import edu.swu.agridemo.module.user.dto.UserLoginDto;
import edu.swu.agridemo.module.user.dto.UserRegisterDto;
import edu.swu.agridemo.module.user.dto.UserVo;
import edu.swu.agridemo.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@Tag(name = "用户管理", description = "用户注册、登录、信息查询")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<UserVo> register(@Valid @RequestBody UserRegisterDto dto) {
        return Result.success(userService.register(dto));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<UserVo> login(@Valid @RequestBody UserLoginDto dto) {
        return Result.success(userService.login(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取用户信息")
    public Result<UserVo> getUserById(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }
}
