package com.quant.trade.agent.controller;

import com.quant.trade.agent.service.AgentQueryService;
import com.quant.trade.agent.vo.TrustedAnswer;
import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.common.exception.ErrorCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Agent 只读 API 控制器。
 * <p>
 * 所有接口均为 GET，禁止写操作。
 * 需要 Bearer Token 认证，受 AgentSecurityConfig 保护。
 * 统一返回 TrustedAnswer 可信回答契约。
 * <p>
 * 审计与 requestId：审计由 AgentAuditFilter 在 Filter 链最外层统一记录，
 * requestId 由该 filter 生成并写入 request 属性 agentRequestId + X-Request-ID 响应头。
 * Controller 不再自行生成 requestId，也不再调用 auditService.record。
 * 错误响应使用真实 HTTP 状态码（500=INTERNAL_ERROR），body 为 success=false。
 */
@Slf4j
@RestController
@RequestMapping(ApiConstants.API_V1 + "/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentQueryService queryService;

    @GetMapping("/capabilities")
    @Operation(operationId = "qtaAgentCapabilities", summary = "Agent 能力查询")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TrustedAnswer>> capabilities(HttpServletRequest request) {
        return execute("qtaAgentCapabilities", "capabilities", request, () ->
            queryService.capabilities());
    }

    @GetMapping("/system/health")
    @Operation(operationId = "qtaAgentSystemHealth", summary = "系统健康摘要")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TrustedAnswer>> systemHealth(HttpServletRequest request) {
        return execute("qtaAgentSystemHealth", "system/health", request, () ->
            queryService.systemHealth());
    }

    @GetMapping("/trading/today")
    @Operation(operationId = "qtaAgentTodayOverview", summary = "今日待办概览")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TrustedAnswer>> todayOverview(
            @RequestParam(required = false) LocalDate date,
            HttpServletRequest request) {
        return execute("qtaAgentTodayOverview", "trading/today?date=" + date, request, () ->
            queryService.todayOverview(date));
    }

    @GetMapping("/portfolio/summary")
    @Operation(operationId = "qtaAgentPortfolioSummary", summary = "持仓摘要")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TrustedAnswer>> portfolioSummary(HttpServletRequest request) {
        return execute("qtaAgentPortfolioSummary", "portfolio/summary", request, () ->
            queryService.portfolioSummary());
    }

    @GetMapping("/market-data/collection-overview")
    @Operation(operationId = "qtaAgentCollectionOverview", summary = "行情采集概览")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TrustedAnswer>> collectionOverview(
            @RequestParam(required = false) String market,
            @RequestParam(required = false) LocalDate date,
            HttpServletRequest request) {
        return execute("qtaAgentCollectionOverview", "market-data/collection-overview?market=" + market + "&date=" + date, request, () ->
            queryService.collectionOverview(market, date));
    }

    @GetMapping("/market-data/failures")
    @Operation(operationId = "qtaAgentCollectionFailures", summary = "采集失败查询")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TrustedAnswer>> collectionFailures(
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        return execute("qtaAgentCollectionFailures", "market-data/failures?market=" + market + "&since=" + since + "&limit=" + limit, request, () ->
            queryService.collectionFailures(market, since, limit));
    }

    @GetMapping("/market-data/alerts")
    @Operation(operationId = "qtaAgentDataQualityAlerts", summary = "数据质量提醒")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TrustedAnswer>> dataQualityAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        return execute("qtaAgentDataQualityAlerts", "market-data/alerts?status=" + status + "&since=" + since + "&limit=" + limit, request, () ->
            queryService.dataQualityAlerts(status, since, limit));
    }

    @GetMapping("/market-sectors/ranking-summary")
    @Operation(operationId = "qtaAgentSectorRankingSummary", summary = "板块排行摘要")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TrustedAnswer>> sectorRankingSummary(
            @RequestParam(defaultValue = "CN") String market,
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        return execute("qtaAgentSectorRankingSummary", "market-sectors/ranking-summary?market=" + market + "&limit=" + limit, request, () ->
            queryService.sectorRankingSummary(market, limit));
    }

    @GetMapping("/securities/{canonicalSymbol}/market-summary")
    @Operation(operationId = "qtaAgentSecurityMarketSummary", summary = "单证券行情摘要")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<TrustedAnswer>> securityMarketSummary(
            @PathVariable String canonicalSymbol,
            HttpServletRequest request) {
        return execute("qtaAgentSecurityMarketSummary",
            "securities/" + canonicalSymbol + "/market-summary", request, () ->
            queryService.securityMarketSummary(canonicalSymbol));
    }

    // ==================== 内部执行 ====================
    // 审计由 AgentAuditFilter 统一记录，这里只负责业务执行与响应封装。

    private ResponseEntity<ApiResponse<TrustedAnswer>> execute(String operationCode, String pathSummary,
                                                HttpServletRequest request,
                                                java.util.function.Supplier<TrustedAnswer> action) {
        String requestId = resolveRequestId(request);

        TrustedAnswer answer;
        int httpStatus;
        boolean isFailure;

        try {
            answer = action.get();
            // TrustedAnswer with data=null and non-empty warnings indicates a handled failure
            boolean isHandledFailure = answer.data() == null
                && answer.warnings() != null && !answer.warnings().isEmpty();
            if (isHandledFailure) {
                httpStatus = 500;
                isFailure = true;
                log.warn("Agent operation {} returned handled failure for requestId={}: warnings={}",
                    operationCode, requestId, answer.warnings());
            } else {
                httpStatus = 200;
                isFailure = false;
            }
        } catch (Exception e) {
            // 不泄露内部异常细节：response body 只包含通用错误码与消息。
            log.error("Agent operation {} threw for requestId={}", operationCode, requestId, e);
            answer = new TrustedAnswer(
                "内部错误",
                OffsetDateTime.now(ZoneOffset.ofHours(8)),
                null,
                TrustedAnswer.UNKNOWN,
                List.of(),
                List.of("内部错误"),
                null
            );
            httpStatus = 500;
            isFailure = true;
        }

        // 错误响应：HTTP 500 + success=false + code != SUCCESS
        if (isFailure) {
            return ResponseEntity.status(httpStatus).body(
                ApiResponse.fail(ErrorCodeEnum.INTERNAL_ERROR, "内部错误，请联系管理员，requestId=" + requestId));
        }
        return ResponseEntity.status(httpStatus).body(ApiResponse.ok(answer));
    }

    /** 复用 AgentAuditFilter 的 requestId，保证 header / body / audit 三者一致。 */
    private String resolveRequestId(HttpServletRequest request) {
        Object attr = request.getAttribute("agentRequestId");
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
