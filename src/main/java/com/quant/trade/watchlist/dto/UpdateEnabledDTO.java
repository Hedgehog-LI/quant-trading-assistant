package com.quant.trade.watchlist.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 启用/停用自选股请求 DTO。
 */
public record UpdateEnabledDTO(

        /** 是否启用 */
        @NotNull(message = "enabled is required")
        Boolean enabled
) {}
