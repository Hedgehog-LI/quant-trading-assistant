package com.quant.trade.marketdata.foundation.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.FoundationConstants;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetVersionMapper;
import com.quant.trade.marketdata.foundation.dao.MdfVersionManifestMapper;
import com.quant.trade.marketdata.foundation.model.MdfManifestDriftPairDO;
import com.quant.trade.marketdata.foundation.model.MdfVersionManifestDO;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 版本血缘与内容身份（Repair R1 §六）。
 *
 * - 行哈希冻结公式：sha256(concat_ws('|', symbol, date, open, high, low, close, volume, amount, source, adjust))，
 *   在 bar 写入事实后按版本口径计算并写入不可变 manifest（uk bar_id + 业务键双保险）。
 * - 内容哈希 = sha256 over 有序 "symbol|date|rowHash" 行流（ResultHandler 流式，不整表驻内存）。
 * - 漂移检测：按冻结公式对当前 stock_daily_bar 重算比对（含 bar 缺失/键漂移）；漂移版本标记 DRIFTED，
 *   阻断发布与"静默可复现"声明。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionLineageService {

    private final MdfVersionManifestMapper manifestMapper;
    private final MdfDatasetVersionMapper versionMapper;
    private final TransactionTemplate txRequiresNew;
    private final Clock marketDataClock;

    /** 行哈希（冻结公式；数值用 toPlainString 保持 decimal 刻度稳定）。 */
    public static String rowHash(StockDailyBarDO bar) {
        String canonical = String.join("|",
                bar.getCanonicalSymbol(),
                bar.getTradeDate().toString(),
                plain(bar.getOpenPrice()), plain(bar.getHighPrice()),
                plain(bar.getLowPrice()), plain(bar.getClosePrice()),
                bar.getVolume() == null ? "" : bar.getVolume().toString(),
                plain(bar.getAmount()),
                bar.getDataSource(), bar.getAdjustType());
        return sha256(canonical);
    }

    /**
     * 把版本口径（source+adjust+证券集+窗口）的 bar 事实纳入 manifest（幂等 ODKU）。
     * 回补分片与快照导入在事实落库后调用；bars 须携带 id（selectByFilter 已含）。
     */
    public void recordBars(long versionId, List<StockDailyBarDO> bars, String sourceType, long sourceId) {
        if (bars.isEmpty()) {
            return;
        }
        LocalDateTime includedAt = LocalDateTime.now(marketDataClock);
        List<MdfVersionManifestDO> rows = new ArrayList<>(bars.size());
        for (StockDailyBarDO bar : bars) {
            if (bar.getId() == null) {
                throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "manifest 记录要求 bar 已含主键 id");
            }
            rows.add(MdfVersionManifestDO.builder()
                    .datasetVersionId(versionId).barId(bar.getId())
                    .canonicalSymbol(bar.getCanonicalSymbol()).tradeDate(bar.getTradeDate())
                    .rowHash(rowHash(bar)).sourceType(sourceType).sourceId(sourceId)
                    .includedAt(includedAt).build());
        }
        txRequiresNew.executeWithoutResult(status -> {
            for (int from = 0; from < rows.size(); from += 500) {
                manifestMapper.insertBatch(rows.subList(from, Math.min(from + 500, rows.size())));
            }
        });
    }

    public long countManifest(long versionId) {
        return manifestMapper.countByVersion(versionId);
    }

    /** 内容哈希（有序行流；空 manifest 返回对空串的 sha256）。 */
    public String contentHash(long versionId) {
        MessageDigest digest = sha256Digest();
        manifestMapper.streamOrderedRowHashes(versionId,
                (ResultHandler<com.quant.trade.marketdata.foundation.model.MdfManifestRowHashDO>) context -> {
                    var row = context.getResultObject();
                    String line = row.getCanonicalSymbol() + "|" + row.getTradeDate() + "|" + row.getRowHash() + "\n";
                    digest.update(line.getBytes(StandardCharsets.UTF_8));
                });
        return HexFormat.of().formatHex(digest.digest());
    }

    /** 漂移行数（bar 缺失 / 键漂移 / 内容重算哈希不等）。 */
    public long countDrifted(long versionId) {
        DriftCounter counter = new DriftCounter();
        manifestMapper.streamDriftPairs(versionId,
                (ResultHandler<MdfManifestDriftPairDO>) context -> counter.accept(context.getResultObject()));
        return counter.drifted;
    }

    /** 冻结血缘（发布前）：内容哈希 + manifest 行数 + FROZEN。 */
    public void freeze(long versionId) {
        long rows = manifestMapper.countByVersion(versionId);
        String hash = contentHash(versionId);
        txRequiresNew.executeWithoutResult(status ->
                versionMapper.updateLineage(versionId, hash, rows, FoundationConstants.LINEAGE_FROZEN));
        log.info("版本血缘冻结: versionId={}, manifestRows={}, contentHash={}", versionId, rows, hash);
    }

    /** 标记漂移（质量检查检出后；发布门禁阻断）。 */
    public void markDrifted(long versionId) {
        txRequiresNew.executeWithoutResult(status ->
                versionMapper.updateLineage(versionId, null, null, FoundationConstants.LINEAGE_DRIFTED));
    }

    // ---------------------------------------------------------------- 内部

    private static final class DriftCounter {
        private long drifted;

        private void accept(MdfManifestDriftPairDO pair) {
            if (pair.getBarId() == null
                    || !pair.getCanonicalSymbol().equals(pair.getBarSymbol())
                    || !pair.getTradeDate().equals(pair.getBarDate())
                    || !pair.getFrozenHash().equals(currentRowHash(pair))) {
                drifted++;
            }
        }

        private static String currentRowHash(MdfManifestDriftPairDO pair) {
            String canonical = String.join("|",
                    pair.getBarSymbol(),
                    pair.getBarDate().toString(),
                    plain(pair.getOpenPrice()), plain(pair.getHighPrice()),
                    plain(pair.getLowPrice()), plain(pair.getClosePrice()),
                    pair.getVolume() == null ? "" : pair.getVolume().toString(),
                    plain(pair.getAmount()),
                    pair.getDataSource(), pair.getAdjustType());
            return sha256(canonical);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String sha256(String value) {
        return HexFormat.of().formatHex(sha256Digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "SHA-256 不可用");
        }
    }
}
