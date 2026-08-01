package com.quant.trade.marketdata.provider.csv;

import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.SecurityDirectoryConstants;
import com.quant.trade.marketdata.model.StockAliasDO;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.provider.DirectorySnapshotIdentity;
import com.quant.trade.marketdata.provider.SecurityDirectoryProvider;
import com.quant.trade.marketdata.provider.SecurityDirectoryProvider.DirectorySnapshot;
import com.quant.trade.marketdata.provider.SecurityDirectoryProvider.SnapshotRow;
import com.quant.trade.marketdata.provider.SecurityDirectoryProviderException;
import com.quant.trade.marketdata.util.SecurityDirectoryIdentityCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 快照目录 provider。从本地可配置 CSV 路径读取快照，复用 {@link SecurityDirectoryCsvParser}
 * （D1 冻结口径）解析并标准化为 stock/alias 候选。来源、版本、文件时间和执行结果可审计。
 * <p>
 * 不做网络下载；provider disabled / 文件缺失 / 内容非法时应用仍可启动，D1 本地搜索可用。
 */
@Slf4j
@RequiredArgsConstructor
public class CsvSnapshotSecurityDirectoryProvider implements SecurityDirectoryProvider {

    private final boolean enabled;
    private final String providerCode;
    private final Path snapshotPath;
    private final SecurityDirectoryCsvParser csvParser;

    @Override
    public String getProviderCode() {
        return providerCode;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isConfigured() {
        return enabled && snapshotPath != null && Files.isReadable(snapshotPath);
    }

    @Override
    public DirectorySnapshot fetch(String mode) {
        if (!enabled) {
            throw new SecurityDirectoryProviderException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    SecurityDirectoryConstants.REASON_PROVIDER_DISABLED,
                    "证券目录同步 provider 未启用");
        }
        if (snapshotPath == null || !Files.exists(snapshotPath)) {
            throw new SecurityDirectoryProviderException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    SecurityDirectoryConstants.REASON_FILE_NOT_FOUND,
                    "证券目录快照文件未找到");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(snapshotPath);
        } catch (IOException exception) {
            log.warn("证券目录快照读取失败: {}", exception.getMessage());
            throw new SecurityDirectoryProviderException(ErrorCodeEnum.DAILY_BAR_CSV_PARSE_ERROR,
                    SecurityDirectoryConstants.REASON_PARSE_ERROR,
                    "证券目录快照文件读取失败");
        }
        SecurityDirectoryCsvParser.ParsedDirectoryBatch batch = csvParser.parse(bytes);
        List<StockBasicDO> stocks = new ArrayList<>();
        List<SnapshotRow> rows = new ArrayList<>();
        List<StockAliasDO> allAliases = new ArrayList<>();
        for (SecurityDirectoryCsvParser.DirectoryRow row : batch.rows()) {
            stocks.add(row.stock());
            List<StockAliasDO> rowAliases = new ArrayList<>();
            row.aliases().forEach(alias -> {
                StockAliasDO copy = StockAliasDO.builder()
                        .alias(alias.getAlias())
                        .normalizedAlias(alias.getNormalizedAlias())
                        .normalizedAliasKey(alias.getNormalizedAliasKey())
                        .aliasType(alias.getAliasType())
                        .language(alias.getLanguage())
                        .dataSource(alias.getDataSource())
                        .build();
                rowAliases.add(copy);
                allAliases.add(copy);
            });
            rows.add(new SnapshotRow(row.stock(), List.copyOf(rowAliases)));
        }
        String snapshotHash = SecurityDirectoryIdentityCalculator.computeSnapshotHash(
                providerCode, mode, stocks, allAliases);
        String snapshotId = SecurityDirectoryIdentityCalculator.snapshotIdFromHash(snapshotHash);
        DirectorySnapshotIdentity identity = new DirectorySnapshotIdentity(
                snapshotId, snapshotHash, providerCode + "@" + snapshotId);
        LocalDateTime fileTime = fileTimeOf(snapshotPath);
        return new DirectorySnapshot(providerCode, mode, identity.sourceDescription(), fileTime,
                List.copyOf(stocks), List.copyOf(rows), batch.duplicateUnchanged());
    }

    private static LocalDateTime fileTimeOf(Path path) {
        try {
            FileTime time = Files.getLastModifiedTime(path);
            return LocalDateTime.ofInstant(time.toInstant(), ZoneId.of("UTC"));
        } catch (IOException exception) {
            return null;
        }
    }
}
