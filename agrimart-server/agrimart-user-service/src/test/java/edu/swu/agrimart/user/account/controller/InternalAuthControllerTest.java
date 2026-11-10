package edu.swu.agrimart.user.account.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.swu.agrimart.common.identity.dto.AuthContext;
import edu.swu.agrimart.common.merchant.dto.MerchantProfile;
import edu.swu.agrimart.user.account.dto.IntrospectionRequest;
import edu.swu.agrimart.user.account.entity.User;
import edu.swu.agrimart.user.account.repository.UserRepository;
import edu.swu.agrimart.user.account.service.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

/** 验证提供内部令牌校验及商家资格查询接口的测试。 */
class InternalAuthControllerTest {
    private AuthService auth;
    private UserRepository users;
    private InternalAuthController controller;

    /** 初始化当前测试所需的替身、校验器或样例数据。 */
    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        users = mock(UserRepository.class);
        controller = new InternalAuthController(auth, users, "service-secret");
    }

    /**
     * 验证两个内部接口均在访问业务依赖前拒绝错误凭据。
     *
     * @param token 访问令牌或待验证的调用凭据
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"wrong", " service-secret", "SERVICE-SECRET"})
    void bothEndpointsRejectInvalidServiceCredentialsBeforeCallingDependencies(String token) {
        assertThatThrownBy(
                        () ->
                                controller.introspect(
                                        new IntrospectionRequest("access"), request(token)))
                .isInstanceOf(SecurityException.class)
                .hasMessage("UNAUTHENTICATED");
        assertThatThrownBy(() -> controller.merchant(7L, request(token)))
                .isInstanceOf(SecurityException.class)
                .hasMessage("UNAUTHENTICATED");
        verifyNoInteractions(auth, users);
    }

    /** 验证内部令牌校验返回身份、角色和当前两小时到期约定。 */
    @Test
    void introspectionMapsIdentityRoleAndTwoHourExpiry() {
        User user = new User();
        user.setUsername("merchant");
        user.setRole(User.Role.MERCHANT);
        ReflectionTestUtils.setField(user, "id", 7L);
        when(auth.resolve("access")).thenReturn(user);
        OffsetDateTime before = OffsetDateTime.now().plusHours(2);
        var result =
                controller.introspect(
                        new IntrospectionRequest("access"), request("service-secret"));
        assertThat(result.code()).isEqualTo(200);
        var context = (AuthContext) result.data();
        assertThat(context.userId()).isEqualTo(7L);
        assertThat(context.username()).isEqualTo("merchant");
        assertThat(context.role()).isEqualTo("MERCHANT");
        assertThat(context.expiresAt()).isBetween(before, OffsetDateTime.now().plusHours(2));
        verify(auth).resolve("access");
        verifyNoInteractions(users);
    }

    /** 验证内部接口向上传递无效访问令牌错误。 */
    @Test
    void introspectionPropagatesInvalidAccessToken() {
        when(auth.resolve(null)).thenThrow(new SecurityException("UNAUTHENTICATED"));
        assertThatThrownBy(
                        () ->
                                controller.introspect(
                                        new IntrospectionRequest(null), request("service-secret")))
                .isInstanceOf(SecurityException.class)
                .hasMessage("UNAUTHENTICATED");
    }

    /** 验证商家资格标志与账户角色、启用状态一致。 */
    @Test
    void merchantProfileFlagsReflectEveryRoleAndStatusCombination() {
        for (User.Role role : User.Role.values()) {
            for (User.Status status : User.Status.values()) {
                User user = new User();
                user.setUsername("shop");
                user.setRole(role);
                ReflectionTestUtils.setField(user, "status", status);
                when(users.findById(7L)).thenReturn(Optional.of(user));
                var result = controller.merchant(7L, request("service-secret"));
                assertThat(result.data())
                        .isEqualTo(
                                new MerchantProfile(
                                        7L,
                                        role == User.Role.MERCHANT,
                                        status == User.Status.ACTIVE,
                                        "shop"));
            }
        }
    }

    /** 验证不存在的商家返回资源缺失错误。 */
    @Test
    void missingMerchantReportsResourceNotFound() {
        when(users.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.merchant(7L, request("service-secret")))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("RESOURCE_NOT_FOUND");
    }

    /**
     * 构造测试使用的HTTP 请求。
     *
     * @param token 访问令牌或待验证的调用凭据
     * @return 测试所需的样例数据或配置对象
     */
    private static MockHttpServletRequest request(String token) {
        var request = new MockHttpServletRequest();
        if (token != null) request.addHeader("X-Service-Token", token);
        return request;
    }
}
