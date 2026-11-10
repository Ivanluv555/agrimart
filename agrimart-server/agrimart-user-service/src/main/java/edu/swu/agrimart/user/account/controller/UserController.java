package edu.swu.agrimart.user.account.controller;

import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.common.auth.service.RequestAuth;
import edu.swu.agrimart.user.account.dto.Login;
import edu.swu.agrimart.user.account.dto.Register;
import edu.swu.agrimart.user.account.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

/** 提供账户注册、登录、退出与本人资料接口。 */
@RestController
@RequestMapping("/users")
public class UserController {
    private final AuthService auth;

    /**
     * 创建提供账户注册、登录、退出与本人资料接口。
     *
     * @param a 认证服务
     */
    public UserController(AuthService a) {
        auth = a;
    }

    /**
     * 提取请求中的 Bearer 访问令牌。
     *
     * @param r 当前 HTTP 请求
     * @return 接口响应数据
     */
    private String token(HttpServletRequest r) {
        return RequestAuth.bearer(r);
    }

    /**
     * 注册默认农户账户并编码密码。
     *
     * @param x 当前业务请求
     * @return 接口响应数据
     */
    @PostMapping("/register")
    public Api.Result<?> register(@Valid @RequestBody Register x) {
        return Api.Result.ok(
                auth.register(x.username(), x.password(), x.realName(), x.phone(), x.email()));
    }

    /**
     * 校验凭据并签发两小时有效的会话令牌。
     *
     * @param x 当前业务请求
     * @return 访问令牌、有效时长和账户资料
     */
    @PostMapping("/login")
    public Api.Result<?> login(@Valid @RequestBody Login x) {
        return Api.Result.ok(auth.login(x.username(), x.password()));
    }

    /**
     * 吊销令牌对应的用户会话。
     *
     * @param r 当前 HTTP 请求
     * @return 接口响应数据
     */
    @PostMapping("/logout")
    public Api.Result<?> logout(HttpServletRequest r) {
        auth.resolve(token(r));
        auth.logout(token(r));
        return Api.Result.ok();
    }

    /**
     * 返回当前登录用户的公开资料。
     *
     * @param r 当前 HTTP 请求
     * @return 接口响应数据
     */
    @GetMapping("/me")
    public Api.Result<?> me(HttpServletRequest r) {
        return Api.Result.ok(auth.view(auth.resolve(token(r))));
    }

    /**
     * 返回当前服务的健康状态。
     *
     * @return 接口响应数据
     */
    @GetMapping("/health")
    public Api.Result<?> health() {
        return Api.Result.ok();
    }
}
