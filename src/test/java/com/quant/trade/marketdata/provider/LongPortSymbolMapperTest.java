package com.quant.trade.marketdata.provider;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** LongPortSymbolMapper 双向映射测试。 */
class LongPortSymbolMapperTest {

    private final LongPortSymbolMapper mapper = new LongPortSymbolMapper();

    @Test
    void toLongPortSh() {
        assertEquals("600519.SH", mapper.toLongPort("SH.600519"));
    }

    @Test
    void toLongPortSz() {
        assertEquals("000001.SZ", mapper.toLongPort("SZ.000001"));
    }

    @Test
    void toLongPortBj() {
        assertEquals("430047.BJ", mapper.toLongPort("BJ.430047"));
    }

    @Test
    void toLongPortCaseInsensitive() {
        assertEquals("600519.SH", mapper.toLongPort("sh.600519"));
        assertEquals("600519.SH", mapper.toLongPort("  sh.600519  "));
    }

    @Test
    void toLongPortInvalidMarket() {
        assertNull(mapper.toLongPort("XX.600519"));
        assertNull(mapper.toLongPort("HK.00700"));
    }

    @Test
    void toLongPortInvalidFormat() {
        assertNull(mapper.toLongPort("SH600519"));
        assertNull(mapper.toLongPort("SH."));
        assertNull(mapper.toLongPort(".SH"));
        assertNull(mapper.toLongPort("SH.ABC"));
        assertNull(mapper.toLongPort("SH.123"));
    }

    @Test
    void fromLongPortSh() {
        assertEquals("SH.600519", mapper.fromLongPort("600519.SH"));
    }

    @Test
    void fromLongPortSz() {
        assertEquals("SZ.000001", mapper.fromLongPort("000001.SZ"));
    }

    @Test
    void fromLongPortBj() {
        assertEquals("BJ.430047", mapper.fromLongPort("430047.BJ"));
    }

    @Test
    void fromLongPortCaseInsensitive() {
        assertEquals("SH.600519", mapper.fromLongPort("600519.sh"));
        assertEquals("SH.600519", mapper.fromLongPort("  600519.SH  "));
    }

    @Test
    void fromLongPortInvalid() {
        assertNull(mapper.fromLongPort("600519.HK"));
        assertNull(mapper.fromLongPort("ABC.SH"));
        assertNull(mapper.fromLongPort("SH"));
        assertNull(mapper.fromLongPort("600519SH"));
    }

    @Test
    void roundTrip() {
        assertEquals("SH.600519", mapper.fromLongPort(mapper.toLongPort("SH.600519")));
        assertEquals("SZ.000001", mapper.fromLongPort(mapper.toLongPort("SZ.000001")));
        assertEquals("BJ.430047", mapper.fromLongPort(mapper.toLongPort("BJ.430047")));
    }
}
