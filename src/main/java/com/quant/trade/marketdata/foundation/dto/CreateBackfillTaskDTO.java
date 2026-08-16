package com.quant.trade.marketdata.foundation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 创建历史回补任务请求。symbols 为空 = 使用最新股票池快照全量证券。 */
@Data
public class CreateBackfillTaskDTO {

    @NotBlank(message = "datasetCode 不能为空")
    private String datasetCode;

    @NotBlank(message = "marketCode 不能为空")
    private String marketCode;

    @NotBlank(message = "providerCode 不能为空")
    private String providerCode;

    @NotBlank(message = "frequency 不能为空")
    private String frequency;

    @NotBlank(message = "adjustType 不能为空")
    private String adjustType;

    @jakarta.validation.constraints.NotNull(message = "startDate 不能为空")
    private LocalDate startDate;

    @jakarta.validation.constraints.NotNull(message = "endDate 不能为空")
    private LocalDate endDate;

    /** 显式证券列表（可选；空=全池）。 */
    @Size(max = 2000, message = "显式证券列表最多 2000 个")
    private List<String> symbols;

    /** 每分片证券数（可选，默认 50，上限 500）。 */
    private Integer chunkSize;
}
