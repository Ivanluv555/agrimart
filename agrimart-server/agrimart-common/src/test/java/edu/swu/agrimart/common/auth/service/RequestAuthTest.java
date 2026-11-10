package edu.swu.agrimart.common.auth.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/** 验证公共鉴权对空凭据、伪造身份和格式错误均拒绝。 */
class RequestAuthTest {
    /**
     * 验证无效用户标识不会被解释成参数错误或匿名身份。
     * @param id 请求用户标识
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "0", "-1", "9007199254740992", "99999999999999999999999"})
    void rejectsInvalidUserIdentity(String id) {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Gateway-Token", "gateway");
        if (id != null) request.addHeader("X-User-Id", id);
        assertThatThrownBy(() -> new RequestAuth("gateway").user(request))
                .isInstanceOf(SecurityException.class).hasMessage("UNAUTHENTICATED");
    }

    /** 验证服务身份与用户身份不能相互替换，空配置始终拒绝。 */
    @Test
    void credentialsAreNotInterchangeableAndBlankSecretsFailClosed() {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer user-token");
        request.addHeader("X-Gateway-Token", "gateway");
        request.addHeader("X-User-Id", "7");
        assertThat(new RequestAuth("gateway").user(request)).isEqualTo(7);
        assertThatThrownBy(() -> RequestAuth.requireService(request, "service"))
                .hasMessage("UNAUTHENTICATED");
        request.addHeader("X-Service-Token", "service");
        assertThatCode(() -> RequestAuth.requireService(request, "service")).doesNotThrowAnyException();
        assertThat(RequestAuth.matches("", "")).isFalse();
        assertThat(RequestAuth.matches(" ", " ")).isFalse();
        assertThat(RequestAuth.matches("secret", "SECRET")).isFalse();
        assertThat(RequestAuth.matches("secret", " secret ")).isFalse();
        assertThat(RequestAuth.matches("secret", null)).isFalse();
        assertThat(RequestAuth.matches(null, null)).isFalse();
        assertThat(RequestAuth.status(new SecurityException("FORBIDDEN"))).isEqualTo(403);
        assertThat(RequestAuth.status(new SecurityException("UNAUTHENTICATED"))).isEqualTo(401);
    }
}
