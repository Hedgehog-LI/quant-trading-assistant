package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.exception.SecurityDirectoryNotFoundException;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichFields;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichReason;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichRequest;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichResult;
import com.quant.trade.marketdata.vo.SecurityMetadataEnrichVO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D3-03 service 编排纯 JUnit 单测（不启动 Spring）。
 * <p>
 * 覆盖：persist=false 展示不落库、persist=true 委托原子条件更新、provider 无数据/空白
 * 语义、受影响行数=0 时的 NO_CHANGE/404 分支、非法代码。数据库层「非空字段不被覆盖」
 * 由 H2 集成测试验证 SQL，本单测只验证 service 委托行为与规范化。
 */
class SecurityMetadataEnrichmentServiceTest {

    private static final String SYMBOL = "SH.600519";
    private static final String PROVIDER = "LONGPORT";

    /** Fake enricher 返回 Static-Info 形状（贵州茅台/100）。 */
    private static final class DataEnricher implements SecurityMetadataEnricher {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getProviderCode() {
            return PROVIDER;
        }

        @Override
        public EnrichResult enrich(EnrichRequest request) {
            EnrichFields fields = new EnrichFields("贵州茅台", "貴州茅台", "Kweichow Moutai",
                    "SSE", "CNY");
            return new EnrichResult(request.canonicalSymbol(), true, PROVIDER,
                    fields, 100, EnrichReason.OK);
        }
    }

    private static StockBasicMapper rowMapper() {
        StockBasicMapper mapper = Mockito.mock(StockBasicMapper.class);
        Mockito.when(mapper.selectByCanonicalSymbol(SYMBOL))
                .thenReturn(StockBasicDO.builder()
                        .id(1L).canonicalSymbol(SYMBOL).symbol("600519").market("SH")
                        .exchange("SSE").currency("CNY").build());
        return mapper;
    }

    @Test
    void persistFalseReturnsFieldsAndLotSize() {
        StockBasicMapper mapper = rowMapper();
        SecurityMetadataEnrichmentService service = new SecurityMetadataEnrichmentService(
                new DataEnricher(), mapper);

        SecurityMetadataEnrichVO vo = service.enrich(SYMBOL, false);

        assertEquals(SYMBOL, vo.canonicalSymbol());
        assertEquals(PROVIDER, vo.providerCode());
        assertTrue(vo.enriched(), "enriched=true");
        assertFalse(vo.persisted(), "persist=false 不写库");
        assertEquals("OK", vo.reason());
        assertEquals(100, vo.lotSize(), "A2：顶层 lotSize");
        assertNotNull(vo.fields());
        assertEquals("贵州茅台", vo.fields().nameCn());
        assertEquals("貴州茅台", vo.fields().nameHk());
        assertEquals("Kweichow Moutai", vo.fields().nameEn());
        assertEquals("SSE", vo.fields().exchange());
        assertEquals("CNY", vo.fields().currency());
        Mockito.verify(mapper).selectByCanonicalSymbol(SYMBOL);
        Mockito.verify(mapper, Mockito.never())
                .updateEmptyMetadataByCanonicalSymbol(Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void persistTrueFillsEmptyFieldsAndWrites() {
        StockBasicMapper mapper = rowMapper();
        Mockito.when(mapper.updateEmptyMetadataByCanonicalSymbol(SYMBOL, "贵州茅台", "貴州茅台",
                        "Kweichow Moutai", "SSE", "CNY"))
                .thenReturn(1);
        SecurityMetadataEnrichmentService service = new SecurityMetadataEnrichmentService(
                new DataEnricher(), mapper);

        SecurityMetadataEnrichVO vo = service.enrich(SYMBOL, true);

        assertTrue(vo.persisted(), "persist=true 且有可补字段 → 写入");
        assertEquals("OK", vo.reason());
        Mockito.verify(mapper).updateEmptyMetadataByCanonicalSymbol(
                SYMBOL, "贵州茅台", "貴州茅台", "Kweichow Moutai", "SSE", "CNY");
    }

    @Test
    void providerBlankFieldsAreNeverWritten() {
        StockBasicMapper mapper = rowMapper();
        SecurityMetadataEnricher blankEnricher = new SecurityMetadataEnricher() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public String getProviderCode() {
                return PROVIDER;
            }

            @Override
            public EnrichResult enrich(EnrichRequest request) {
                // 全是 null / 空字符串 / 纯空白：统一视为 null，不得写库。
                EnrichFields fields = new EnrichFields(null, "", "   ", " ", null);
                return new EnrichResult(request.canonicalSymbol(), true, PROVIDER,
                        fields, null, EnrichReason.OK);
            }
        };
        SecurityMetadataEnrichmentService service = new SecurityMetadataEnrichmentService(
                blankEnricher, mapper);

        SecurityMetadataEnrichVO vo = service.enrich(SYMBOL, true);

        assertFalse(vo.persisted(), "空白字段不落库");
        assertEquals("NO_CHANGE", vo.reason());
        Mockito.verify(mapper, Mockito.never())
                .updateEmptyMetadataByCanonicalSymbol(Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void providerNotFoundReturnsProviderNotFoundWithoutWrite() {
        StockBasicMapper mapper = rowMapper();
        SecurityMetadataEnricher notFoundEnricher = new SecurityMetadataEnricher() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public String getProviderCode() {
                return PROVIDER;
            }

            @Override
            public EnrichResult enrich(EnrichRequest request) {
                return new EnrichResult(request.canonicalSymbol(), false, PROVIDER,
                        null, null, EnrichReason.PROVIDER_NOT_FOUND);
            }
        };
        SecurityMetadataEnrichmentService service = new SecurityMetadataEnrichmentService(
                notFoundEnricher, mapper);

        SecurityMetadataEnrichVO vo = service.enrich(SYMBOL, true);

        assertFalse(vo.enriched(), "provider 无数据 enriched=false");
        assertFalse(vo.persisted(), "provider 无数据不落库");
        assertEquals("PROVIDER_NOT_FOUND", vo.reason(), "不能以 OK 掩盖 provider 无数据");
        Mockito.verify(mapper, Mockito.never())
                .updateEmptyMetadataByCanonicalSymbol(Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void affectedZeroRowExistsReturnsNoChange() {
        StockBasicMapper mapper = rowMapper();
        Mockito.when(mapper.updateEmptyMetadataByCanonicalSymbol(Mockito.anyString(),
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(0);
        SecurityMetadataEnrichmentService service = new SecurityMetadataEnrichmentService(
                new DataEnricher(), mapper);

        SecurityMetadataEnrichVO vo = service.enrich(SYMBOL, true);

        assertFalse(vo.persisted(), "0 行受影响且行存在 → 无可补字段");
        assertEquals("NO_CHANGE", vo.reason());
    }

    @Test
    void affectedZeroRowDeletedThrowsNotFound() {
        StockBasicMapper mapper = rowMapper();
        Mockito.when(mapper.updateEmptyMetadataByCanonicalSymbol(Mockito.anyString(),
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(0);
        // 行在 LongPort 调用期间被并发删除。
        Mockito.when(mapper.selectByCanonicalSymbol(SYMBOL))
                .thenReturn(null);
        SecurityMetadataEnrichmentService service = new SecurityMetadataEnrichmentService(
                new DataEnricher(), mapper);

        SecurityDirectoryNotFoundException exception = assertThrows(
                SecurityDirectoryNotFoundException.class,
                () -> service.enrich(SYMBOL, true));
        assertEquals(ErrorCodeEnum.STOCK_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void localMissingBeforeExternalCallReturns404() {
        StockBasicMapper mapper = Mockito.mock(StockBasicMapper.class);
        Mockito.when(mapper.selectByCanonicalSymbol(SYMBOL)).thenReturn(null);
        SecurityMetadataEnrichmentService service = new SecurityMetadataEnrichmentService(
                new DataEnricher(), mapper);

        SecurityDirectoryNotFoundException exception = assertThrows(
                SecurityDirectoryNotFoundException.class,
                () -> service.enrich(SYMBOL, false));
        assertEquals(ErrorCodeEnum.STOCK_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void invalidSymbolThrowsInvalidCanonical() {
        StockBasicMapper mapper = rowMapper();
        SecurityMetadataEnrichmentService service = new SecurityMetadataEnrichmentService(
                new DataEnricher(), mapper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.enrich("BAD.CANONICAL", false));
        assertEquals(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL, exception.getErrorCode());
    }
}
