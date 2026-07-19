package com.quant.trade.marketdata.service;

import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.MarketDataAlertMapper;
import com.quant.trade.marketdata.dao.MarketDataWatermarkMapper;
import com.quant.trade.marketdata.dao.StockMinuteBarMapper;
import com.quant.trade.marketdata.dto.MinuteBarUpsertDTO;
import com.quant.trade.marketdata.manager.MinuteBarQualityManager;
import com.quant.trade.marketdata.manager.TradingSessionManager;
import com.quant.trade.marketdata.model.MarketDataAlertDO;
import com.quant.trade.marketdata.model.MarketDataWatermarkDO;
import com.quant.trade.marketdata.model.StockMinuteBarDO;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 单条分钟 K 质量校验、幂等写入、提醒和水位更新。每次调用使用短事务。 */
@Service
@RequiredArgsConstructor
public class MinuteBarIngestService {
    private final StockMinuteBarMapper minuteBarMapper;
    private final MarketDataWatermarkMapper watermarkMapper;
    private final MarketDataAlertMapper alertMapper;
    private final MinuteBarQualityManager qualityManager;
    private final TradingSessionManager tradingSessionManager;
    private final Clock marketDataClock;

    @Transactional
    public Result upsert(MinuteBarUpsertDTO dto) {
        StockMinuteBarDO bar = toDO(dto);
        String quality = qualityManager.validate(bar);
        bar.setQualityStatus(quality);
        if (WorkbenchConstants.QUALITY_REJECTED.equals(quality)) {
            createAlert(bar, "分钟K质量校验失败(REJECTED)");
            return Result.rejected();
        }
        String marketCode = marketCode(bar.getCanonicalSymbol());
        if (!tradingSessionManager.isTradingDay(marketCode, bar.getTradeDate())) {
            bar.setQualityStatus(WorkbenchConstants.QUALITY_REJECTED);
            createAlert(bar, "分钟K落库失败: 非交易日 " + bar.getTradeDate());
            return Result.rejected();
        }
        List<int[]> windows = tradingSessionManager.getSessionWindows(marketCode, false);
        if (!qualityManager.isBarInSession(bar.getBarStartTime(), windows, false)) {
            bar.setQualityStatus(WorkbenchConstants.QUALITY_SUSPECT);
            createAlert(bar, "分钟K时段校验失败，标记 SUSPECT");
        }
        StockMinuteBarDO existing = minuteBarMapper.selectByUniqueKey(bar.getCanonicalSymbol(),
                bar.getBarStartTime(), bar.getIntervalType(), bar.getAdjustType(), bar.getDataSource());
        if (existing != null) {
            if (qualityManager.isContentConflict(existing, bar)) {
                createAlert(bar, "分钟K幂等键内容冲突，保留旧数据不覆盖");
                return Result.conflict();
            }
            touchWatermark(bar, 0L);
            return Result.skipped();
        }
        minuteBarMapper.insert(bar);
        touchWatermark(bar, 1L);
        return new Result("INSERTED", bar.getQualityStatus());
    }

    private void touchWatermark(StockMinuteBarDO bar, long insertedDelta) {
        MarketDataWatermarkDO existing = watermarkMapper.selectByUniqueKey(bar.getCanonicalSymbol(),
                bar.getDataSource(), bar.getIntervalType(), bar.getAdjustType());
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        if (existing == null) {
            watermarkMapper.insert(MarketDataWatermarkDO.builder()
                    .canonicalSymbol(bar.getCanonicalSymbol()).dataSource(bar.getDataSource())
                    .intervalType(bar.getIntervalType()).adjustType(bar.getAdjustType())
                    .lastSuccessTime(now).lastTradeDate(bar.getTradeDate())
                    .lastBarTime(bar.getBarStartTime()).totalRows(insertedDelta).build());
            return;
        }
        existing.setLastSuccessTime(now);
        if (existing.getLastBarTime() == null || bar.getBarStartTime().isAfter(existing.getLastBarTime())) {
            existing.setLastTradeDate(bar.getTradeDate());
            existing.setLastBarTime(bar.getBarStartTime());
        }
        existing.setTotalRows((existing.getTotalRows() == null ? 0L : existing.getTotalRows()) + insertedDelta);
        watermarkMapper.updateByUniqueKey(existing);
    }

    private void createAlert(StockMinuteBarDO bar, String message) {
        alertMapper.insert(MarketDataAlertDO.builder().alertType(WorkbenchConstants.ALERT_QUALITY_CONFLICT)
                .severity("WARN").canonicalSymbol(bar.getCanonicalSymbol()).provider(bar.getDataSource())
                .message(message + " symbol=" + bar.getCanonicalSymbol() + " time=" + bar.getBarStartTime()
                        + " interval=" + bar.getIntervalType()).resolved(false).build());
    }

    private StockMinuteBarDO toDO(MinuteBarUpsertDTO dto) {
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        return StockMinuteBarDO.builder()
                .canonicalSymbol(CanonicalSymbolUtils.normalize(dto.getCanonicalSymbol()))
                .tradeDate(dto.getBarStartTime().toLocalDate()).barStartTime(dto.getBarStartTime())
                .barEndTime(dto.getBarEndTime()).intervalType(dto.getIntervalType())
                .sessionType(dto.getSessionType() == null ? WorkbenchConstants.SESSION_REGULAR : dto.getSessionType())
                .openPrice(dto.getOpenPrice()).highPrice(dto.getHighPrice()).lowPrice(dto.getLowPrice())
                .closePrice(dto.getClosePrice()).volume(dto.getVolume()).amount(dto.getAmount())
                .turnoverRate(dto.getTurnoverRate()).adjustType(dto.getAdjustType()).dataSource(dto.getDataSource())
                .fetchedAt(now).qualityStatus(WorkbenchConstants.QUALITY_VALID).build();
    }

    private String marketCode(String canonicalSymbol) {
        if (canonicalSymbol.startsWith("SH.") || canonicalSymbol.startsWith("SZ.") || canonicalSymbol.startsWith("BJ.")) {
            return WorkbenchConstants.MARKET_CN_A;
        }
        if (canonicalSymbol.startsWith("HK.")) return "HK";
        if (canonicalSymbol.startsWith("US.")) return "US";
        return WorkbenchConstants.MARKET_CN_A;
    }

    public record Result(String result, String qualityStatus) {
        public static Result skipped() { return new Result("SKIPPED", "VALID"); }
        public static Result conflict() { return new Result("CONFLICT", "SUSPECT"); }
        public static Result rejected() { return new Result("REJECTED", "REJECTED"); }
    }
}
