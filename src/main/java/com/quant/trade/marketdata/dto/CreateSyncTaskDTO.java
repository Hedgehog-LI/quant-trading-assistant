package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * 创建日 K 同步任务请求 DTO。
 * <p>
 * 结构化字段替代 scopeJson 手写解析，序列化时由 Jackson 统一转 JSON 存入 scope_json 列。
 */
public record CreateSyncTaskDTO(
    @NotBlank(message = "taskType 不能为空") String taskType,
    @NotBlank(message = "provider 不能为空") String provider,
    @NotBlank(message = "canonicalSymbol 不能为空") String canonicalSymbol,
    /** 起始日期，为空时默认近 1 个月 */
    LocalDate startDate,
    /** 截止日期，为空时默认今天 */
    LocalDate endDate,
    /** 复权类型 NONE/QF/HF，为空时默认 NONE */
    String adjustType
) {}
