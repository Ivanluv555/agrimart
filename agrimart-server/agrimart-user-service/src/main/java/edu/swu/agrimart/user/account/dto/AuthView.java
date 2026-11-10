package edu.swu.agrimart.user.account.dto;

/**
 * 登录成功后的访问令牌及账户资料。
 *
 * @param accessToken 访问令牌
 * @param tokenType   令牌类型
 * @param expiresIn   有效时长，单位为秒
 * @param user        用户资料
 */
public record AuthView(String accessToken, String tokenType, long expiresIn, UserView user) {
}
