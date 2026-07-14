package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 分钟 K 线写入 DTO（供手动写入或测试用）。 */
@Data
public class MinuteBarUpsertDTO {

    @NotBlank(message = "canonicalSymbol 不能为空")
    private String canonicalSymbol;

    @NotNull(message = "barStartTime 不能为空")
    private LocalDateTime barStartTime;

    @NotNull(message = "barEndTime 不能为空")
    private LocalDateTime barEndTime;

    @Pattern(regexp = "^(1M|5M|15M|30M|60M)$", message = "intervalType 必须为 1M/5M/15M/30M/60M")
    private String intervalType;

    @Pattern(regexp = "^(NONE|QF|HF)$", message = "adjustType 必须为 NONE/QF/HF")
    private String adjustType;

    @NotBlank(message = "dataSource 不能为空")
    private String dataSource;

    @NotNull(message = "openPrice 不能为空")
    private BigDecimal openPrice;

    @NotNull(message = "highPrice 不能为空")
    private BigDecimal highPrice;

    @NotNull(message = "lowPrice 不能为空")
    private BigDecimal lowPrice;

    @NotNull(message = "closePrice 不能为空")
    private BigDecimal closePrice;

    @NotNull(message = "volume 不能为空")
    private Long volume;

    @NotNull(message = "amount 不能为空")
    private BigDecimal amount;

    private BigDecimal turnoverRate;
    private String sessionType;
}
