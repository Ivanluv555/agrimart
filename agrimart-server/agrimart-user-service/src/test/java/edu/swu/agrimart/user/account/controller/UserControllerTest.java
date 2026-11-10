package edu.swu.agrimart.user.account.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.swu.agrimart.user.account.dto.AuthView;
import edu.swu.agrimart.user.account.dto.Login;
import edu.swu.agrimart.user.account.dto.Register;
import edu.swu.agrimart.user.account.dto.UserView;
import edu.swu.agrimart.user.account.entity.User;
import edu.swu.agrimart.user.account.service.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.OffsetDateTime;

/** 验证提供账户注册、登录、退出与本人资料接口的测试。 */
class UserControllerTest {
    private AuthService auth;
    private UserController controller;

    /** 初始化当前测试所需的替身、校验器或样例数据。 */
    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        controller = new UserController(auth);
    }

    /** 验证注册接口完整传递请求字段并包装账户视图。 */
    @Test
    void registrationDelegatesEveryRequestFieldAndWrapsView() {
        var request = new Register("farmer", "secret123", "Name", "13800138000", "a@example.com");
        var view = view();
        when(auth.register(
                        request.username(),
                        request.password(),
                        request.realName(),
                        request.phone(),
                        request.email()))
                .thenReturn(view);
        var result = controller.register(request);
        assertThat(result.code()).isEqualTo(200);
        assertThat(result.data()).isSameAs(view);
        assertThat(result.traceId()).isNotBlank();
    }

    /** 验证登录接口传递凭据并包装令牌视图。 */
    @Test
    void loginDelegatesCredentialsAndReturnsTokenView() {
        var request = new Login("farmer", "secret123");
        var view = new AuthView("token", "Bearer", 7200, view());
        when(auth.login(request.username(), request.password())).thenReturn(view);
        assertThat(controller.login(request).data()).isSameAs(view);
    }

    /** 验证用户接口向上抛出业务服务异常。 */
    @Test
    void serviceFailuresAreNotSwallowed() {
        when(auth.login("farmer", "wrong")).thenThrow(new SecurityException("INVALID_CREDENTIALS"));
        assertThatThrownBy(() -> controller.login(new Login("farmer", "wrong")))
                .isInstanceOf(SecurityException.class)
                .hasMessage("INVALID_CREDENTIALS");
        when(auth.register(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("DUPLICATE_ACCOUNT"));
        assertThatIllegalStateException()
                .isThrownBy(
                        () ->
                                controller.register(
                                        new Register(
                                                "farmer",
                                                "secret123",
                                                "Name",
                                                "13800138000",
                                                "a@example.com")))
                .withMessage("DUPLICATE_ACCOUNT");
    }

    /** 验证退出操作先认证再吊销会话。 */
    @Test
    void logoutAuthenticatesBeforeRevoking() {
        MockHttpServletRequest request = request("Bearer token");
        when(auth.resolve("token")).thenReturn(new User());
        var result = controller.logout(request);
        var order = inOrder(auth);
        order.verify(auth).resolve("token");
        order.verify(auth).logout("token");
        assertThat(result.code()).isEqualTo(200);
        assertThat(result.data()).isNull();
    }

    /** 验证认证失败的退出请求不会吊销会话。 */
    @Test
    void rejectedLogoutNeverRevokes() {
        when(auth.resolve("bad")).thenThrow(new SecurityException("UNAUTHENTICATED"));
        assertThatThrownBy(() -> controller.logout(request("Bearer bad")))
                .isInstanceOf(SecurityException.class);
        verify(auth, never()).logout(any());
    }

    /** 验证本人资料接口解析令牌并返回账户视图。 */
    @Test
    void meResolvesBearerTokenAndMapsUser() {
        User user = new User();
        var view = view();
        when(auth.resolve("token")).thenReturn(user);
        when(auth.view(user)).thenReturn(view);
        assertThat(controller.me(request("Bearer token")).data()).isSameAs(view);
        verify(auth).resolve("token");
    }

    /**
     * 验证缺失或非 Bearer 请求头按未提供令牌处理。
     *
     * @param header 模拟请求头
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Basic token", "bearer token", "Bearer"})
    void missingOrNonBearerHeadersPassNullForAuthentication(String header) {
        when(auth.resolve(null)).thenThrow(new SecurityException("UNAUTHENTICATED"));
        assertThatThrownBy(() -> controller.me(request(header)))
                .isInstanceOf(SecurityException.class);
        verify(auth).resolve(null);
        verify(auth, never()).view(any());
    }

    /** 验证空 Bearer 凭据按缺失凭据处理。 */
    @Test
    void emptyBearerIsUnauthenticated() {
        when(auth.resolve(null)).thenThrow(new SecurityException("UNAUTHENTICATED"));
        assertThatThrownBy(() -> controller.me(request("Bearer ")))
                .isInstanceOf(SecurityException.class);
        verify(auth).resolve(null);
    }

    /** 验证用户服务健康接口无需认证。 */
    @Test
    void healthDoesNotRequireAuthentication() {
        var response = controller.health();
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.data()).isNull();
        verifyNoInteractions(auth);
    }

    /**
     * 构造测试使用的HTTP 请求。
     *
     * @param header 模拟请求头
     * @return 测试所需的样例数据或配置对象
     */
    private static MockHttpServletRequest request(String header) {
        var request = new MockHttpServletRequest();
        if (header != null) request.addHeader("Authorization", header);
        return request;
    }

    /**
     * 构造测试使用的用户公开资料。
     *
     * @return 测试所需的样例数据或配置对象
     */
    private static UserView view() {
        return new UserView(
                1L,
                "farmer",
                "Name",
                "13800138000",
                "a@example.com",
                "FARMER",
                "ACTIVE",
                OffsetDateTime.now());
    }
}
