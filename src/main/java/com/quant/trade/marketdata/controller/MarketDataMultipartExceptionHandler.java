package com.quant.trade.marketdata.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.vo.SecurityDirectoryImportErrorVO;
import com.quant.trade.marketdata.vo.SecurityDirectoryImportResultVO;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.List;

/** Servlet multipart 上限在进入 controller 前触发时仍返回稳定 413 envelope。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.quant.trade.marketdata.controller")
public class MarketDataMultipartExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return badRequest("缺少必需参数: " + exception.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return badRequest("参数类型不合法: " + exception.getName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<SecurityDirectoryImportResultVO>> handleMaxUploadSize() {
        SecurityDirectoryImportErrorVO error = new SecurityDirectoryImportErrorVO(
                0, "file", "FILE_TOO_LARGE", "CSV 文件超过 50 MiB 限制");
        SecurityDirectoryImportResultVO result = new SecurityDirectoryImportResultVO(
                0, 0, 0, 0, 0, 0, 0, 1, List.of(error));
        ApiResponse<SecurityDirectoryImportResultVO> body = new ApiResponse<>(
                false, ErrorCodeEnum.CSV_FILE_TOO_LARGE.getCode(), error.message(), result, LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        ApiResponse<Void> body = new ApiResponse<>(
                false, ErrorCodeEnum.PARAM_ERROR.getCode(), message, null, LocalDateTime.now());
        return ResponseEntity.badRequest().body(body);
    }
}
