package com.quant.trade.marketdata.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalSymbolUtilsTest {

    @Test
    void normalizeSupportedMarkets() {
        assertEquals("SH.600519", CanonicalSymbolUtils.normalize(" sh.600519 "));
        assertEquals("HK.02498", CanonicalSymbolUtils.normalize("hk.2498"));
        assertEquals("HK.00700", CanonicalSymbolUtils.normalize("HK.00700"));
        assertEquals("US.AAPL", CanonicalSymbolUtils.normalize("us.aapl"));
        assertEquals("US.BRK.B", CanonicalSymbolUtils.normalize("US.BRK.B"));
        assertEquals("US.BRK-B", CanonicalSymbolUtils.normalize("US.BRK-B"));
    }

    @Test
    void rejectsInvalidSymbols() {
        assertFalse(CanonicalSymbolUtils.isValid(null));
        assertFalse(CanonicalSymbolUtils.isValid("HK.00000"));
        assertFalse(CanonicalSymbolUtils.isValid("HK.ABC"));
        assertFalse(CanonicalSymbolUtils.isValid("US.AAPL/WS"));
        assertFalse(CanonicalSymbolUtils.isValid("XX.600519"));
        assertTrue(CanonicalSymbolUtils.isValid("HK.2498"));
    }
}
