package com.quant.trade.dashboard.vo;

/**
 * 工作台待办 VO。
 * <p>
 * 待办只表达"需要用户关注的数据质量或纪律事项"，不包含任何买卖建议。
 * 点击后跳转 {@code targetPath} 对应页面处理。
 *
 * @param code        待办码，参见 {@link com.quant.trade.common.enums.DashboardTodoCodeEnum}
 * @param level       级别，参见 {@link com.quant.trade.common.enums.DashboardTodoLevelEnum}
 * @param title       标题
 * @param description 说明
 * @param count       数量，0 不应出现
 * @param targetPath  跳转路径
 */
public record DashboardTodoVO(

        String code,
        String level,
        String title,
        String description,
        long count,
        String targetPath
) {}
