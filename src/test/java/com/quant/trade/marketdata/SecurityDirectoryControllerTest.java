package com.quant.trade.marketdata;

import com.quant.trade.marketdata.controller.SecurityDirectoryController;
import com.quant.trade.marketdata.exception.SecurityDirectoryImportException;
import com.quant.trade.marketdata.service.SecurityDirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityDirectoryControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SecurityDirectoryService service;
    @Autowired
    private SecurityDirectoryController controller;

    @BeforeEach
    void cleanDirectory() {
        jdbcTemplate.update("DELETE FROM stock_alias");
        jdbcTemplate.update("DELETE FROM stock_daily_bar");
        jdbcTemplate.update("DELETE FROM stock_basic");
    }

    @Test
    void importSearchAndDetailExposeFrozenEnvelopeFields() throws Exception {
        String csv = """
                canonical_symbol,name,market,exchange,currency,security_type,list_status,data_source,source_updated_at,aliases
                US.AAPL,Apple Inc.,US,NASDAQ,USD,STOCK,LISTED,TEST,2026-07-01T00:00:00Z,ENGLISH:en:Apple
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "directory.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/market-data/security-directory/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.inserted").value(1))
                .andExpect(jsonPath("$.data.aliasesInserted").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));

        mockMvc.perform(get("/api/v1/market-data/securities/search")
                        .param("q", "Apple").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].canonicalSymbol").value("US.AAPL"))
                .andExpect(jsonPath("$.data.items[0].matchedBy").value("FORMAL_NAME_PREFIX"))
                .andExpect(jsonPath("$.data.catalogStatus").value("READY"))
                .andExpect(jsonPath("$.data.catalogUpdatedAt").value("2026-07-01T00:00:00Z"))
                .andExpect(jsonPath("$.data.degraded").value(false));

        mockMvc.perform(get("/api/v1/market-data/securities/US.AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canonicalSymbol").value("US.AAPL"))
                .andExpect(jsonPath("$.data.displayName").value("Apple Inc."))
                .andExpect(jsonPath("$.data.aliases[0].normalizedAlias").value("apple"));
    }

    @Test
    void invalidMissingAndNoMatchStatesUseDistinctStableEnvelopes() throws Exception {
        mockMvc.perform(post("/api/v1/market-data/security-directory/import"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CSV_EMPTY_FILE"))
                .andExpect(jsonPath("$.data.errors[0].reasonCode").value("EMPTY_FILE"));

        mockMvc.perform(get("/api/v1/market-data/securities/search").param("q", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PARAM_ERROR"));

        mockMvc.perform(get("/api/v1/market-data/securities/search").param("q", "zz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.catalogStatus").value("EMPTY"))
                .andExpect(jsonPath("$.data.catalogUpdatedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.stale").value(false));

        mockMvc.perform(get("/api/v1/market-data/securities/US.MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
    }

    @Test
    void oversizeMapsTo413WithoutAllocatingPayload() {
        SecurityDirectoryImportException exception = assertThrows(
                SecurityDirectoryImportException.class,
                () -> service.importCsv(new ByteArrayInputStream(new byte[]{1}),
                        SecurityDirectoryService.MAX_FILE_SIZE + 1));
        ResponseEntity<?> response = controller.handleImport(exception);
        assertEquals(413, response.getStatusCode().value());
    }
}
