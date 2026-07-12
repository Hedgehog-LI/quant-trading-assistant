package com.longport;

/** Test-only minimal LongPort Config stub for reflective adapter verification. */
public class Config implements AutoCloseable {

    private final String appKey;
    private final String appSecret;
    private final String accessToken;
    private String httpUrl;
    private String quoteWebsocketUrl;

    private Config(String appKey, String appSecret, String accessToken) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.accessToken = accessToken;
    }

    public static Config fromApikey(String appKey, String appSecret, String accessToken) {
        return new Config(appKey, appSecret, accessToken);
    }

    public static Config fromApikeyEnv() {
        return new Config("env-app-key", "env-app-secret", "env-access-token");
    }

    /**
     * Mirrors the official SDK's chainable HTTP API base override. Official SDK
     * returns the same Config instance to allow fluent chaining; this stub does
     * the same so the reflective adapter's {@code Config.httpUrl(...)} call works.
     */
    public Config httpUrl(String httpUrl) {
        this.httpUrl = httpUrl;
        return this;
    }

    /**
     * Mirrors the official SDK's chainable quote websocket base override, paired
     * with {@link #httpUrl(String)} to switch both endpoints off the deprecated
     * default domains.
     */
    public Config quoteWebsocketUrl(String quoteWebsocketUrl) {
        this.quoteWebsocketUrl = quoteWebsocketUrl;
        return this;
    }

    public String appKey() {
        return appKey;
    }

    public String appSecret() {
        return appSecret;
    }

    public String accessToken() {
        return accessToken;
    }

    public String httpUrl() {
        return httpUrl;
    }

    public String quoteWebsocketUrl() {
        return quoteWebsocketUrl;
    }

    @Override
    public void close() {
        // Test stub: no native resource.
    }
}
