package com.quant.trade.marketdata.poc;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.AnalysisCommand;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.AnalysisResult;
import com.quant.trade.marketdata.poc.Mr0PocIngestService.IngestCommand;
import com.quant.trade.marketdata.poc.Mr0PocIngestService.IngestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * MR-0 数据与语义 PoC REST 入口（AC-06）：/api/v1/market-research/mr0-poc。ingest=受控写入口
 * （仅当 qta.mr0-poc.ingest-enabled=true，默认 false；本地脚本经命令行开启），analyze/report=只读库
 * 入口（不触碰 PublicMarketDataClient，零外联）。PoC 入口，不承诺 MR-1 稳定契约。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-research/mr0-poc")
@RequiredArgsConstructor
public class Mr0PocController {

    private static final MediaType MARKDOWN = MediaType.parseMediaType("text/markdown;charset=UTF-8");

    private final Mr0PocIngestService ingestService;
    private final Mr0PocAnalysisService analysisService;
    private final Mr0PocQualityService qualityService;

    @Value("${qta.mr0-poc.ingest-enabled:false}")
    private boolean ingestEnabled;

    /** 受控写入口：未启用时 400 BUSINESS_RULE_VIOLATION（不泄露内部细节）。 */
    @PostMapping("/ingest")
    public ApiResponse<IngestResult> ingest(@RequestBody(required = false) IngestCommand command) {
        if (!ingestEnabled) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "MR-0 PoC ingest 未启用");
        }
        return ApiResponse.ok(ingestService.ingest(command == null ? IngestCommand.builder().build() : command));
    }

    /** 只读分析（默认窗口 2026-07-01..2026-07-31，D5 冻结口径）。 */
    @GetMapping("/analyze")
    public ApiResponse<AnalysisResult> analyze(@RequestParam(required = false) LocalDate start,
                                               @RequestParam(required = false) LocalDate end) {
        return ApiResponse.ok(analysisService.analyze(command(start, end)));
    }

    /** 只读质量报告；format=markdown 返回文本，否则 ApiResponse 包装 JSON。 */
    @GetMapping("/report")
    public ResponseEntity<?> report(@RequestParam(required = false) LocalDate start,
                                    @RequestParam(required = false) LocalDate end,
                                    @RequestParam(defaultValue = "json") String format) {
        Mr0PocQualityService.QualityReport report = qualityService.generateReport(
                analysisService.analyze(command(start, end)));
        if ("markdown".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MARKDOWN).body(report.toMarkdown());
        }
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    private AnalysisCommand command(LocalDate start, LocalDate end) {
        return AnalysisCommand.builder().analysisStart(start == null ? LocalDate.of(2026, 7, 1) : start)
                .analysisEnd(end == null ? LocalDate.of(2026, 7, 31) : end).build();
    }
}
