package com.quant.trade.marketdata.foundation.provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 历史日 K 回补 Provider 接口（ADR-0015）。
 * 实现必须：单位统一为价格元 / volume 股 / amount 元 / 换手率小数；抛出的业务异常区分
 * 可重试网络错误与不可重试权限错误（401/403）。
 */
public interface HistoricalBarProvider {

    /** Provider 代码（与 mdf_dataset.provider_code 对齐，如 TENCENT_PUBLIC / IMPORT_CSV_*）。 */
    String providerCode();

    /** 支持的复权口径（首期公共源仅 NONE）。 */
    String supportedAdjustType();

    /**
     * 拉取单标的日 K（含边界）。窗口内无数据返回空列表（由调用方决定跳过/失败语义）。
     */
    List<ProviderDailyBar> getDailyBars(String canonicalSymbol, LocalDate start, LocalDate end);

    /**
     * 单次请求安全日期窗口（自然日）。Provider 必须保证窗口内交易日条数不超过其单次返回上限
     * （R1：腾讯公共源单次约 640 条 → 365 自然日 ≈ 245 交易日，二维分片按此切窗防截断）。
     */
    int safeRequestWindowDays();
}
