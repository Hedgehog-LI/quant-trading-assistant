package com.quant.trade.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.ZoneId;

/** LongPort 只读行情源配置，不记录、不打印密钥。 */
@ConfigurationProperties(prefix = "qta.market-data.longport")
public class LongPortProperties {

    /** 是否启用 LongPort provider。默认关闭，避免未配置环境误连外部。 */
    private boolean enabled = false;

    /** LongPort App Key，可通过环境变量 LONGPORT_APP_KEY 注入。 */
    private String appKey;

    /** LongPort App Secret，可通过环境变量 LONGPORT_APP_SECRET 注入。 */
    private String appSecret;

    /** LongPort Access Token，可通过环境变量 LONGPORT_ACCESS_TOKEN 注入。 */
    private String accessToken;

    /** SDK Future 等待超时时间，单位秒。 */
    private int timeoutSeconds = 10;

    /** 将 SDK 返回时间转换为本系统本地时间时使用的时区。 */
    private String quoteTimeZone = "Asia/Shanghai";

    /**
     * 可选的 HTTP API 基址覆盖。为空时使用 SDK 自带域名；非空时通过 {@code Config.httpUrl(...)} 覆盖。
     * 用于在 SDK 默认域名（如 openapi.longport.cn）不可达时切换到可用的同源服务域名
     * （如 https://openapi.longbridge.cn）。可通过环境变量 LONGPORT_HTTP_URL 注入。
     */
    private String httpUrl;

    /**
     * 可选的 Quote WebSocket 基址覆盖。为空时使用 SDK 自带域名；非空时通过
     * {@code Config.quoteWebsocketUrl(...)} 覆盖。SDK 默认 quote ws 域名（openapi-quote.longport.cn）
     * 与 http 域名同属一批，需与 {@link #httpUrl} 一起切换（如 wss://openapi-quote.longbridge.cn/v2）。
     * 可通过环境变量 LONGPORT_QUOTE_WEBSOCKET_URL 注入。
     */
    private String quoteWebsocketUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getQuoteTimeZone() {
        return quoteTimeZone;
    }

    public void setQuoteTimeZone(String quoteTimeZone) {
        this.quoteTimeZone = quoteTimeZone;
    }

    public ZoneId quoteZoneId() {
        return ZoneId.of(StringUtils.hasText(quoteTimeZone) ? quoteTimeZone : "Asia/Shanghai");
    }

    public String getHttpUrl() {
        return httpUrl;
    }

    public void setHttpUrl(String httpUrl) {
        this.httpUrl = httpUrl;
    }

    /** 是否显式配置了 HTTP API 基址覆盖。 */
    public boolean hasHttpUrl() {
        return hasText(httpUrl);
    }

    public String getQuoteWebsocketUrl() {
        return quoteWebsocketUrl;
    }

    public void setQuoteWebsocketUrl(String quoteWebsocketUrl) {
        this.quoteWebsocketUrl = quoteWebsocketUrl;
    }

    /** 是否显式配置了 Quote WebSocket 基址覆盖。 */
    public boolean hasQuoteWebsocketUrl() {
        return hasText(quoteWebsocketUrl);
    }

    /** 是否通过 Spring 配置显式传入了完整 legacy API key 凭据。 */
    public boolean hasExplicitCredentials() {
        return hasText(appKey) && hasText(appSecret) && hasText(accessToken);
    }

    /** 是否通过进程环境变量传入了完整 legacy API key 凭据。 */
    public boolean hasEnvironmentCredentials() {
        return hasText(System.getenv("LONGPORT_APP_KEY"))
                && hasText(System.getenv("LONGPORT_APP_SECRET"))
                && hasText(System.getenv("LONGPORT_ACCESS_TOKEN"));
    }

    /** 是否具备创建 LongPort legacy API key Config 的凭据条件。 */
    public boolean hasCredentials() {
        return hasExplicitCredentials() || hasEnvironmentCredentials();
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
