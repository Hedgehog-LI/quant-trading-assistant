package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.NotBlank;

/** 创建同步任务请求 DTO。 */
public record CreateSyncTaskDTO(
    @NotBlank String taskType,
    @NotBlank String provider,
    @NotBlank String scopeJson
) {}
