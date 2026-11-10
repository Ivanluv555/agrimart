package edu.swu.agrimart.user.account.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.swu.agrimart.user.account.repository.UserRepository;
import edu.swu.agrimart.user.account.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

/** 验证用户服务通过实际 MVC 异常解析返回契约状态。 */
class ErrorMappingTest {
    /**
     * 验证有效服务凭据查询不存在商家返回 404，无效凭据返回 401。
     * @throws Exception HTTP 测试失败
     */
    @Test
    void distinguishesMissingCredentialAndMissingResource() throws Exception {
        var users = mock(UserRepository.class);
        when(users.findById(7L)).thenReturn(Optional.empty());
        var mvc = MockMvcBuilders.standaloneSetup(
                new InternalAuthController(mock(AuthService.class), users, "service"))
                .setControllerAdvice(new Errors()).build();
        mvc.perform(get("/internal/merchants/7")).andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
        mvc.perform(get("/internal/merchants/7").header("X-Service-Token", "service"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    /**
     * 验证未授权、无效输入和服务器故障不会混淆。
     * @throws Exception HTTP 测试失败
     */
    @Test
    void distinguishesForbiddenBadInputAndInternalFailure() throws Exception {
        var auth = mock(AuthService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UserController(auth))
                .setControllerAdvice(new Errors()).build();
        when(auth.resolve("token")).thenThrow(new SecurityException("FORBIDDEN"));
        mvc.perform(get("/users/me").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        doThrow(new IllegalArgumentException("invalid")).when(auth).resolve("token");
        mvc.perform(get("/users/me").header("Authorization", "Bearer token"))
                .andExpect(status().isBadRequest());
        doThrow(new RuntimeException("database credentials")).when(auth).resolve("token");
        mvc.perform(get("/users/me").header("Authorization", "Bearer token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("internal error"));
    }
}
