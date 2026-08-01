package com.quant.trade.marketdata;

import com.quant.trade.marketdata.config.SecurityDirectoryProperties;
import com.quant.trade.marketdata.constant.SecurityDirectoryConstants;
import com.quant.trade.marketdata.controller.SecurityDirectorySyncController;
import com.quant.trade.marketdata.dao.SecurityDirectorySyncStateMapper;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.provider.DisabledSecurityDirectoryProvider;
import com.quant.trade.marketdata.service.SecurityDirectoryService;
import com.quant.trade.marketdata.service.SecurityDirectorySyncService;
import com.quant.trade.marketdata.vo.MarketDataSyncTaskVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AC-03：sync 触发/任务详情/状态 API 契约。provider disabled → HTTP 400 + BUSINESS_RULE_VIOLATION，
 * 不返回凭据；任务不存在 → HTTP 404；status 不泄露路径/凭据。
 */
@ExtendWith(MockitoExtension.class)
class SecurityDirectorySyncControllerTest {

    @Mock
    private SecurityDirectorySyncService syncService;
    @Mock
    private SecurityDirectorySyncStateMapper syncStateMapper;
    @Mock
    private StockBasicMapper stockBasicMapper;
    @Mock
    private SecurityDirectoryService directoryService;

    private SecurityDirectoryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SecurityDirectoryProperties();
    }

    private SecurityDirectorySyncController disabledController() {
        return new SecurityDirectorySyncController(syncService,
                new DisabledSecurityDirectoryProvider(), syncStateMapper, stockBasicMapper,
                directoryService, properties, Clock.systemUTC());
    }

    @Test
    void syncWhenProviderDisabledReturnsBusinessRuleViolation() {
        SecurityDirectorySyncController controller = disabledController();

        ResponseEntity<ApiResponse<Void>> response = controller.handleBusiness(
                new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "证券目录同步 provider 未启用"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCodeEnum.BUSINESS_RULE_VIOLATION.getCode(), response.getBody().getCode());
        // 不返回凭据/路径：消息不含 path/token/secret。
        assertFalse(response.getBody().getMessage().toLowerCase().contains("path"));
        assertFalse(response.getBody().getMessage().toLowerCase().contains("token"));
    }

    @Test
    void taskNotFoundReturns404() {
        when(syncService.getTask(999L)).thenReturn(null);
        SecurityDirectorySyncController controller = disabledController();

        ResponseEntity<ApiResponse<MarketDataSyncTaskVO>> response = controller.task(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(ErrorCodeEnum.RESOURCE_NOT_FOUND.getCode(), response.getBody().getCode());
    }

    @Test
    void statusDoesNotLeakSecretsOrPaths() {
        when(stockBasicMapper.countAll()).thenReturn(0L);
        SecurityDirectorySyncController controller = disabledController();

        ApiResponse<com.quant.trade.marketdata.vo.SecurityDirectoryStatusVO> response = controller.status();
        com.quant.trade.marketdata.vo.SecurityDirectoryStatusVO data = response.getData();

        // provider disabled：providerEnabled=false。
        assertFalse(data.providerEnabled());
        // 空目录 catalogStatus=EMPTY，无 lastSnapshotId。
        assertEquals("EMPTY", data.catalogStatus());
        assertNull(data.lastSnapshotId());
        // VO 无路径/凭据字段（record 字段名固定，无 path/token/secret/key）。
        assertFalse(data.toString().toLowerCase().contains("path"));
    }
}
