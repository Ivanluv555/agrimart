package edu.swu.agrimart.user.account.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.swu.agrimart.user.account.dto.UserView;
import edu.swu.agrimart.user.account.entity.Session;
import edu.swu.agrimart.user.account.entity.User;
import edu.swu.agrimart.user.account.repository.SessionRepository;
import edu.swu.agrimart.user.account.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** 验证用户注册、密码校验及可撤销会话的业务服务的测试。 */
class AuthServiceTest {
    private UserRepository users;
    private SessionRepository sessions;
    private PasswordEncoder encoder;
    private AuthService service;
    private User user;

    /** 初始化当前测试所需的替身、校验器或样例数据。 */
    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        sessions = mock(SessionRepository.class);
        encoder = mock(PasswordEncoder.class);
        service = new AuthService(users, sessions, encoder);
        user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setUsername("farmer");
        user.setPasswordHash("encoded");
        user.setRealName("Farmer Name");
        user.setPhone("13800138000");
        user.setEmail("farmer@example.com");
    }

    /** 验证注册编码密码并返回持久化账户的默认资料。 */
    @Test
    void registerEncodesPasswordAndReturnsSavedDefaults() {
        when(encoder.encode("secret123")).thenReturn("encoded");
        when(users.save(any(User.class)))
                .thenAnswer(
                        invocation -> {
                            User saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", 7L);
                            return saved;
                        });
        var result = service.register("farmer", "secret123", "Farmer Name", "13800138000", null);
        var captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded");
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.username()).isEqualTo("farmer");
        assertThat(result.realName()).isEqualTo("Farmer Name");
        assertThat(result.phone()).isEqualTo("13800138000");
        assertThat(result.email()).isNull();
        assertThat(result.role()).isEqualTo("FARMER");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.createdAt()).isNotNull();
        verify(users).existsByPhone("13800138000");
        verifyNoInteractions(sessions);
    }

    /** 验证用户名重复时提前终止后续检查和写入。 */
    @Test
    void duplicateUsernameShortCircuitsBeforePhoneAndEncoding() {
        when(users.existsByUsername("farmer")).thenReturn(true);
        assertThatIllegalStateException()
                .isThrownBy(() -> service.register("farmer", "pw", "name", "phone", null))
                .withMessage("DUPLICATE_ACCOUNT");
        verify(users, never()).existsByPhone(any());
        verify(users, never()).save(any());
        verifyNoInteractions(encoder, sessions);
    }

    /** 验证手机号重复时不会编码密码或保存账户。 */
    @Test
    void duplicatePhoneDoesNotSaveOrEncode() {
        when(users.existsByPhone("phone")).thenReturn(true);
        assertThatIllegalStateException()
                .isThrownBy(() -> service.register("new", "pw", "name", "phone", null))
                .withMessage("DUPLICATE_ACCOUNT");
        verify(users, never()).save(any());
        verifyNoInteractions(encoder, sessions);
    }

    /** 验证账户视图包含公开字段及角色状态名称。 */
    @Test
    void viewIncludesAllPublicFieldsAndEnumValues() {
        user.setRole(User.Role.ADMIN);
        ReflectionTestUtils.setField(user, "status", User.Status.INACTIVE);
        assertThat(service.view(user))
                .isEqualTo(
                        new UserView(
                                42L,
                                "farmer",
                                "Farmer Name",
                                "13800138000",
                                "farmer@example.com",
                                "ADMIN",
                                "INACTIVE",
                                user.getCreatedAt()));
    }

    /**
     * 验证登录仅存储令牌摘要并签发独立的两小时令牌。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void loginPersistsOnlyDigestAndIssuesIndependentTwoHourTokens() throws Exception {
        when(users.findByUsername("farmer")).thenReturn(Optional.of(user));
        when(encoder.matches("pw", "encoded")).thenReturn(true);
        var before = OffsetDateTime.now().plusHours(2);
        var first = service.login("farmer", "pw");
        var second = service.login("farmer", "pw");
        var after = OffsetDateTime.now().plusHours(2);
        var captor = ArgumentCaptor.forClass(Session.class);
        verify(sessions, times(2)).save(captor.capture());
        assertThat(first.accessToken())
                .matches("[A-Za-z0-9_-]{64}")
                .isNotEqualTo(second.accessToken());
        assertThat(Base64.getUrlDecoder().decode(first.accessToken())).hasSize(48);
        assertThat(first.tokenType()).isEqualTo("Bearer");
        assertThat(first.expiresIn()).isEqualTo(7200);
        assertThat(first.user()).isEqualTo(service.view(user));
        Session stored = captor.getAllValues().getFirst();
        assertThat(stored.getTokenHash())
                .isEqualTo(digest(first.accessToken()))
                .isNotEqualTo(first.accessToken());
        assertThat(stored.getExpiresAt()).isBetween(before, after);
        assertThat(stored.getUserId()).isEqualTo(42L);
        assertThat(stored.isRevoked()).isFalse();
    }

    /** 验证未知用户名不会触发密码校验。 */
    @Test
    void loginRejectsUnknownUsernameWithoutPasswordCheck() {
        when(users.findByUsername("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("missing", "pw"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("INVALID_CREDENTIALS");
        verifyNoInteractions(encoder, sessions);
    }

    /** 验证禁用账户在密码比较前被拒绝。 */
    @Test
    void loginRejectsInactiveUserBeforePasswordCheck() {
        ReflectionTestUtils.setField(user, "status", User.Status.INACTIVE);
        when(users.findByUsername("farmer")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.login("farmer", "pw"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("INVALID_CREDENTIALS");
        verifyNoInteractions(encoder, sessions);
    }

    /** 验证错误密码不会创建会话。 */
    @Test
    void loginRejectsIncorrectPasswordWithoutCreatingSession() {
        when(users.findByUsername("farmer")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "encoded")).thenReturn(false);
        assertThatThrownBy(() -> service.login("farmer", "wrong"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("INVALID_CREDENTIALS");
        verifyNoInteractions(sessions);
    }

    /**
     * 验证缺失令牌时不访问数据库。
     *
     * @param token 访问令牌或待验证的调用凭据
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void resolveRejectsMissingTokenWithoutDatabaseAccess(String token) {
        unauthenticated(() -> service.resolve(token));
        verifyNoInteractions(users, sessions);
    }

    /**
     * 验证未知令牌摘要无法解析身份。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void resolveRejectsUnknownDigest() throws Exception {
        unauthenticated(() -> service.resolve("unknown"));
        verify(sessions).findByTokenHash(digest("unknown"));
        verifyNoInteractions(users);
    }

    /**
     * 验证有效会话返回启用状态的所属账户。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void resolveReturnsActiveSessionOwner() throws Exception {
        Session session = validSession();
        when(sessions.findByTokenHash(digest("token"))).thenReturn(Optional.of(session));
        when(users.findById(42L)).thenReturn(Optional.of(user));
        assertThat(service.resolve("token")).isSameAs(user);
        verify(sessions, never()).save(any());
    }

    /**
     * 验证吊销会话在查询用户前被拒绝。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void resolveRejectsRevokedSessionBeforeUserLookup() throws Exception {
        Session session = validSession();
        session.setRevoked(true);
        when(sessions.findByTokenHash(digest("token"))).thenReturn(Optional.of(session));
        unauthenticated(() -> service.resolve("token"));
        verifyNoInteractions(users);
    }

    /**
     * 验证过期会话在查询用户前被拒绝。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void resolveRejectsExpiredSessionBeforeUserLookup() throws Exception {
        Session session = validSession();
        session.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        when(sessions.findByTokenHash(digest("token"))).thenReturn(Optional.of(session));
        unauthenticated(() -> service.resolve("token"));
        verifyNoInteractions(users);
    }

    /**
     * 验证会话到期边界采用严格早于当前时间的比较。
     *
     * @param nanosFromNow 相对当前时间的纳秒偏移
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void resolveExpiryBoundaryUsesStrictlyBeforeCurrentTime(long nanosFromNow) throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2030-01-01T00:00:00Z");
        Session session = validSession();
        session.setExpiresAt(now.plusNanos(nanosFromNow));
        when(sessions.findByTokenHash(digest("token"))).thenReturn(Optional.of(session));
        if (nanosFromNow >= 0) when(users.findById(42L)).thenReturn(Optional.of(user));
        try (var clock = mockStatic(OffsetDateTime.class)) {
            clock.when(OffsetDateTime::now).thenReturn(now);
            if (nanosFromNow < 0) {
                unauthenticated(() -> service.resolve("token"));
                verifyNoInteractions(users);
            } else {
                assertThat(service.resolve("token")).isSameAs(user);
            }
        }
    }

    /**
     * 验证会话所属用户已删除时拒绝认证。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void resolveRejectsDeletedOwner() throws Exception {
        when(sessions.findByTokenHash(digest("token"))).thenReturn(Optional.of(validSession()));
        when(users.findById(42L)).thenReturn(Optional.empty());
        unauthenticated(() -> service.resolve("token"));
    }

    /**
     * 验证会话所属账户禁用时拒绝认证。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void resolveRejectsInactiveOwner() throws Exception {
        ReflectionTestUtils.setField(user, "status", User.Status.INACTIVE);
        when(sessions.findByTokenHash(digest("token"))).thenReturn(Optional.of(validSession()));
        when(users.findById(42L)).thenReturn(Optional.of(user));
        unauthenticated(() -> service.resolve("token"));
    }

    /**
     * 验证重复退出仍保存同一会话的吊销状态。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void logoutRevokesAndSavesExistingSessionIdempotently() throws Exception {
        Session session = validSession();
        when(sessions.findByTokenHash(digest("token"))).thenReturn(Optional.of(session));
        service.logout("token");
        service.logout("token");
        assertThat(session.isRevoked()).isTrue();
        verify(sessions, times(2)).save(same(session));
        verifyNoInteractions(users, encoder);
    }

    /**
     * 验证未知令牌退出不会产生写入。
     *
     * @throws Exception 测试准备或执行过程中发生异常
     */
    @Test
    void logoutUnknownTokenDoesNotWrite() throws Exception {
        service.logout("unknown");
        verify(sessions).findByTokenHash(digest("unknown"));
        verify(sessions, never()).save(any());
    }

    /** 记录空令牌导致摘要失败且不会访问仓储的既有行为。 */
    @Test
    void logoutNullTokenWrapsHashFailureBeforeDatabaseAccess() {
        assertThatIllegalStateException()
                .isThrownBy(() -> service.logout(null))
                .withCauseInstanceOf(NullPointerException.class);
        verifyNoInteractions(sessions);
    }

    /**
     * 构造未过期且未吊销的测试会话。
     *
     * @return 测试所需的样例数据或配置对象
     */
    private Session validSession() {
        Session session = new Session();
        session.setUserId(42L);
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));
        return session;
    }

    /**
     * 计算测试令牌的 SHA-256 摘要。
     *
     * @param token 访问令牌或待验证的调用凭据
     * @return 测试所需的样例数据或配置对象
     * @throws Exception 测试准备或执行过程中发生异常
     */
    private static String digest(String token) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 断言操作抛出既定未认证错误。
     *
     * @param action 待验证的认证操作
     */
    private static void unauthenticated(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(SecurityException.class)
                .hasMessage("UNAUTHENTICATED");
    }
}
