package com.quant.trade.marketdata.poc;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * MR-0 PoC 入参边界（AC-03，AMD-001 冻结数值，Controller 与 Service 双层共用同一实现）：
 * <ul>
 *   <li>{@code analysisStart <= analysisEnd}（相等合法）；</li>
 *   <li>{@code warmupStart <= analysisStart}（相等合法；仅 IngestCommand 有 warmupStart）；</li>
 *   <li>{@code sampleSize ∈ [1, 500]}（含端点；0/负数/&gt;500 拒绝）；</li>
 *   <li>跨度上限 {@code analysisEnd − analysisStart <= 365 天}（365 合法，366+ 拒绝）。</li>
 * </ul>
 * 非法统一抛 {@link BusinessException}（{@link ErrorCodeEnum#VALIDATION_ERROR}）→ 400 envelope，
 * 禁止 500。GET /analyze、/report 只应用窗口顺序与跨度规则；POST /ingest 应用全部规则。
 * 失效场景：直调 service 绕过 controller 时由 service 入口二次拦截；参数被拒前不得触发任何
 * {@link PublicMarketDataClient} 交互或 mapper 读取。
 */
final class Mr0PocParamValidator {

    static final int SAMPLE_SIZE_MIN = 1;
    static final int SAMPLE_SIZE_MAX = 500;
    static final long MAX_ANALYSIS_SPAN_DAYS = 365L;

    private Mr0PocParamValidator() {
    }

    /** analyze/report 共用：分析窗口顺序 + 跨度上限（GET 无 warmup/sampleSize 参数）。 */
    static void validateAnalysisWindow(LocalDate analysisStart, LocalDate analysisEnd) {
        requireNonNull(analysisStart, "analysisStart");
        requireNonNull(analysisEnd, "analysisEnd");
        if (analysisStart.isAfter(analysisEnd)) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "analysisStart 不能晚于 analysisEnd");
        }
        long spanDays = ChronoUnit.DAYS.between(analysisStart, analysisEnd);
        if (spanDays > MAX_ANALYSIS_SPAN_DAYS) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    "分析窗口跨度不能超过 " + MAX_ANALYSIS_SPAN_DAYS + " 天（当前 " + spanDays + " 天）");
        }
    }

    /** sampleSize 边界：[1, 500] 含端点。 */
    static void validateSampleSize(int sampleSize) {
        if (sampleSize < SAMPLE_SIZE_MIN || sampleSize > SAMPLE_SIZE_MAX) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    "sampleSize 必须在 [" + SAMPLE_SIZE_MIN + ", " + SAMPLE_SIZE_MAX + "] 内（当前 " + sampleSize + "）");
        }
    }

    /** ingest 全量规则：分析窗口 + warmup 顺序 + sampleSize 边界。 */
    static void validateIngestCommand(LocalDate warmupStart, LocalDate analysisStart,
                                      LocalDate analysisEnd, int sampleSize) {
        validateAnalysisWindow(analysisStart, analysisEnd);
        requireNonNull(warmupStart, "warmupStart");
        if (warmupStart.isAfter(analysisStart)) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "warmupStart 不能晚于 analysisStart");
        }
        validateSampleSize(sampleSize);
    }

    private static void requireNonNull(LocalDate value, String field) {
        if (value == null) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, field + " 不能为空");
        }
    }
}
