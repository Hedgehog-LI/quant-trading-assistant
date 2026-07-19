package com.quant.trade.marketdata.manager;

import com.quant.trade.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityCodeManagerTest {
    private final SecurityCodeManager manager = new SecurityCodeManager();

    @Test
    void convertsThreeMarketsToCanonicalSymbols() {
        assertEquals("SH.603308", manager.toCanonicalSymbol("CN", "603308"));
        assertEquals("HK.02498", manager.toCanonicalSymbol("HK", "2498"));
        assertEquals("US.NVDA", manager.toCanonicalSymbol("US", "nvda"));
        assertEquals("US.BRK.B", manager.toCanonicalSymbol("US", "brk.b"));
    }

    @Test
    void distinguishesShenzhenAndBeijingCodes() {
        assertEquals("SZ.000858", manager.toCanonicalSymbol("CN", "000858"));
        assertEquals("BJ.920001", manager.toCanonicalSymbol("CN", "920001"));
        assertEquals("BJ.832000", manager.toCanonicalSymbol("CN", "832000"));
        assertEquals("SH.512480", manager.toCanonicalSymbol("CN", "512480"));
    }

    @Test
    void rejectsAmbiguousOrMalformedInput() {
        assertThrows(BusinessException.class, () -> manager.toCanonicalSymbol("CN", "123"));
        assertThrows(BusinessException.class, () -> manager.toCanonicalSymbol("EU", "SAP"));
        assertThrows(BusinessException.class, () -> manager.toCanonicalSymbol("HK", "ABC"));
    }
}
