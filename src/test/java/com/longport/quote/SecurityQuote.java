package com.longport.quote;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Test-only minimal LongPort security quote DTO. */
public class SecurityQuote {

    private final String symbol;
    private final BigDecimal lastDone;
    private final BigDecimal prevClose;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final OffsetDateTime timestamp;
    private final long volume;
    private final BigDecimal turnover;
    private final TradeStatus tradeStatus;

    public SecurityQuote(String symbol, BigDecimal lastDone, BigDecimal prevClose, BigDecimal open,
                         BigDecimal high, BigDecimal low, OffsetDateTime timestamp, long volume,
                         BigDecimal turnover, TradeStatus tradeStatus) {
        this.symbol = symbol;
        this.lastDone = lastDone;
        this.prevClose = prevClose;
        this.open = open;
        this.high = high;
        this.low = low;
        this.timestamp = timestamp;
        this.volume = volume;
        this.turnover = turnover;
        this.tradeStatus = tradeStatus;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getLastDone() {
        return lastDone;
    }

    public BigDecimal getPrevClose() {
        return prevClose;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public long getVolume() {
        return volume;
    }

    public BigDecimal getTurnover() {
        return turnover;
    }

    public TradeStatus getTradeStatus() {
        return tradeStatus;
    }
}
