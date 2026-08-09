package com.quant.trade.marketdata.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.marketdata.dto.SecurityMetadataEnrichRequestDTO;
import com.quant.trade.marketdata.exception.SecurityDirectoryImportException;
import com.quant.trade.marketdata.exception.SecurityDirectoryNotFoundException;
import com.quant.trade.marketdata.service.SecurityDirectoryService;
import com.quant.trade.marketdata.service.SecurityMetadataEnrichmentService;
import com.quant.trade.marketdata.vo.SecurityDetailVO;
import com.quant.trade.marketdata.vo.SecurityDirectoryImportResultVO;
import com.quant.trade.marketdata.vo.SecurityMetadataEnrichVO;
import com.quant.trade.marketdata.vo.SecuritySearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/** 本地证券目录 D1 REST 接口；enrich 端点按需补全本地目录已存在证券的元数据（D3-03）。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-data")
@RequiredArgsConstructor
public class SecurityDirectoryController {

    private final SecurityDirectoryService securityDirectoryService;
    private final SecurityMetadataEnrichmentService securityMetadataEnrichmentService;

    @PostMapping("/security-directory/import")
    public ApiResponse<SecurityDirectoryImportResultVO> importDirectory(
            @RequestPart(value = "file", required = false) MultipartFile file) throws IOException {
        if (file == null) {
            return ApiResponse.ok(securityDirectoryService.importCsv(null, 0));
        }
        return ApiResponse.ok(securityDirectoryService.importCsv(file.getInputStream(), file.getSize()));
    }

    @GetMapping("/securities/search")
    public ApiResponse<SecuritySearchResultVO> search(
            @RequestParam String q,
            @RequestParam(required = false) List<String> markets,
            @RequestParam(required = false) List<String> types,
            @RequestParam(defaultValue = "false") boolean includeDelisted,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(securityDirectoryService.search(q, markets, types, includeDelisted, limit));
    }

    @GetMapping("/securities/{canonicalSymbol}")
    public ApiResponse<SecurityDetailVO> detail(@PathVariable String canonicalSymbol) {
        return ApiResponse.ok(securityDirectoryService.detail(canonicalSymbol));
    }

    /**
     * D3-03 按需补全本地目录已存在证券的元数据（LongPort Static Info）。
     * <p>
     * 错误全部经现有异常体系：disabled/invalid/provider-fail/身份不一致 → {@code BusinessException}
     * → {@code GlobalExceptionHandler}（400）；本地缺失 → {@link SecurityDirectoryNotFoundException}
     * → 本 controller 既有 404 handler。不新建 handler，不泄露凭据。
     */
    @PostMapping("/security-directory/enrich")
    public ApiResponse<SecurityMetadataEnrichVO> enrich(
            @Valid @RequestBody SecurityMetadataEnrichRequestDTO request) {
        return ApiResponse.ok(securityMetadataEnrichmentService.enrich(
                request.canonicalSymbol(), request.persistOrDefault()));
    }

    @ExceptionHandler(SecurityDirectoryImportException.class)
    public ResponseEntity<ApiResponse<SecurityDirectoryImportResultVO>> handleImport(
            SecurityDirectoryImportException exception) {
        HttpStatus status = "CSV_FILE_TOO_LARGE".equals(exception.getErrorCode().getCode())
                ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
        ApiResponse<SecurityDirectoryImportResultVO> body = new ApiResponse<>(
                false, exception.getErrorCode().getCode(), exception.getMessage(),
                exception.getResult(), LocalDateTime.now());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(SecurityDirectoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(SecurityDirectoryNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(exception.getErrorCode(), exception.getMessage()));
    }
}
