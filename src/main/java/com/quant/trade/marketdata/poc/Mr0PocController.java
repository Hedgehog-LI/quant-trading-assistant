package com.quant.trade.marketdata.poc;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.AnalysisCommand;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.AnalysisResult;
import com.quant.trade.marketdata.poc.Mr0PocIngestService.IngestCommand;
import com.quant.trade.marketdata.poc.Mr0PocIngestService.IngestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;

/**
 * MR-0 PoC 数据与语义 PoC REST 入口（AC-06）：/api/v1/market-research/mr0-poc。ingest=受控写入口
 * （AC-02 双门禁：qta.mr0-poc.ingest-enabled=true 且 active profiles 恰为 {"local"}，见
 * {@link Mr0PocIngestGate}；本地脚本经命令行开启）；analyze/report=只读库入口（不触碰
 * PublicMarketDataClient，零外联）。入参边界（AC-03，AMD-001）在 Controller 与 Service 双层校验：
 * analyze/report 应用窗口顺序+跨度上限，ingest 应用全部规则；畸形日期参数（如 2026-13-01）由本
 * controller 局部 handler 映射为 400 VALIDATION_ERROR（全局 handler 未覆盖类型不匹配，参照
 * MarketDataAssetController 的 controller 级 handler 先例，不改公共代码）。PoC 入口，不承诺
 * MR-1 稳定契约。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-research/mr0-poc")
@RequiredArgsConstructor
public class Mr0PocController {

    private static final MediaType MARKDOWN = MediaType.parseMediaType("text/markdown;charset=UTF-8");

    private final Mr0PocIngestService ingestService;
    private final Mr0PocAnalysisService analysisService;
    private final Mr0PocQualityService qualityService;
    private final Mr0PocIngestGate ingestGate;

    /**
     * 受控写入口：开关未启用或非恰 local profile 时 400 BUSINESS_RULE_VIOLATION（不泄露内部细节）；
     * 参数进入 service 前完成 AC-03 全量校验。
     */
    @PostMapping("/ingest")
    public ApiResponse<IngestResult> ingest(@RequestBody(required = false) IngestCommand command) {
        ingestGate.checkIngestAllowed();
        IngestCommand effective = command == null ? IngestCommand.builder().build() : command;
        Mr0PocParamValidator.validateIngestCommand(effective.getWarmupStart(), effective.getAnalysisStart(),
                effective.getAnalysisEnd(), effective.getSampleSize());
        return ApiResponse.ok(ingestService.ingest(effective));
    }

    /** 只读分析（默认窗口 2026-07-01..2026-07-31，D5 冻结口径）；入口应用窗口顺序+跨度上限。 */
    @GetMapping("/analyze")
    public ApiResponse<AnalysisResult> analyze(@RequestParam(required = false) LocalDate start,
                                               @RequestParam(required = false) LocalDate end) {
        AnalysisCommand command = command(start, end);
        Mr0PocParamValidator.validateAnalysisWindow(command.getAnalysisStart(), command.getAnalysisEnd());
        return ApiResponse.ok(analysisService.analyze(command));
    }

    /** 只读质量报告；format=markdown 返回文本，否则 ApiResponse 包装 JSON；入口应用窗口顺序+跨度上限。 */
    @GetMapping("/report")
    public ResponseEntity<?> report(@RequestParam(required = false) LocalDate start,
                                    @RequestParam(required = false) LocalDate end,
                                    @RequestParam(defaultValue = "json") String format) {
        AnalysisCommand command = command(start, end);
        Mr0PocParamValidator.validateAnalysisWindow(command.getAnalysisStart(), command.getAnalysisEnd());
        Mr0PocQualityService.QualityReport report = qualityService.generateReport(
                analysisService.analyze(command));
        if ("markdown".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MARKDOWN).body(report.toMarkdown());
        }
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    /** 畸形日期等类型不匹配 → 400 VALIDATION_ERROR envelope（非 500），不泄露内部细节。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(
                ApiResponse.fail(ErrorCodeEnum.VALIDATION_ERROR, "参数类型不合法: " + exception.getName()));
    }

    private AnalysisCommand command(LocalDate start, LocalDate end) {
        return AnalysisCommand.builder().analysisStart(start == null ? LocalDate.of(2026, 7, 1) : start)
                .analysisEnd(end == null ? LocalDate.of(2026, 7, 31) : end).build();
    }
}
