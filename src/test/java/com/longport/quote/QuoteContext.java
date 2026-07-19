package com.longport.quote;

import com.longport.Config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/** Test-only minimal LongPort QuoteContext stub for reflective adapter verification. */
public class QuoteContext implements AutoCloseable {

    public static QuoteContext create(Config config) {
        return new QuoteContext(config);
    }

    private final Config config;

    private QuoteContext(Config config) {
        this.config = config;
    }

    public CompletableFuture<String> getQuoteLevel() {
        return CompletableFuture.completedFuture("LV1");
    }

    public CompletableFuture<SecurityQuote[]> getQuote(String[] symbols) {
        SecurityQuote[] quotes = Arrays.stream(symbols)
                .map(symbol -> new SecurityQuote(
                        symbol,
                        new BigDecimal("1680.120000"),
                        new BigDecimal("1668.000000"),
                        new BigDecimal("1670.000000"),
                        new BigDecimal("1690.000000"),
                        new BigDecimal("1660.000000"),
                        OffsetDateTime.parse("2026-07-10T15:00:00+08:00"),
                        1200L,
                        new BigDecimal("2016144.000000"),
                        TradeStatus.Normal))
                .toArray(SecurityQuote[]::new);
        return CompletableFuture.completedFuture(quotes);
    }

    public CompletableFuture<SecurityStaticInfo[]> getStaticInfo(String[] symbols) {
        SecurityStaticInfo[] infos = Arrays.stream(symbols)
                .map(symbol -> new SecurityStaticInfo(symbol, "贵州茅台", "貴州茅台", "Kweichow Moutai",
                        "SSE", "CNY", 100))
                .toArray(SecurityStaticInfo[]::new);
        return CompletableFuture.completedFuture(infos);
    }

    public CompletableFuture<Candlestick[]> getHistoryCandlesticksByDate(String symbol, Period period,
                                                                         AdjustType adjustType, LocalDate start,
                                                                         LocalDate end,
                                                                         TradeSessions tradeSessions) {
        Candlestick[] bars = new Candlestick[] {
                new Candlestick(
                        new BigDecimal("1680.120000"),
                        new BigDecimal("1670.000000"),
                        new BigDecimal("1660.000000"),
                        new BigDecimal("1690.000000"),
                        1200L,
                        new BigDecimal("2016144.000000"),
                        start.atStartOfDay().atOffset(OffsetDateTime.parse("2026-07-10T00:00:00+08:00").getOffset()))
        };
        return CompletableFuture.completedFuture(bars);
    }

    public Config config() {
        return config;
    }

    @Override
    public void close() {
        // Test stub: no native resource.
    }
}
