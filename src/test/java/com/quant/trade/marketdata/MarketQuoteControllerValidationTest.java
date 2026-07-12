package com.quant.trade.marketdata;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 行情 HTTP 入参校验测试。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketQuoteControllerValidationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void latestQuoteRejectsEmptySymbolListAtHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/market-data/quotes/latest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canonicalSymbols\":[],\"persist\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("canonicalSymbols 不能为空")));
    }

    @Test
    void latestQuoteRejectsBlankSymbolAtHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/market-data/quotes/latest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canonicalSymbols\":[\"\"],\"persist\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("canonicalSymbol 不能为空")));
    }
}
