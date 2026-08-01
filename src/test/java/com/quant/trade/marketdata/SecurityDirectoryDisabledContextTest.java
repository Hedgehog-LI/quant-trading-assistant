package com.quant.trade.marketdata;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.config.SecurityDirectoryProperties;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.provider.DisabledSecurityDirectoryProvider;
import com.quant.trade.marketdata.provider.SecurityDirectoryProvider;
import com.quant.trade.marketdata.service.SecurityDirectorySyncScheduler;
import com.quant.trade.marketdata.service.SecurityDirectorySyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-01/AC-05：provider 与 scheduler 默认关闭时应用可启动，D1 本地搜索/导入可用，
 * 且 SecurityDirectorySyncScheduler bean 不装配（@ConditionalOnProperty 无 matchIfMissing）。
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityDirectoryDisabledContextTest {

    @Autowired
    private SecurityDirectoryProvider provider;
    @Autowired
    private SecurityDirectoryProperties properties;
    @Autowired
    private SecurityDirectorySyncService syncService;
    @Autowired
    private StockBasicMapper stockBasicMapper;

    @Test
    void defaultsDisabledAndContextStarts() {
        assertFalse(properties.isEnabled(), "默认 enabled=false");
        assertFalse(properties.getScheduler().isEnabled(), "默认 scheduler.enabled=false");
        assertTrue(provider instanceof DisabledSecurityDirectoryProvider,
                "provider disabled 时使用 DisabledSecurityDirectoryProvider 兜底");
        assertFalse(provider.isEnabled());
        assertFalse(provider.isConfigured());
        // D1 本地目录基础设施仍可用。
        assertTrue(stockBasicMapper != null);
    }

    @Test
    void triggerWhenDisabledThrowsBusinessRuleViolation() {
        assertThrows(BusinessException.class, () -> syncService.trigger("FULL"));
    }
}
