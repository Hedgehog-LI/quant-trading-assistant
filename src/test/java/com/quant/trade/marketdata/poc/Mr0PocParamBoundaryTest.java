package com.quant.trade.marketdata.poc;

import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.common.exception.GlobalExceptionHandler;
import com.quant.trade.marketdata.manager.StockBasicRegistrationManager;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.AnalysisCommand;
import com.quant.trade.marketdata.poc.Mr0PocIngestService.IngestCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-03 聚焦测试（TEST-04）：入参与资源边界（AMD-001 冻结数值）。
 * analysisStart&lt;=analysisEnd、warmupStart&lt;=analysisStart、sampleSize∈[1,500]、跨度
 * analysisEnd−analysisStart&lt;=365 天（相差 365 天=含端点 366 个日历日，放行；相差 366 天拒绝）。
 * Controller 层（standalone MockMvc + 真实 GlobalExceptionHandler 验证 400 envelope）与
 * Service 直调两条路径均覆盖；畸形日期（2026-13-01）经 controller 局部 type-mismatch handler
 * 返回 400 VALIDATION_ERROR 非 500；非法参数时 PublicMarketDataClient 零交互。
 */
class Mr0PocParamBoundaryTest {

    private static final String INGEST_URL = ApiConstants.API_V1 + "/market-research/mr0-poc/ingest";
    private static final String ANALYZE_URL = ApiConstants.API_V1 + "/market-research/mr0-poc/analyze";
    private static final String REPORT_URL = ApiConstants.API_V1 + "/market-research/mr0-poc/report";
    private static final LocalDate START = LocalDate.of(2026, 7, 1);
    private static final LocalDate END = LocalDate.of(2026, 7, 31);

    private final PublicMarketDataClient client = mock(PublicMarketDataClient.class);
    private final Mr0PocMapper mr0PocMapper = mock(Mr0PocMapper.class);
    private final StockBasicRegistrationManager registrationManager = mock(StockBasicRegistrationManager.class);
    private final Mr0PocAnalysisMapper analysisMapper = mock(Mr0PocAnalysisMapper.class);

    /** 门禁固定放行（恰 local + 开关 true），本测试类专注 AC-03 入参边界。 */
    private MockMvc mockMvc() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(Mr0PocIngestGate.LOCAL_PROFILE);
        Mr0PocIngestGate gate = new Mr0PocIngestGate(environment, true);
        Mr0PocController controller = new Mr0PocController(
                new Mr0PocIngestService(client, mr0PocMapper, registrationManager, gate),
                new Mr0PocAnalysisService(analysisMapper),
                new Mr0PocQualityService(analysisMapper),
                gate);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Mr0PocIngestService ingestService() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(Mr0PocIngestGate.LOCAL_PROFILE);
        return new Mr0PocIngestService(client, mr0PocMapper, registrationManager,
                new Mr0PocIngestGate(environment, true));
    }

    @BeforeEach
    void stubEmptyPublicSource() {
        when(client.fetchUniversePage(anyInt(), anyInt())).thenReturn(List.of());
        when(client.fetchIndustryCatalog()).thenReturn(Map.of());
        when(client.fetchDailyBars(anyString(), any(), any())).thenReturn(List.of());
        when(client.fetchMoneyFlow(anyString())).thenReturn(List.of());
    }

    // ==================== Controller 层：GET analyze/report 窗口规则 ====================

    @Test
    void analyzeAndReportRejectStartAfterEnd() throws Exception {
        mockMvc().perform(get(ANALYZE_URL).param("start", "2026-07-31").param("end", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.VALIDATION_ERROR.getCode()));
        mockMvc().perform(get(REPORT_URL).param("start", "2026-07-31").param("end", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.VALIDATION_ERROR.getCode()));
        verifyNoInteractions(analysisMapper, client, mr0PocMapper);
    }

    @Test
    void analyzeAndReportRejectSpanBeyond365Days() throws Exception {
        // 相差 366 天（2026-01-01..2027-01-02）拒绝；相差 365 天（2026-01-01..2026-12-31，含端点 366 个日历日）合法
        mockMvc().perform(get(ANALYZE_URL).param("start", "2026-01-01").param("end", "2027-01-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.VALIDATION_ERROR.getCode()));
        mockMvc().perform(get(REPORT_URL).param("start", "2026-01-01").param("end", "2027-01-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.VALIDATION_ERROR.getCode()));
        verifyNoInteractions(analysisMapper, client, mr0PocMapper);
    }

    @Test
    void analyzeAllowsEqualBoundsAndSpanOfExactly365Days() throws Exception {
        mockMvc().perform(get(ANALYZE_URL).param("start", "2026-07-01").param("end", "2026-07-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc().perform(get(ANALYZE_URL).param("start", "2026-01-01").param("end", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(analysisMapper, org.mockito.Mockito.times(2)).selectDailyBars(any(), any(), any());
    }

    // ==================== Controller 层：POST ingest 全量规则 ====================

    @Test
    void ingestRejectsStartAfterEnd() throws Exception {
        mockMvc().perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisStart\":\"2026-07-31\",\"analysisEnd\":\"2026-07-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.VALIDATION_ERROR.getCode()));
        verifyNoInteractions(client, mr0PocMapper);
    }

    @Test
    void ingestRejectsWarmupAfterAnalysisStart() throws Exception {
        mockMvc().perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisStart\":\"2026-07-01\",\"analysisEnd\":\"2026-07-31\","
                                + "\"warmupStart\":\"2026-07-02\",\"sampleSize\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.VALIDATION_ERROR.getCode()));
        verifyNoInteractions(client, mr0PocMapper);
    }

    @Test
    void ingestAllowsWarmupEqualToAnalysisStart() throws Exception {
        mockMvc().perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisStart\":\"2026-07-01\",\"analysisEnd\":\"2026-07-31\","
                                + "\"warmupStart\":\"2026-07-01\",\"sampleSize\":150}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(client).fetchUniversePage(1, 100);
    }

    @Test
    void ingestRejectsSampleSizeOutOfBounds() throws Exception {
        for (int sampleSize : new int[]{0, -1, 501}) {
            mockMvc().perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"analysisStart\":\"2026-07-01\",\"analysisEnd\":\"2026-07-31\","
                                    + "\"sampleSize\":" + sampleSize + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(ErrorCodeEnum.VALIDATION_ERROR.getCode()));
        }
        verifyNoInteractions(client, mr0PocMapper);
    }

    @Test
    void ingestAllowsSampleSizeBoundsOfOneAndFiveHundred() throws Exception {
        for (int sampleSize : new int[]{1, 500}) {
            mockMvc().perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"analysisStart\":\"2026-07-01\",\"analysisEnd\":\"2026-07-31\","
                                    + "\"sampleSize\":" + sampleSize + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
        verify(client, org.mockito.Mockito.times(2)).fetchUniversePage(1, 100);
    }

    @Test
    void ingestRejectsSpanBeyond365Days() throws Exception {
        mockMvc().perform(post(INGEST_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisStart\":\"2026-01-01\",\"analysisEnd\":\"2027-01-02\","
                                + "\"warmupStart\":\"2025-12-31\",\"sampleSize\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.VALIDATION_ERROR.getCode()));
        verifyNoInteractions(client, mr0PocMapper);
    }

    // ==================== Controller 层：畸形日期 → 400 非 500 ====================

    @Test
    void malformedDateParametersReturn400Not500() throws Exception {
        mockMvc().perform(get(ANALYZE_URL).param("start", "2026-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.VALIDATION_ERROR.getCode()));
        mockMvc().perform(get(REPORT_URL).param("end", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.VALIDATION_ERROR.getCode()));
        verifyNoInteractions(analysisMapper, client, mr0PocMapper);
    }

    // ==================== Service 直调层（防绕过 controller） ====================

    @Test
    void serviceDirectAnalyzeRejectsIllegalWindowWithoutStorageAccess() {
        Mr0PocAnalysisService service = new Mr0PocAnalysisService(analysisMapper);
        assertThatThrownBy(() -> service.analyze(AnalysisCommand.builder()
                .analysisStart(END).analysisEnd(START).build()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCodeEnum.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.analyze(AnalysisCommand.builder()
                .analysisStart(LocalDate.of(2026, 1, 1)).analysisEnd(LocalDate.of(2027, 1, 2)).build()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCodeEnum.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.analyze(AnalysisCommand.builder()
                .analysisStart(START).analysisEnd(END).sampleSize(501).build()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCodeEnum.VALIDATION_ERROR);
        verifyNoInteractions(analysisMapper);
    }

    @Test
    void serviceDirectIngestRejectsIllegalParametersWithoutClientAccess() {
        Mr0PocIngestService service = ingestService();
        assertThatThrownBy(() -> service.ingest(IngestCommand.builder()
                .analysisStart(END).analysisEnd(START).build()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.ingest(IngestCommand.builder()
                .analysisStart(START).analysisEnd(END).warmupStart(LocalDate.of(2026, 7, 2)).build()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.ingest(IngestCommand.builder()
                .analysisStart(START).analysisEnd(END).sampleSize(0).build()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.ingest(IngestCommand.builder()
                .analysisStart(LocalDate.of(2026, 1, 1)).analysisEnd(LocalDate.of(2027, 1, 2))
                .warmupStart(LocalDate.of(2025, 12, 31)).build()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCodeEnum.VALIDATION_ERROR);
        verifyNoInteractions(client, mr0PocMapper);
    }

    @Test
    void serviceDirectIngestAcceptsFrozenBoundaryValues() {
        Mr0PocIngestService service = ingestService();
        // 相差 365 天（含端点 366 个日历日）+ warmup 相等 + sampleSize 端点 1/500 全部放行
        service.ingest(IngestCommand.builder()
                .analysisStart(LocalDate.of(2026, 1, 1)).analysisEnd(LocalDate.of(2026, 12, 31))
                .warmupStart(LocalDate.of(2026, 1, 1)).sampleSize(500).build());
        service.ingest(IngestCommand.builder()
                .analysisStart(START).analysisEnd(START).warmupStart(START).sampleSize(1).build());
        verify(client, org.mockito.Mockito.times(2)).fetchUniversePage(1, 100);
    }
}
