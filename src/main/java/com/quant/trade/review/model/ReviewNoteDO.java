package com.quant.trade.review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 盘后复盘数据库对象。
 * <p>
 * 对应数据库表 {@code review_note}，记录每日总复盘或个股复盘内容。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewNoteDO {

    /** 主键 ID */
    private Long id;

    /** 复盘日期 */
    private LocalDate reviewDate;

    /** 股票代码（为空表示每日总复盘） */
    private String symbol;

    /** 复盘标题 */
    private String title;

    /** 市场环境描述 */
    private String marketContext;

    /** 原计划摘要 */
    private String planSummary;

    /** 实际操作摘要 */
    private String actionSummary;

    /** 做对了什么 */
    private String rightThings;

    /** 做错了什么 */
    private String wrongThings;

    /** 规则修正 */
    private String ruleChanges;

    /** 下一步行动 */
    private String nextActions;

    /** 关联的交易记录 ID 列表（逗号分隔字符串，API 层暴露为 List） */
    private String linkedJournalIds;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
