package com.quant.trade.marketdata.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.config.SecurityDirectoryProperties;
import com.quant.trade.marketdata.dao.SecurityDirectorySyncStateMapper;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.dto.SecurityDirectorySyncRequestDTO;
import com.quant.trade.marketdata.enums.SecurityCatalogStatusEnum;
import com.quant.trade.marketdata.model.SecurityDirectorySyncStateDO;
import com.quant.trade.marketdata.provider.SecurityDirectoryProvider;
import com.quant.trade.marketdata.service.SecurityDirectoryService;
import com.quant.trade.marketdata.service.SecurityDirectorySyncService;
import com.quant.trade.marketdata.vo.MarketDataSyncTaskVO;
import com.quant.trade.marketdata.vo.SecurityDirectoryStatusVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 证券目录同步（D3）REST 接口：手动触发、任务详情、目录同步状态。不返回 provider 凭据或路径。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-data/security-directory")
@RequiredArgsConstructor
public class SecurityDirectorySyncController {

    private static final Duration STALE_AFTER = Duration.ofHours(48);

    private final SecurityDirectorySyncService syncService;
    private final SecurityDirectoryProvider provider;
    private final SecurityDirectorySyncStateMapper syncStateMapper;
    private final StockBasicMapper stockBasicMapper;
    private final SecurityDirectoryService securityDirectoryService;
    private final SecurityDirectoryProperties properties;
    private final Clock marketDataClock;

    @PostMapping("/sync")
    public ApiResponse<MarketDataSyncTaskVO> sync(@Valid @RequestBody(required = false)
                                                          SecurityDirectorySyncRequestDTO request) {
        String mode = request == null ? null : request.mode();
        return ApiResponse.ok(syncService.trigger(mode));
    }

    @GetMapping("/sync/tasks/{taskId}")
    public ResponseEntity<ApiResponse<MarketDataSyncTaskVO>> task(@PathVariable Long taskId) {
        MarketDataSyncTaskVO task = syncService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.fail(ErrorCodeEnum.RESOURCE_NOT_FOUND, "证券目录同步任务未找到: " + taskId));
        }
        return ResponseEntity.ok(ApiResponse.ok(task));
    }

    @GetMapping("/status")
    public ApiResponse<SecurityDirectoryStatusVO> status() {
        SecurityDirectorySyncStateDO state = syncStateMapper.selectByProvider(properties.getProviderCode());
        long total = stockBasicMapper.countAll();
        SecurityCatalogStatusEnum catalogStatus;
        Instant catalogUpdatedAt;
        boolean stale;
        if (total == 0) {
            catalogStatus = SecurityCatalogStatusEnum.EMPTY;
            catalogUpdatedAt = null;
            stale = false;
        } else {
            LocalDateTime max = stockBasicMapper.selectMaxSourceUpdatedAt();
            catalogStatus = SecurityCatalogStatusEnum.READY;
            catalogUpdatedAt = max == null ? null : max.toInstant(ZoneOffset.UTC);
            stale = max == null || marketDataClock.instant().isAfter(max.toInstant(ZoneOffset.UTC).plus(STALE_AFTER));
        }
        return ApiResponse.ok(new SecurityDirectoryStatusVO(
                provider.getProviderCode(),
                provider.isEnabled(),
                provider.isConfigured(),
                state == null ? null : state.getLastSuccessAt(),
                state == null ? null : state.getLastSnapshotId(),
                state == null ? null : state.getLastMode(),
                state == null ? null : state.getLastErrorCode(),
                catalogStatus.name(),
                catalogUpdatedAt,
                stale,
                false));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        HttpStatus status = ErrorCodeEnum.BUSINESS_RULE_VIOLATION.equals(exception.getErrorCode())
                ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(exception.getErrorCode(), exception.getMessage()));
    }
}
