package com.longport.quote;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Test-only minimal LongPort candlestick DTO. */
public class Candlestick {

    private final BigDecimal close;
    private final BigDecimal open;
    private final BigDecimal low;
    private final BigDecimal high;
    private final long volume;
    private final BigDecimal turnover;
    private final OffsetDateTime timestamp;

    public Candlestick(BigDecimal close, BigDecimal open, BigDecimal low, BigDecimal high, long volume,
                       BigDecimal turnover, OffsetDateTime timestamp) {
        this.close = close;
        this.open = open;
        this.low = low;
        this.high = high;
        this.volume = volume;
        this.turnover = turnover;
        this.timestamp = timestamp;
    }

    public BigDecimal getClose() {
        return close;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public long getVolume() {
        return volume;
    }

    public BigDecimal getTurnover() {
        return turnover;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }
}
