package com.quant.trade.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quant.trade.common.exception.ErrorCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 统一 API 响应封装。
 * <p>
 * 所有 REST 接口统一返回此格式，便于前端解析和错误处理。
 *
 * @param <T> 响应数据类型
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** 是否成功 */
    private final boolean success;

    /** 错误码 */
    private final String code;

    /** 错误信息（成功时为 null） */
    private final String message;

    /** 响应数据 */
    private final T data;

    /** 响应时间 */
    private final LocalDateTime timestamp;

    /**
     * 成功响应（带数据）。
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, ErrorCodeEnum.SUCCESS.getCode(), null, data, LocalDateTime.now());
    }

    /**
     * 成功响应（无数据）。
     */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, ErrorCodeEnum.SUCCESS.getCode(), null, null, LocalDateTime.now());
    }

    /**
     * 失败响应。
     *
     * @param errorCode 错误码枚举
     * @param message   错误描述
     */
    public static <T> ApiResponse<T> fail(ErrorCodeEnum errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null, LocalDateTime.now());
    }
}
