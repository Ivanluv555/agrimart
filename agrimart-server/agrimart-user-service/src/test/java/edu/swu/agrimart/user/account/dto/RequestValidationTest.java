package edu.swu.agrimart.user.account.dto;

import static org.assertj.core.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 验证注册、登录与内部账户请求校验的测试。 */
class RequestValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    /** 初始化当前测试所需的替身、校验器或样例数据。 */
    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /** 释放测试校验器资源。 */
    @AfterAll
    static void close() {
        factory.close();
    }

    /** 验证注册字段允许恰好满足长度上下界。 */
    @Test
    void registrationAcceptsExactMinimumAndMaximumLengths() {
        assertThat(
                        validator.validate(
                                new Register("abc", "12345678", "N", "13800138000", "a@b.com")))
                .isEmpty();
        assertThat(
                        validator.validate(
                                new Register(
                                        "u".repeat(50),
                                        "p".repeat(72),
                                        "n".repeat(50),
                                        "19900138000",
                                        "a".repeat(64) + "@" + "b".repeat(31) + ".com")))
                .isEmpty();
    }

    /** 验证注册请求同时报告所有无效字段。 */
    @Test
    void registrationReportsAllInvalidFields() {
        var violations = validator.validate(new Register("ab", "short", " ", "123", "invalid"));
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("username", "password", "realName", "phone", "email");
    }

    /** 验证注册字段拒绝超过最大长度。 */
    @Test
    void registrationRejectsOverlongFields() {
        var violations =
                validator.validate(
                        new Register(
                                "u".repeat(51),
                                "p".repeat(73),
                                "n".repeat(51),
                                "13800138000",
                                "a".repeat(101)));
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("username", "password", "realName", "email");
    }

    /** 记录必填文本与现有可空联系方式的校验约定。 */
    @Test
    void requiredTextRejectsNullWhileCurrentPhoneAndEmailConstraintsAllowNull() {
        var violations = validator.validate(new Register(null, null, null, null, null));
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("username", "password", "realName");
        assertThat(validator.validate(new Register("farmer", "secret123", "Name", null, null)))
                .isEmpty();
    }

    /**
     * 验证手机号拒绝错误号段、长度和字符。
     *
     * @param phone 手机号码
     */
    @ParameterizedTest
    @ValueSource(strings = {"12800138000", "1380013800", "138001380000", "1380013800x", ""})
    void phoneRejectsWrongPrefixLengthAndCharacters(String phone) {
        assertThat(validator.validate(new Register("farmer", "secret123", "Name", phone, null)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("phone");
    }

    /** 验证登录凭据不可为空白。 */
    @Test
    void loginRequiresNonblankCredentials() {
        assertThat(validator.validate(new Login("farmer", "password"))).isEmpty();
        assertThat(validator.validate(new Login(" ", null)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("username", "password");
    }

    /** 验证内部请求保留令牌，并由服务处理缺失令牌。 */
    @Test
    void internalRequestCarriesTokenAndCurrentlyAllowsNullForServiceToReject() {
        var request = new IntrospectionRequest("token");
        assertThat(request.accessToken()).isEqualTo("token");
        assertThat(validator.validate(request)).isEmpty();
        assertThat(validator.validate(new IntrospectionRequest(null))).isEmpty();
    }
}
