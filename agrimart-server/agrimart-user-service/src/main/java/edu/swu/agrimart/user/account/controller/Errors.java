package edu.swu.agrimart.user.account.controller;

import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.common.auth.service.RequestAuth;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 将业务异常转换为当前服务的统一 HTTP 错误响应。 */
@RestControllerAdvice
public class Errors {
    /**
     * 将认证异常映射为未认证响应。
     *
     * @param e 待处理的业务异常
     * @return 对应 HTTP 状态和统一错误响应
     */
    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Api.ErrorBody> auth(SecurityException e) {
        int status = RequestAuth.status(e);
        return ResponseEntity.status(status)
                .body(
                        new Api.ErrorBody(
                                status,
                                e.getMessage(),
                                e.getMessage(),
                                null,
                                UUID.randomUUID().toString()));
    }

    /**
     * 将状态或账户冲突映射为冲突响应。
     *
     * @param e 待处理的业务异常
     * @return 对应 HTTP 状态和统一错误响应
     */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Api.ErrorBody> conflict(IllegalStateException e) {
        return ResponseEntity.status(409)
                .body(
                        new Api.ErrorBody(
                                409,
                                e.getMessage(),
                                e.getMessage(),
                                null,
                                UUID.randomUUID().toString()));
    }

    /**
     * 将资源缺失映射为 404，不泄露私有资源存在性。
     * @param e 资源缺失异常
     * @return 统一资源缺失响应
     */
    @ExceptionHandler({java.util.NoSuchElementException.class,
            org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class})
    ResponseEntity<Api.ErrorBody> missing(Exception e) {
        return ResponseEntity.status(404).body(new Api.ErrorBody(
                404, "resource not found", "RESOURCE_NOT_FOUND", null, UUID.randomUUID().toString()));
    }

    /**
     * 将输入格式或校验失败映射为 400。
     * @param e 输入异常
     * @return 不含内部详情的参数错误响应
     */
    @ExceptionHandler({IllegalArgumentException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<Api.ErrorBody> invalid(Exception e) {
        return ResponseEntity.badRequest().body(new Api.ErrorBody(
                400, "invalid request", "VALIDATION_ERROR", null, UUID.randomUUID().toString()));
    }

    /**
     * 隐藏未知异常详情并返回服务器错误。
     * @param e 未预期的异常
     * @return 不含内部详情的服务器错误响应
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Api.ErrorBody> other(Exception e) {
        return ResponseEntity.internalServerError().body(new Api.ErrorBody(
                500, "internal error", "INTERNAL_ERROR", null, UUID.randomUUID().toString()));
    }
}
