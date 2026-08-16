package com.quant.trade.marketdata.foundation;

import com.quant.trade.marketdata.foundation.service.DataFoundationDatasetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T13：数据底座 REST API（契约 AC-06，/api/v1/market-data/data-foundation/*）。
 * 统一响应 {success,code,data,message}；参数/错误码/路径全覆盖；run 由 STUB_TEST Provider 支撑（不外联）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StubHistoricalBarProviderConfig.class)
class DataFoundationControllerTest {

    private static final String BASE = "/api/v1/market-data/data-foundation";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StubHistoricalBarProvider stubProvider;
    @Autowired
    private DataFoundationDatasetService datasetService;

    @BeforeEach
    @AfterEach
    void cleanFoundationTables() {
        FoundationTestTables.cleanAll(jdbcTemplate);
        stubProvider.reset();
    }

    private String createStubDatasetViaService() {
        datasetService.createDataset("API_STUB_DS", "API 回补数据集", "CN", "DAILY", "1D",
                StubHistoricalBarProvider.PROVIDER_CODE, "NONE", "api");
        return "API_STUB_DS";
    }

    // ---------------------------------------------------------------- 数据集

    @Test
    void datasetCreationValidationAndConflicts() throws Exception {
        mockMvc.perform(post(BASE + "/datasets").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(BASE + "/datasets").contentType("application/json").content("""
                        {"datasetCode":"API_DS","datasetName":"API数据集","marketCode":"CN","barType":"DAILY",
                         "frequency":"1D","providerCode":"IMPORT_CSV_DAILY","adjustType":"NONE"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.datasetCode").value("API_DS"))
                .andExpect(jsonPath("$.data.unitCaliber").exists());

        mockMvc.perform(post(BASE + "/datasets").contentType("application/json").content("""
                        {"datasetCode":"API_DS","datasetName":"API数据集","marketCode":"CN","barType":"DAILY",
                         "frequency":"1D","providerCode":"IMPORT_CSV_DAILY","adjustType":"NONE"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

        mockMvc.perform(post(BASE + "/datasets").contentType("application/json").content("""
                        {"datasetCode":"API_HFQ","datasetName":"HFQ数据集","marketCode":"CN","barType":"DAILY",
                         "frequency":"1D","providerCode":"IMPORT_CSV_DAILY","adjustType":"HFQ"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ENUM_CODE"));

        mockMvc.perform(get(BASE + "/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    // ---------------------------------------------------------------- 回补任务

    @Test
    void backfillTaskEndpointsFullLifecycle() throws Exception {
        createStubDatasetViaService();
        stubProvider.putBars("SH.600519", java.time.LocalDate.of(2021, 1, 4), 3);

        mockMvc.perform(post(BASE + "/backfill-tasks").contentType("application/json").content("""
                        {"datasetCode":"NO_SUCH_DS","marketCode":"CN","providerCode":"STUB_TEST",
                         "frequency":"1D","adjustType":"NONE","startDate":"2021-01-04","endDate":"2021-01-08"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATA_FOUNDATION_DATASET_NOT_FOUND"));

        MvcResult created = mockMvc.perform(post(BASE + "/backfill-tasks").contentType("application/json").content("""
                        {"datasetCode":"API_STUB_DS","marketCode":"CN","providerCode":"STUB_TEST",
                         "frequency":"1D","adjustType":"NONE","startDate":"2021-01-04","endDate":"2021-01-08",
                         "symbols":["SH.600519"],"chunkSize":50}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.plannedCount").value(1))
                .andExpect(jsonPath("$.data.totalChunks").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.symbols[0]").value("SH.600519"))
                .andReturn();
        int taskId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get(BASE + "/backfill-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(taskId));

        mockMvc.perform(get(BASE + "/backfill-tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get(BASE + "/backfill-tasks/" + taskId + "/chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        mockMvc.perform(post(BASE + "/backfill-tasks/" + taskId + "/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.insertedCount").value(3))
                .andExpect(jsonPath("$.data.successCount").value(1));

        mockMvc.perform(post(BASE + "/backfill-tasks/999999/run"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get(BASE + "/backfill-tasks/999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 终态任务不允许 retry / pause
        mockMvc.perform(post(BASE + "/backfill-tasks/" + taskId + "/chunks/retry"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATA_FOUNDATION_BACKFILL_STATE_INVALID"));
        mockMvc.perform(post(BASE + "/backfill-tasks/" + taskId + "/pause"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATA_FOUNDATION_BACKFILL_STATE_INVALID"));
    }

    // ---------------------------------------------------------------- 导入

    @Test
    void importEndpointsHappyPathAndInvalidKind() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bars.csv", "text/csv",
                """
                symbol,trade_date,open,high,low,close,volume,amount
                SH.600519,2026-07-01,1700.00,1750.00,1690.00,1720.50,2500000,4290125000.00
                SH.600519,2026-07-02,1720.50,1730.00,1701.00,1715.25,1800000,3087450000.00
                """.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(BASE + "/imports?kind=NOT_A_KIND").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DATA_FOUNDATION_IMPORT_KIND_INVALID"));

        MvcResult imported = mockMvc.perform(multipart(BASE + "/imports?kind=DAILY_BAR").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insertedCount").value(2))
                .andExpect(jsonPath("$.data.rejectedCount").value(0))
                .andExpect(jsonPath("$.data.providerCode").value("IMPORT_CSV_DAILY"))
                .andReturn();
        int batchId = com.jayway.jsonpath.JsonPath.read(imported.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get(BASE + "/imports/" + batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importKind").value("DAILY_BAR"))
                .andExpect(jsonPath("$.data.fileName").value("bars.csv"));

        mockMvc.perform(get(BASE + "/imports").param("kind", "DAILY_BAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get(BASE + "/imports/999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- 版本 / 质量 / 发布 / released

    @Test
    void versionQualityAndReleasedEndpoints() throws Exception {
        mockMvc.perform(post(BASE + "/datasets").contentType("application/json").content("""
                        {"datasetCode":"API_IMP_DS","datasetName":"API导入数据集","marketCode":"CN","barType":"DAILY",
                         "frequency":"1D","providerCode":"IMPORT_CSV_DAILY","adjustType":"NONE"}
                        """))
                .andExpect(status().isOk());

        // 未发布：released 返回 data=null（字段省略），前端显示未发布
        mockMvc.perform(get(BASE + "/datasets/API_IMP_DS/released"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 不存在数据集 → DATA_FOUNDATION_DATASET_NOT_FOUND
        mockMvc.perform(get(BASE + "/datasets/NO_SUCH_DS/released"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATA_FOUNDATION_DATASET_NOT_FOUND"));

        MvcResult versionCreated = mockMvc.perform(post(BASE + "/datasets/API_IMP_DS/versions")
                        .contentType("application/json")
                        .content("{\"startDate\":\"2026-07-01\",\"endDate\":\"2026-07-03\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionCode").value("v1"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        int versionId = com.jayway.jsonpath.JsonPath.read(versionCreated.getResponse().getContentAsString(), "$.data.id");

        // 空版本质量检查：13 族全部返回且 EMPTY_DATASET FAIL
        mockMvc.perform(post(BASE + "/dataset-versions/" + versionId + "/quality-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(13)))
                .andExpect(jsonPath("$.data[?(@.checkCode == 'EMPTY_DATASET')].status")
                        .value(org.hamcrest.Matchers.hasItem("FAIL")));

        mockMvc.perform(get(BASE + "/dataset-versions/" + versionId + "/quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(13)));

        mockMvc.perform(get(BASE + "/dataset-versions/" + versionId + "/coverage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // REJECTED 版本禁止发布；released 仍为空
        mockMvc.perform(post(BASE + "/dataset-versions/" + versionId + "/publish"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATA_FOUNDATION_QUALITY_GATE_FAILED"));
        mockMvc.perform(get(BASE + "/datasets/API_IMP_DS/released"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        // 非导入类数据集不允许手动建版本
        createStubDatasetViaService();
        mockMvc.perform(post(BASE + "/datasets/API_STUB_DS/versions")
                        .contentType("application/json")
                        .content("{\"startDate\":\"2026-07-01\",\"endDate\":\"2026-07-03\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATA_FOUNDATION_DATASET_CONFLICT"));

        mockMvc.perform(get(BASE + "/datasets/API_IMP_DS/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }
}
