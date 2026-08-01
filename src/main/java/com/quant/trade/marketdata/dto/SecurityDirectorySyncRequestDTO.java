package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.Pattern;

/** 目录同步触发请求。mode 可选，默认 FULL。 */
public record SecurityDirectorySyncRequestDTO(
        @Pattern(regexp = "FULL|INCREMENTAL", message = "mode 必须为 FULL 或 INCREMENTAL") String mode) {
}
