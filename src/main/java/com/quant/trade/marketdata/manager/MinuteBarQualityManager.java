package com.quant.trade.marketdata.manager;

import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.model.StockMinuteBarDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分钟 K 线数据质量校验。
 * <p>
 * 规则（设计基线 MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN §9）：
 * 1. OHLC 合法：high &gt;= open/close/low，low &lt;= open/close/high。
 * 2. volume / amount 非负。
 * 3. bar 时间落在交易时段内（由 TradingSessionManager 配合）。
 * 4. 同一幂等键内容冲突产生 alert，不静默覆盖（由 service 层 upsert 配合）。
 * 5. qualityStatus 支持 VALID / SUSPECT / REJECTED。
 */
@Slf4j
@Component
public class MinuteBarQualityManager {

    /**
     * 校验单条分钟 K，返回质量状态。
     *
     * @return VALID / SUSPECT / REJECTED
     */
    public String validate(StockMinuteBarDO bar) {
        if (bar == null) {
            return WorkbenchConstants.QUALITY_REJECTED;
        }
        // 硬失败 → REJECTED
        if (!isValidOhlc(bar)) {
            return WorkbenchConstants.QUALITY_REJECTED;
        }
        if (hasNegativeVolumeOrAmount(bar)) {
            return WorkbenchConstants.QUALITY_REJECTED;
        }
        // 轻度可疑 → SUSPECT（如换手率为负但其他正常）
        if (bar.getTurnoverRate() != null && bar.getTurnoverRate().signum() < 0) {
            return WorkbenchConstants.QUALITY_SUSPECT;
        }
        return WorkbenchConstants.QUALITY_VALID;
    }

    /** OHLC 合法性：high >= open/close/low，low <= open/close/high。 */
    public boolean isValidOhlc(StockMinuteBarDO bar) {
        BigDecimal open = bar.getOpenPrice();
        BigDecimal high = bar.getHighPrice();
        BigDecimal low = bar.getLowPrice();
        BigDecimal close = bar.getClosePrice();
        if (open == null || high == null || low == null || close == null) {
            return false;
        }
        return high.compareTo(open) >= 0 && high.compareTo(close) >= 0 && high.compareTo(low) >= 0
                && low.compareTo(open) <= 0 && low.compareTo(close) <= 0;
    }

    /** volume / amount 非负。 */
    public boolean hasNegativeVolumeOrAmount(StockMinuteBarDO bar) {
        if (bar.getVolume() != null && bar.getVolume() < 0) {
            return true;
        }
        return bar.getAmount() != null && bar.getAmount().signum() < 0;
    }

    /**
     * 校验 bar 时间是否落在交易时段内。
     *
     * @param barStartTime    K 线开始时间
     * @param sessionWindows  交易时段窗口列表，每项 [startHHMM, endHHMM]
     * @param includeAuction  是否包含集合竞价窗口
     * @return true 表示在允许窗口内
     */
    public boolean isBarInSession(LocalDateTime barStartTime, List<int[]> sessionWindows, boolean includeAuction) {
        if (barStartTime == null || sessionWindows == null || sessionWindows.isEmpty()) {
            return false;
        }
        int hhmm = barStartTime.getHour() * 100 + barStartTime.getMinute();
        for (int[] window : sessionWindows) {
            if (window.length < 3) continue;
            boolean isAuction = window[2] == 1;
            if (isAuction && !includeAuction) continue;
            if (hhmm >= window[0] && hhmm < window[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断两条同幂等键数据是否内容冲突。
     *
     * @return true 表示内容不一致（冲突），false 表示一致（可跳过）
     */
    public boolean isContentConflict(StockMinuteBarDO existing, StockMinuteBarDO incoming) {
        return !safeEquals(existing.getOpenPrice(), incoming.getOpenPrice())
                || !safeEquals(existing.getHighPrice(), incoming.getHighPrice())
                || !safeEquals(existing.getLowPrice(), incoming.getLowPrice())
                || !safeEquals(existing.getClosePrice(), incoming.getClosePrice())
                || !safeEquals(existing.getVolume(), incoming.getVolume())
                || !safeEquals(existing.getAmount(), incoming.getAmount());
    }

    private boolean safeEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
