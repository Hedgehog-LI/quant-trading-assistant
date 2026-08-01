package com.quant.trade.marketdata.config;

import com.quant.trade.marketdata.constant.SecurityDirectoryConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 证券目录同步（D3）配置。所有项默认安全关闭：enabled=false、scheduler.enabled=false。
 * provider disabled / CSV 路径缺失 / 内容非法时应用仍可启动，D1 本地搜索可用。
 */
@ConfigurationProperties(prefix = "qta.market-data.security-directory")
public class SecurityDirectoryProperties {

    /** 是否启用证券目录同步 provider（disabled 时使用 DisabledSecurityDirectoryProvider 兜底）。 */
    private boolean enabled = false;

    /** provider code（默认 CSV 快照目录）。 */
    private String providerCode = SecurityDirectoryConstants.PROVIDER_CODE_CSV_SNAPSHOT_DIR;

    /** 本地 CSV 快照文件路径（provider enabled 时必填，缺失则 providerConfigured=false）。 */
    private String snapshotPath;

    /** 每日增量同步默认模式。 */
    private String dailyMode = SecurityDirectoryConstants.MODE_INCREMENTAL;

    /** 每周全量对账默认模式。 */
    private String weeklyMode = SecurityDirectoryConstants.MODE_FULL;

    /** 数量波动阈值（候选发布集行数相对上一成功目录的相对偏差）。 */
    private double rowCountSwingThreshold = SecurityDirectoryConstants.DEFAULT_ROW_COUNT_SWING_THRESHOLD;

    private final Scheduler scheduler = new Scheduler();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }

    public String getSnapshotPath() { return snapshotPath; }
    public void setSnapshotPath(String snapshotPath) { this.snapshotPath = snapshotPath; }

    public String getDailyMode() { return dailyMode; }
    public void setDailyMode(String dailyMode) { this.dailyMode = dailyMode; }

    public String getWeeklyMode() { return weeklyMode; }
    public void setWeeklyMode(String weeklyMode) { this.weeklyMode = weeklyMode; }

    public double getRowCountSwingThreshold() { return rowCountSwingThreshold; }
    public void setRowCountSwingThreshold(double rowCountSwingThreshold) {
        this.rowCountSwingThreshold = rowCountSwingThreshold;
    }

    public Scheduler getScheduler() { return scheduler; }

    /** 调度配置，默认关闭。 */
    public static class Scheduler {
        private boolean enabled = false;
        /** 每日增量同步 cron（Asia/Shanghai）。 */
        private String dailyCron = SecurityDirectoryConstants.DEFAULT_DAILY_CRON;
        /** 每周全量对账 cron（Asia/Shanghai）。 */
        private String weeklyCron = SecurityDirectoryConstants.DEFAULT_WEEKLY_CRON;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getDailyCron() { return dailyCron; }
        public void setDailyCron(String dailyCron) { this.dailyCron = dailyCron; }
        public String getWeeklyCron() { return weeklyCron; }
        public void setWeeklyCron(String weeklyCron) { this.weeklyCron = weeklyCron; }
    }
}
