package edu.swu.agrimart.common.api.dto;

import java.util.UUID;

/** 统一响应信封及错误响应协议。 */
public final class Api {
    /** 禁止实例化统一响应工具。 */
    private Api() {}

    /**
     * 成功响应，携带业务数据与追踪标识。
     *
     * @param <T> 业务数据类型
     * @param code 响应状态码
     * @param message 响应消息
     * @param data 业务数据
     * @param traceId 请求追踪标识
     */
    public record Result<T>(int code, String message, T data, String traceId) {
        /**
         * 创建带有独立追踪标识的成功响应。
         *
         * @param <T> 业务数据类型
         * @param data 业务数据
         * @return 带追踪标识的成功响应
         */
        public static <T> Result<T> ok(T data) {
            return new Result<>(200, "success", data, UUID.randomUUID().toString());
        }

        /**
         * 创建带有独立追踪标识的成功响应。
         *
         * @return 带追踪标识的成功响应
         */
        public static Result<Void> ok() {
            return ok(null);
        }
    }

    /**
     * 失败响应，包含业务错误码与请求追踪标识。
     *
     * @param code 响应状态码
     * @param message 响应消息
     * @param errorCode 业务错误码
     * @param data 业务数据
     * @param traceId 请求追踪标识
     */
    public record ErrorBody(
            int code, String message, String errorCode, Object data, String traceId) {}
}
