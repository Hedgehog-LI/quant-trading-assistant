package com.quant.trade.marketdata.provider;

import com.quant.trade.marketdata.model.StockAliasDO;
import com.quant.trade.marketdata.model.StockBasicDO;

import java.util.List;

/**
 * 证券目录 Provider 只读抽象。职责仅限目录快照/增量拉取与标准化为
 * {@link StockBasicDO}/{@link StockAliasDO} 候选；不复用报价/K 线 {@code MarketDataProvider}，
 * 不接交易/账户/订单。实现必须只读、可审计、不打印或返回凭据。
 */
public interface SecurityDirectoryProvider {

    /** Provider 唯一标识（≤16 字符，匹配 market_data_sync_task.provider 列长）。 */
    String getProviderCode();

    /** 是否已启用（配置 enabled）。disabled 时由 DisabledSecurityDirectoryProvider 兜底。 */
    boolean isEnabled();

    /** 是否已配置可用（enabled 且来源就绪，如 CSV 文件存在）。未就绪时 fetch 返回可解释失败。 */
    boolean isConfigured();

    /**
     * 拉取并标准化证券目录快照/增量。
     *
     * @param mode FULL/INCREMENTAL
     * @return 标准化后的证券与别名候选；失败抛 {@link SecurityDirectoryProviderException}
     */
    DirectorySnapshot fetch(String mode);

    /** Provider 拉取的目录快照。 */
    record DirectorySnapshot(String providerCode, String mode, String sourceDescription,
                             java.time.LocalDateTime sourceFileTime,
                             List<StockBasicDO> stocks, List<SnapshotRow> rows,
                             long duplicateUnchanged) {}

    /** 单行：一只证券及其别名候选，保证 alias 与 stock 的归属关系。 */
    record SnapshotRow(StockBasicDO stock, List<StockAliasDO> aliases) {}
}
