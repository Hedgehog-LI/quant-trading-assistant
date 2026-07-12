package com.quant.trade.marketdata.provider.longport;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.config.LongPortProperties;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderHealthStatus;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 基于反射调用 LongPort 官方 Java SDK。
 * <p>
 * 当前官方文档给出 Maven 坐标，但 Maven Central 暂不可拉取。使用反射可以让项目在 SDK
 * 未安装时仍可编译、启动，并在 SDK 进入运行时 classpath 后真实调用只读行情接口。
 */
@Slf4j
public class ReflectiveLongPortQuoteClient implements LongPortQuoteClient {

    private static final String CLASS_CONFIG = "com.longport.Config";
    private static final String CLASS_QUOTE_CONTEXT = "com.longport.quote.QuoteContext";
    private static final String CLASS_PERIOD = "com.longport.quote.Period";
    private static final String CLASS_ADJUST_TYPE = "com.longport.quote.AdjustType";
    private static final String CLASS_TRADE_SESSIONS = "com.longport.quote.TradeSessions";
    private static final String SDK_PERIOD_DAY = "Day";
    private static final String SDK_TRADE_SESSIONS_ALL = "All";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 300;

    private final LongPortProperties properties;
    private final ZoneId quoteZoneId;
    private final ClassLoader sdkClassLoader;
    private volatile LocalDateTime lastSuccessAt;
    private volatile String lastError;

    public ReflectiveLongPortQuoteClient(LongPortProperties properties) {
        this(properties, Thread.currentThread().getContextClassLoader());
    }

    public ReflectiveLongPortQuoteClient(LongPortProperties properties, ClassLoader sdkClassLoader) {
        this.properties = properties;
        this.quoteZoneId = properties.quoteZoneId();
        this.sdkClassLoader = sdkClassLoader == null
                ? ReflectiveLongPortQuoteClient.class.getClassLoader()
                : sdkClassLoader;
    }

    @Override
    public boolean isSdkAvailable() {
        try {
            loadClass(CLASS_CONFIG);
            loadClass(CLASS_QUOTE_CONTEXT);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            lastError = MarketDataConstants.LONGPORT_SDK_MISSING_MESSAGE;
            return false;
        }
    }

    @Override
    public boolean hasCredentials() {
        return properties.hasCredentials();
    }

    @Override
    public String unavailableReason() {
        if (!isSdkAvailable()) {
            return MarketDataConstants.LONGPORT_SDK_MISSING_MESSAGE;
        }
        if (!hasCredentials()) {
            return MarketDataConstants.LONGPORT_CREDENTIALS_MISSING_MESSAGE;
        }
        return lastError;
    }

    @Override
    public ProviderHealthStatus healthCheck() {
        if (!isSdkAvailable()) {
            return new ProviderHealthStatus(false, false,
                    MarketDataConstants.LONGPORT_SDK_MISSING_MESSAGE, lastSuccessAt);
        }
        if (!hasCredentials()) {
            return new ProviderHealthStatus(false, false,
                    MarketDataConstants.LONGPORT_CREDENTIALS_MISSING_MESSAGE, lastSuccessAt);
        }

        try (SdkSession session = openSession()) {
            Method getQuoteLevel = session.quoteContext().getClass().getMethod("getQuoteLevel");
            waitFuture(getQuoteLevel.invoke(session.quoteContext()));
            markSuccess();
            return new ProviderHealthStatus(true, true, null, lastSuccessAt);
        } catch (Exception e) {
            String message = toSafeMessage(e);
            lastError = message;
            return new ProviderHealthStatus(true, false, message, lastSuccessAt);
        }
    }

    @Override
    public List<LongPortQuote> getLatestQuotes(List<String> longPortSymbols) {
        ensureReady();
        if (longPortSymbols == null || longPortSymbols.isEmpty()) {
            return List.of();
        }

        try (SdkSession session = openSession()) {
            Method getQuote = session.quoteContext().getClass().getMethod("getQuote", String[].class);
            Object result = waitFuture(getQuote.invoke(session.quoteContext(),
                    (Object) longPortSymbols.toArray(String[]::new)));
            List<LongPortQuote> quotes = new ArrayList<>();
            for (Object item : (Object[]) result) {
                quotes.add(toLongPortQuote(item));
            }
            markSuccess();
            return quotes;
        } catch (Exception e) {
            throw providerException("获取 LongPort 最新行情失败", e);
        }
    }

    @Override
    public List<LongPortDailyBar> getDailyBars(String longPortSymbol, LocalDate startDate, LocalDate endDate,
                                               String sdkAdjustTypeName, String systemAdjustType) {
        ensureReady();
        try (SdkSession session = openSession()) {
            Class<?> periodClass = loadClass(CLASS_PERIOD);
            Class<?> adjustTypeClass = loadClass(CLASS_ADJUST_TYPE);
            Class<?> tradeSessionsClass = loadClass(CLASS_TRADE_SESSIONS);
            Method historyMethod = session.quoteContext().getClass().getMethod(
                    "getHistoryCandlesticksByDate",
                    String.class, periodClass, adjustTypeClass, LocalDate.class, LocalDate.class, tradeSessionsClass);

            Object result = waitFuture(historyMethod.invoke(session.quoteContext(),
                    longPortSymbol,
                    enumValue(periodClass, SDK_PERIOD_DAY),
                    enumValue(adjustTypeClass, sdkAdjustTypeName),
                    startDate,
                    endDate,
                    enumValue(tradeSessionsClass, SDK_TRADE_SESSIONS_ALL)));

            List<LongPortDailyBar> bars = new ArrayList<>();
            for (Object item : (Object[]) result) {
                bars.add(toLongPortDailyBar(longPortSymbol, systemAdjustType, item));
            }
            markSuccess();
            return bars;
        } catch (Exception e) {
            throw providerException("获取 LongPort 历史日 K 失败", e);
        }
    }

    private SdkSession openSession() throws Exception {
        Class<?> configClass = loadClass(CLASS_CONFIG);
        Object config = createConfig(configClass);
        try {
            Class<?> quoteContextClass = loadClass(CLASS_QUOTE_CONTEXT);
            Object quoteContext = quoteContextClass.getMethod("create", configClass).invoke(null, config);
            return new SdkSession(config, quoteContext);
        } catch (Exception e) {
            closeQuietly(config);
            throw e;
        }
    }

    private Object createConfig(Class<?> configClass) throws Exception {
        Object config;
        if (properties.hasExplicitCredentials()) {
            config = configClass.getMethod("fromApikey", String.class, String.class, String.class)
                    .invoke(null, properties.getAppKey(), properties.getAppSecret(), properties.getAccessToken());
        } else {
            config = configClass.getMethod("fromApikeyEnv").invoke(null);
        }
        if (properties.hasHttpUrl()) {
            config = configClass.getMethod("httpUrl", String.class).invoke(config, properties.getHttpUrl());
        }
        if (properties.hasQuoteWebsocketUrl()) {
            config = configClass.getMethod("quoteWebsocketUrl", String.class)
                    .invoke(config, properties.getQuoteWebsocketUrl());
        }
        return config;
    }

    private void ensureReady() {
        if (!isSdkAvailable()) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    MarketDataConstants.LONGPORT_SDK_MISSING_MESSAGE);
        }
        if (!hasCredentials()) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    MarketDataConstants.LONGPORT_CREDENTIALS_MISSING_MESSAGE);
        }
    }

    private LongPortQuote toLongPortQuote(Object item) throws ReflectiveOperationException {
        String symbol = (String) invokeGetter(item, "getSymbol");
        OffsetDateTime timestamp = (OffsetDateTime) invokeGetter(item, "getTimestamp");
        Object tradeStatus = invokeGetter(item, "getTradeStatus");
        return new LongPortQuote(
                symbol,
                toLocalDateTime(timestamp),
                (BigDecimal) invokeGetter(item, "getLastDone"),
                (BigDecimal) invokeGetter(item, "getOpen"),
                (BigDecimal) invokeGetter(item, "getHigh"),
                (BigDecimal) invokeGetter(item, "getLow"),
                (BigDecimal) invokeGetter(item, "getPrevClose"),
                (Long) invokeGetter(item, "getVolume"),
                (BigDecimal) invokeGetter(item, "getTurnover"),
                tradeStatus == null ? null : tradeStatus.toString());
    }

    private LongPortDailyBar toLongPortDailyBar(String longPortSymbol, String systemAdjustType, Object item)
            throws ReflectiveOperationException {
        OffsetDateTime timestamp = (OffsetDateTime) invokeGetter(item, "getTimestamp");
        if (timestamp == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "LongPort 日 K 返回 timestamp 为空，无法确定交易日");
        }
        return new LongPortDailyBar(
                longPortSymbol,
                timestamp.toInstant().atZone(quoteZoneId).toLocalDate(),
                systemAdjustType,
                (BigDecimal) invokeGetter(item, "getOpen"),
                (BigDecimal) invokeGetter(item, "getHigh"),
                (BigDecimal) invokeGetter(item, "getLow"),
                (BigDecimal) invokeGetter(item, "getClose"),
                (Long) invokeGetter(item, "getVolume"),
                (BigDecimal) invokeGetter(item, "getTurnover"));
    }

    private Object invokeGetter(Object target, String methodName) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }
        return timestamp.toInstant().atZone(quoteZoneId).toLocalDateTime();
    }

    private Object waitFuture(Object futureObject) throws Exception {
        if (!(futureObject instanceof CompletableFuture<?> future)) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR,
                    "LongPort SDK 返回值不是 CompletableFuture");
        }
        try {
            return future.get(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "LongPort SDK 调用超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "LongPort SDK 调用被中断");
        }
    }

    private BusinessException providerException(String action, Exception e) {
        String message = action + ": " + toSafeMessage(e);
        lastError = message;
        log.warn(message);
        if (e instanceof BusinessException businessException) {
            return businessException;
        }
        return new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, message);
    }

    private void markSuccess() {
        lastSuccessAt = LocalDateTime.now();
        lastError = null;
    }

    private String toSafeMessage(Throwable throwable) {
        Throwable root = unwrap(throwable);
        String message = root.getMessage();
        String safe = root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        safe = maskCredential(safe, properties.getAppKey());
        safe = maskCredential(safe, properties.getAppSecret());
        safe = maskCredential(safe, properties.getAccessToken());
        if (safe.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return safe.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }
        return safe;
    }

    private String maskCredential(String message, String credential) {
        if (credential == null || credential.isBlank()) {
            return message;
        }
        return message.replace(credential, "***");
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException || current.getCause() != null
                && current.getClass().getName().contains("ExecutionException")) {
            Throwable cause = current instanceof InvocationTargetException invocation
                    ? invocation.getTargetException()
                    : current.getCause();
            if (cause == null) {
                return current;
            }
            current = cause;
        }
        return current;
    }

    private Class<?> loadClass(String className) throws ClassNotFoundException {
        return Class.forName(className, false, sdkClassLoader);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object enumValue(Class<?> enumClass, String constantName) {
        return Enum.valueOf((Class<Enum>) enumClass.asSubclass(Enum.class), constantName);
    }

    private void closeQuietly(Object value) {
        if (value instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("Close LongPort SDK resource failed: {}", e.getMessage());
            }
        }
    }

    private class SdkSession implements AutoCloseable {
        private final Object config;
        private final Object quoteContext;

        private SdkSession(Object config, Object quoteContext) {
            this.config = config;
            this.quoteContext = quoteContext;
        }

        private Object quoteContext() {
            return quoteContext;
        }

        @Override
        public void close() {
            closeQuietly(quoteContext);
            closeQuietly(config);
        }
    }
}
