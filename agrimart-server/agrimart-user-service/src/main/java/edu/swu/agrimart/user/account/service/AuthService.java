package edu.swu.agrimart.user.account.service;

import edu.swu.agrimart.user.account.dto.AuthView;
import edu.swu.agrimart.user.account.dto.UserView;
import edu.swu.agrimart.user.account.entity.Session;
import edu.swu.agrimart.user.account.entity.User;
import edu.swu.agrimart.user.account.repository.SessionRepository;
import edu.swu.agrimart.user.account.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** 用户注册、密码校验及可撤销会话的业务服务。 */
@Service
public class AuthService {
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordEncoder encoder;

    /**
     * 创建用户注册、密码校验及可撤销会话的业务服务。
     *
     * @param u 用户仓储
     * @param s 会话仓储
     * @param e 密码编码器
     */
    public AuthService(UserRepository u, SessionRepository s, PasswordEncoder e) {
        users = u;
        sessions = s;
        encoder = e;
    }

    /**
     * 映射不包含密码摘要的用户资料。
     *
     * @param u 待映射的用户账户
     * @return 用户公开资料
     */
    public UserView view(User u) {
        return new UserView(
                u.getId(),
                u.getUsername(),
                u.getRealName(),
                u.getPhone(),
                u.getEmail(),
                u.getRole().name(),
                u.getStatus().name(),
                u.getCreatedAt());
    }

    /**
     * 注册默认农户账户并编码密码。
     *
     * @param username 用户名
     * @param password 原始密码
     * @param realName 真实姓名
     * @param phone 手机号码
     * @param email 电子邮箱
     * @return 用户公开资料
     */
    @Transactional
    public UserView register(
            String username, String password, String realName, String phone, String email) {
        if (users.existsByUsername(username) || users.existsByPhone(phone))
            throw new IllegalStateException("DUPLICATE_ACCOUNT");
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setRealName(realName);
        u.setPhone(phone);
        u.setEmail(email);
        return view(users.save(u));
    }

    /**
     * 校验凭据并签发两小时有效的会话令牌。
     *
     * @param username 用户名
     * @param password 原始密码
     * @return 访问令牌、有效时长和账户资料
     */
    @Transactional
    public AuthView login(String username, String password) {
        User u =
                users.findByUsername(username)
                        .orElseThrow(() -> new SecurityException("INVALID_CREDENTIALS"));
        if (u.getStatus() != User.Status.ACTIVE || !encoder.matches(password, u.getPasswordHash()))
            throw new SecurityException("INVALID_CREDENTIALS");
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random(48));
        Session s = new Session();
        s.setTokenHash(hash(token));
        s.setUserId(u.getId());
        s.setExpiresAt(OffsetDateTime.now().plusHours(2));
        sessions.save(s);
        return new AuthView(token, "Bearer", 7200, view(u));
    }

    /**
     * 校验令牌、会话期限和账户状态并解析用户。
     *
     * @param token 访问令牌或待验证的调用凭据
     * @return 通过会话与状态校验的用户
     */
    public User resolve(String token) {
        if (token == null || token.isBlank()) throw new SecurityException("UNAUTHENTICATED");
        Session s =
                sessions.findByTokenHash(hash(token))
                        .orElseThrow(() -> new SecurityException("UNAUTHENTICATED"));
        if (s.isRevoked() || s.getExpiresAt().isBefore(OffsetDateTime.now()))
            throw new SecurityException("UNAUTHENTICATED");
        return users.findById(s.getUserId())
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .orElseThrow(() -> new SecurityException("UNAUTHENTICATED"));
    }

    /**
     * 吊销令牌对应的用户会话。
     *
     * @param token 访问令牌或待验证的调用凭据
     */
    @Transactional
    public void logout(String token) {
        sessions.findByTokenHash(hash(token))
                .ifPresent(
                        s -> {
                            s.setRevoked(true);
                            sessions.save(s);
                        });
    }

    /**
     * 生成指定长度的安全随机字节。
     *
     * @param n 随机字节数量
     * @return 安全随机字节数组
     */
    private static byte[] random(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    /**
     * 计算令牌的 SHA-256 摘要。
     *
     * @param s 原始访问令牌
     * @return 十六进制摘要
     */
    private static String hash(String s) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
