package edu.swu.agrimart.common.api.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/** 验证统一响应信封及错误响应协议的测试。 */
class ApiTest {
    /** 验证成功响应保留数据并生成独立追踪标识。 */
    @Test
    void successRetainsPayloadAndGeneratesIndependentTraceIds() {
        Object data = new Object();
        var first = Api.Result.ok(data);
        var second = Api.Result.ok(data);
        assertEquals(200, first.code());
        assertEquals("success", first.message());
        assertSame(data, first.data());
        assertEquals(first.traceId(), UUID.fromString(first.traceId()).toString());
        assertNotEquals(first.traceId(), second.traceId());
    }

    /** 验证无负载成功响应的数据为空。 */
    @Test
    void successWithoutPayloadContainsNullData() {
        var result = Api.Result.ok();
        assertEquals(200, result.code());
        assertEquals("success", result.message());
        assertNull(result.data());
        assertNotNull(UUID.fromString(result.traceId()));
    }

    /** 验证响应构造器保留调用者传入的字段。 */
    @Test
    void resultConstructorPreservesExplicitValues() {
        var result = new Api.Result<>(201, "created", "payload", "trace");
        assertEquals(201, result.code());
        assertEquals("created", result.message());
        assertEquals("payload", result.data());
        assertEquals("trace", result.traceId());
    }

    /** 验证错误协议保留错误码、消息及可空业务数据。 */
    @Test
    void errorBodyPreservesErrorAndOptionalData() {
        var error = new Api.ErrorBody(403, "denied", "FORBIDDEN", null, "trace");
        assertEquals(403, error.code());
        assertEquals("denied", error.message());
        assertEquals("FORBIDDEN", error.errorCode());
        assertNull(error.data());
        assertEquals("trace", error.traceId());
    }
}
