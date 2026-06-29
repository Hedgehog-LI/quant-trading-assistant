package com.quant.trade.common.exception;

import com.quant.trade.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static com.quant.trade.common.constant.MessageConstants.REQUEST_RESOURCE_NOT_FOUND;

/**
 * 全局异常处理器。
 * <p>
 * 将 {@link BusinessException}、Spring Validation 校验异常、
 * 以及未预期异常统一转换为 {@link ApiResponse} 格式返回给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return ApiResponse.fail(e.getErrorCode(), e.getMessage());
    }

    /**
     * 处理 Spring Validation 校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Validation exception: {}", message);
        return ApiResponse.fail(ErrorCodeEnum.VALIDATION_ERROR, message);
    }

    /**
     * 处理不存在的请求路径。
     * <p>
     * Spring MVC 会将未匹配到 Controller 的路径交给静态资源处理器，最终抛出
     * {@link NoResourceFoundException}。该异常属于客户端请求了不存在的资源，
     * 应返回 404，不能被兜底异常处理器错误包装成 500。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("Request resource not found: method={}, path={}", e.getHttpMethod(), e.getResourcePath());
        return ApiResponse.fail(ErrorCodeEnum.RESOURCE_NOT_FOUND, REQUEST_RESOURCE_NOT_FOUND);
    }

    /**
     * 兜底处理未预期异常。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected error: {}", ExceptionUtils.getRootCauseMessage(e), e);
        return ApiResponse.fail(ErrorCodeEnum.INTERNAL_ERROR, "系统内部错误，请稍后重试");
    }
}
