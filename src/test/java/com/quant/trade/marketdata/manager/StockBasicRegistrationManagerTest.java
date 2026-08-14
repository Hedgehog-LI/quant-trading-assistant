package com.quant.trade.marketdata.manager;

import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.model.StockBasicDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 采集计划最小证券身份登记单测。 */
@ExtendWith(MockitoExtension.class)
class StockBasicRegistrationManagerTest {

    @Mock
    private StockBasicMapper stockBasicMapper;
    @InjectMocks
    private StockBasicRegistrationManager manager;

    @Test
    void ensureRegisteredNormalizesDeduplicatesAndKeepsMetadataMinimal() {
        manager.ensureRegistered(List.of("HK.2498", "HK.02498", "US.nvda"));

        ArgumentCaptor<StockBasicDO> captor = ArgumentCaptor.forClass(StockBasicDO.class);
        verify(stockBasicMapper, times(2)).insertMinimalIfAbsent(captor.capture());
        StockBasicDO hongKong = captor.getAllValues().get(0);
        assertEquals("HK.02498", hongKong.getCanonicalSymbol());
        assertEquals("02498", hongKong.getSymbol());
        assertEquals("HKD", hongKong.getCurrency());
        assertEquals("COLLECTION_PLAN", hongKong.getDataSource());
        assertFalse(hongKong.getDelisted());
    }
}
