package com.quant.trade.marketdata.poc;

import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.common.exception.GlobalExceptionHandler;
import com.quant.trade.marketdata.manager.StockBasicRegistrationManager;
import com.quant.trade.marketdata.poc.Mr0PocIngestService.IngestCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-02 聚焦测试（TEST-03）：ingest 环境门禁。ingest 必须同时满足
 * qta.mr0-poc.ingest-enabled=true 且 active profiles 恰为 {"local"}；
 * 非 local/组合/空 profile 一律 400 BUSINESS_RULE_VIOLATION envelope（非 500）。
 * 用 MockEnvironment 驱动 profile 组合，不起完整 Spring 上下文；controller 经 standalone MockMvc
 * + 真实 GlobalExceptionHandler 验证 400 envelope，service 直调路径验证双层防御。
 * 拒绝路径 PublicMarketDataClient 零交互；analyze/report 保持只读、不设门禁。
 */
class Mr0PocIngestGateTest {

    private static final String INGEST_URL = ApiConstants.API_V1 + "/market-research/mr0-poc/ingest";
    private static final String ANALYZE_URL = ApiConstants.API_V1 + "/market-research/mr0-poc/analyze";
    private static final String REPORT_URL = ApiConstants.API_V1 + "/market-research/mr0-poc/report";

    private final PublicMarketDataClient client = mock(PublicMarketDataClient.class);
    private final Mr0PocMapper mr0PocMapper = mock(Mr0PocMapper.class);
    private final StockBasicRegistrationManager registrationManager = mock(StockBasicRegistrationManager.class);
    private final Mr0PocAnalysisMapper analysisMapper = mock(Mr0PocAnalysisMapper.class);

    private static MockEnvironment environmentWith(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    /** 真实 controller + 真实 service，仅底层协作（公共源 client/mapper）打桩，拒绝路径零交互可证。 */
    private MockMvc mockMvc(String... profiles) {
        return mockMvc(new Mr0PocIngestGate(environmentWith(profiles), true));
    }

    private MockMvc mockMvc(Mr0PocIngestGate gate) {
        Mr0PocController controller = new Mr0PocController(
                new Mr0PocIngestService(client, mr0PocMapper, registrationManager, gate),
                new Mr0PocAnalysisService(analysisMapper),
                new Mr0PocQualityService(analysisMapper),
                gate);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @BeforeEach
    void stubEmptyPublicSource() {
        when(client.fetchUniversePage(anyInt(), anyInt())).thenReturn(List.of());
        when(client.fetchIndustryCatalog()).thenReturn(Map.of());
        when(client.fetchDailyBars(anyString(), any(), any())).thenReturn(List.of());
        when(client.fetchMoneyFlow(anyString())).thenReturn(List.of());
    }

    @Test
    void ingestProceedsOnlyWhenExactlyLocalProfileAndSwitchEnabled() throws Exception {
        mockMvc("local").perform(post(INGEST_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 放行进入 service：真实 ingest 管线已到达公共源 client（universe 首页 + 基准 SH.000001 日 K）
        verify(client).fetchUniversePage(1, 100);
        verify(client).fetchDailyBars(eq("sh000001"), any(), any());
    }

    @Test
    void ingestRejectedForTestProfileEvenWhenEnabled() throws Exception {
        mockMvc("test").perform(post(INGEST_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.BUSINESS_RULE_VIOLATION.getCode()));
        verifyNoInteractions(client, mr0PocMapper, registrationManager);
    }

    @Test
    void ingestRejectedForLocalPlusTestProfileCombination() throws Exception {
        mockMvc("local", "test").perform(post(INGEST_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.BUSINESS_RULE_VIOLATION.getCode()));
        verifyNoInteractions(client, mr0PocMapper, registrationManager);
    }

    @Test
    void ingestRejectedWhenNoActiveProfile() throws Exception {
        mockMvc().perform(post(INGEST_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.BUSINESS_RULE_VIOLATION.getCode()));
        verifyNoInteractions(client, mr0PocMapper, registrationManager);
    }

    @Test
    void ingestRejectedForDockerProfile() throws Exception {
        mockMvc("docker").perform(post(INGEST_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.BUSINESS_RULE_VIOLATION.getCode()));
        verifyNoInteractions(client, mr0PocMapper, registrationManager);
    }

    @Test
    void ingestRejectedWhenSwitchDisabledEvenUnderLocalProfile() throws Exception {
        mockMvc(new Mr0PocIngestGate(environmentWith("local"), false)).perform(post(INGEST_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.BUSINESS_RULE_VIOLATION.getCode()));
        verifyNoInteractions(client, mr0PocMapper, registrationManager);
    }

    @Test
    void serviceDirectIngestIsBlockedByGateForNonLocalProfile() {
        Mr0PocIngestService service = new Mr0PocIngestService(client, mr0PocMapper, registrationManager,
                new Mr0PocIngestGate(environmentWith("test"), true));
        assertThatThrownBy(() -> service.ingest(IngestCommand.builder().build()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCodeEnum.BUSINESS_RULE_VIOLATION);
        verifyNoInteractions(client, mr0PocMapper, registrationManager);
    }

    @Test
    void serviceDirectIngestIsBlockedWhenSwitchDisabled() {
        Mr0PocIngestService service = new Mr0PocIngestService(client, mr0PocMapper, registrationManager,
                new Mr0PocIngestGate(environmentWith("local"), false));
        assertThatThrownBy(() -> service.ingest(IngestCommand.builder().build()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCodeEnum.BUSINESS_RULE_VIOLATION);
        verifyNoInteractions(client, mr0PocMapper, registrationManager);
    }

    @Test
    void analyzeAndReportRemainUngatedReadOnlyUnderNonLocalProfile() throws Exception {
        // 门禁只作用于 ingest；analyze/report 在非 local profile 且开关关闭时仍只读可用、零外联
        mockMvc("test").perform(get(ANALYZE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc("test").perform(get(REPORT_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verifyNoInteractions(client, registrationManager, mr0PocMapper);
    }
}
