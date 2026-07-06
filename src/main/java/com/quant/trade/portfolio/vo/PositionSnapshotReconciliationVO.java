package com.quant.trade.portfolio.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 持仓快照与 FIFO 账本对账响应 VO。
 * <p>
 * 以数量为核心一致性判断：{@code hasMismatch=true} 表示存在非 MATCHED 项。
 * 对账只读，不会自动修改任何交易流水。结果仅用于核对，不构成投资建议，也不替代券商正式对账单。
 */
public record PositionSnapshotReconciliationVO(

        /** 快照 ID */
        Long snapshotId,
        /** 快照时间 */
        LocalDateTime snapshotTime,
        /** MATCHED 项数量 */
        int matchedCount,
        /** 非 MATCHED 项数量 */
        int mismatchCount,
        /** 是否存在不一致 */
        boolean hasMismatch,
        /** 提示信息（如同日 trade_time 缺失等口径说明） */
        List<String> warnings,
        /** 明细列表 */
        List<PositionSnapshotReconciliationItemVO> items
) {}
