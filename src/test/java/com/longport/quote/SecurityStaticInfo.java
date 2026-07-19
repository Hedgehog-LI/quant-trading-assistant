package com.longport.quote;

/** Test-only minimal LongPort security static-info DTO. */
public class SecurityStaticInfo {
    private final String symbol;
    private final String nameCn;
    private final String nameHk;
    private final String nameEn;
    private final String exchange;
    private final String currency;
    private final int lotSize;

    public SecurityStaticInfo(String symbol, String nameCn, String nameHk, String nameEn,
                              String exchange, String currency, int lotSize) {
        this.symbol = symbol;
        this.nameCn = nameCn;
        this.nameHk = nameHk;
        this.nameEn = nameEn;
        this.exchange = exchange;
        this.currency = currency;
        this.lotSize = lotSize;
    }

    public String getSymbol() { return symbol; }
    public String getNameCn() { return nameCn; }
    public String getNameHk() { return nameHk; }
    public String getNameEn() { return nameEn; }
    public String getExchange() { return exchange; }
    public String getCurrency() { return currency; }
    public int getLotSize() { return lotSize; }
}
