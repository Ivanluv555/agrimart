package edu.swu.agrimart.user.account.controller;

import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.common.auth.service.RequestAuth;
import edu.swu.agrimart.common.identity.dto.AuthContext;
import edu.swu.agrimart.common.merchant.dto.MerchantProfile;
import edu.swu.agrimart.user.account.dto.IntrospectionRequest;
import edu.swu.agrimart.user.account.entity.User;
import edu.swu.agrimart.user.account.repository.UserRepository;
import edu.swu.agrimart.user.account.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/** 提供内部令牌校验及商家资格查询接口。 */
@RestController
@RequestMapping("/internal")
public class InternalAuthController {
    private final AuthService auth;
    private final UserRepository users;
    private final String token;

    /**
     * 创建提供内部令牌校验及商家资格查询接口。
     *
     * @param a 认证服务
     * @param u 用户仓储
     * @param t 调用凭据
     */
    public InternalAuthController(
            AuthService a,
            UserRepository u,
            @Value("${agrimart.service-token:dev-service-token}") String t) {
        auth = a;
        users = u;
        token = t;
    }

    /**
     * 校验内部调用凭据并返回令牌所属身份。
     *
     * @param x 当前业务请求
     * @param r 当前 HTTP 请求
     * @return 接口响应数据
     */
    @PostMapping("/auth/introspections")
    public Api.Result<?> introspect(
            @Valid @RequestBody IntrospectionRequest x, HttpServletRequest r) {
        RequestAuth.requireService(r, token);
        User u = auth.resolve(x.accessToken());
        return Api.Result.ok(
                new AuthContext(
                        u.getId(),
                        u.getUsername(),
                        u.getRole().name(),
                        OffsetDateTime.now().plusHours(2)));
    }

    /**
     * 查询商家的角色资格及启用状态。
     *
     * @param id 记录标识
     * @param r 当前 HTTP 请求
     * @return 接口响应数据
     */
    @GetMapping("/merchants/{id}")
    public Api.Result<?> merchant(@PathVariable Long id, HttpServletRequest r) {
        RequestAuth.requireService(r, token);
        User u =
                users.findById(id)
                        .orElseThrow(
                                () -> new java.util.NoSuchElementException("RESOURCE_NOT_FOUND"));
        return Api.Result.ok(
                new MerchantProfile(
                        id,
                        u.getRole() == User.Role.MERCHANT,
                        u.getStatus() == User.Status.ACTIVE,
                        u.getUsername()));
    }
}
