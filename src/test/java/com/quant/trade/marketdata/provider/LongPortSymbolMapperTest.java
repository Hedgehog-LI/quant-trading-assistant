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
    void toLongPortHongKongStripsCanonicalLeadingZero() {
        assertEquals("2498.HK", mapper.toLongPort("HK.02498"));
        assertEquals("2498.HK", mapper.toLongPort("HK.2498"));
    }

    @Test
    void toLongPortUsSupportsClassShareSymbol() {
        assertEquals("AAPL.US", mapper.toLongPort("US.AAPL"));
        assertEquals("BRK.B.US", mapper.toLongPort("US.BRK.B"));
    }

    @Test
    void toLongPortCaseInsensitive() {
        assertEquals("600519.SH", mapper.toLongPort("sh.600519"));
        assertEquals("600519.SH", mapper.toLongPort("  sh.600519  "));
    }

    @Test
    void toLongPortInvalidMarket() {
        assertNull(mapper.toLongPort("XX.600519"));
    }

    @Test
    void toLongPortInvalidFormat() {
        assertNull(mapper.toLongPort("SH600519"));
        assertNull(mapper.toLongPort("SH."));
        assertNull(mapper.toLongPort(".SH"));
        assertNull(mapper.toLongPort("SH.ABC"));
        assertNull(mapper.toLongPort("SH.123"));
        assertNull(mapper.toLongPort("HK.ABC"));
        assertNull(mapper.toLongPort("US.AAPL/WS"));
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
    void fromLongPortHongKongPadsCanonicalCode() {
        assertEquals("HK.02498", mapper.fromLongPort("2498.HK"));
        assertEquals("HK.00700", mapper.fromLongPort("700.HK"));
    }

    @Test
    void fromLongPortUsUsesLastSeparatorForMarket() {
        assertEquals("US.AAPL", mapper.fromLongPort("AAPL.US"));
        assertEquals("US.BRK.B", mapper.fromLongPort("BRK.B.US"));
    }

    @Test
    void fromLongPortCaseInsensitive() {
        assertEquals("SH.600519", mapper.fromLongPort("600519.sh"));
        assertEquals("SH.600519", mapper.fromLongPort("  600519.SH  "));
    }

    @Test
    void fromLongPortInvalid() {
        assertNull(mapper.fromLongPort("ABC.HK"));
        assertNull(mapper.fromLongPort("ABC.SH"));
        assertNull(mapper.fromLongPort("SH"));
        assertNull(mapper.fromLongPort("600519SH"));
    }

    @Test
    void roundTrip() {
        assertEquals("SH.600519", mapper.fromLongPort(mapper.toLongPort("SH.600519")));
        assertEquals("SZ.000001", mapper.fromLongPort(mapper.toLongPort("SZ.000001")));
        assertEquals("BJ.430047", mapper.fromLongPort(mapper.toLongPort("BJ.430047")));
        assertEquals("HK.02498", mapper.fromLongPort(mapper.toLongPort("HK.02498")));
        assertEquals("US.AAPL", mapper.fromLongPort(mapper.toLongPort("US.AAPL")));
        assertEquals("US.BRK.B", mapper.fromLongPort(mapper.toLongPort("US.BRK.B")));
    }
}
