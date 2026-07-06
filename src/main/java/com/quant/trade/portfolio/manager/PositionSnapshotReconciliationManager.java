package com.quant.trade.portfolio.manager;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.journal.flow.TradeFlowItem;
import com.quant.trade.portfolio.calculator.FifoCalculatorManager;
import com.quant.trade.portfolio.enums.ReconciliationStatusEnum;
import com.quant.trade.portfolio.enums.SnapshotStatusEnum;
import com.quant.trade.portfolio.model.PositionSnapshotDO;
import com.quant.trade.portfolio.model.PositionSnapshotItemDO;
import com.quant.trade.portfolio.vo.PositionSnapshotReconciliationItemVO;
import com.quant.trade.portfolio.vo.PositionSnapshotReconciliationVO;
import com.quant.trade.portfolio.vo.PositionVO;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 持仓快照与 FIFO 账本对账纯计算 Manager。
 * <p>
 * 入参为已确认快照、快照明细及截止快照时间点的交易流水；复用 {@link FifoCalculatorManager} 计算
 * FIFO 理论持仓，仅以数量作为一致性判断依据，成本差异只展示不判错。
 * <p>
 * 时间口径：
 * <ul>
 *   <li>{@code tradeDate < snapshotDate} 全部纳入；</li>
 *   <li>{@code tradeDate == snapshotDate} 且 {@code tradeTime} 为空，默认纳入并产生 warning；</li>
 *   <li>{@code tradeDate == snapshotDate} 且 {@code tradeTime} 非空，仅 {@code tradeTime <= snapshotTime} 纳入。</li>
 * </ul>
 * 调用方（service）已按上述口径过滤流水，本 Manager 只负责计算。
 */
@Component
@RequiredArgsConstructor
public class PositionSnapshotReconciliationManager {

    /** 同日 trade_time 缺失时附加的口径提示 */
    public static final String WARNING_SAME_DAY_NO_TIME =
            "存在与快照同日且 trade_time 缺失的交易，已默认纳入对账";

    private final FifoCalculatorManager fifoCalculatorManager;

    /**
     * 对账计算。
     *
     * @param snapshot      已确认快照
     * @param snapshotItems 快照明细
     * @param flows         截止快照时间点的交易流水（service 已按时间口径过滤）
     * @return 对账结果
     */
    public PositionSnapshotReconciliationVO reconcile(PositionSnapshotDO snapshot,
                                                      List<PositionSnapshotItemDO> snapshotItems,
                                                      List<TradeFlowItem> flows) {
        if (snapshot == null) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_NOT_FOUND,
                    String.format(MessageConstants.POSITION_SNAPSHOT_NOT_FOUND, 0L));
        }
        if (!SnapshotStatusEnum.CONFIRMED.getCode().equals(snapshot.getSnapshotStatus())) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_INVALID_TRANSITION,
                    MessageConstants.POSITION_SNAPSHOT_ONLY_DRAFT_EDITABLE);
        }

        LocalDate snapshotDate = snapshot.getSnapshotDate();
        LocalDateTime snapshotTime = snapshot.getSnapshotTime();

        // 按 symbol 分组流水，并检测同日 tradeTime 缺失
        Map<String, List<TradeFlowItem>> flowsBySymbol = new LinkedHashMap<>();
        boolean sameDayNullTime = false;
        if (flows != null) {
            for (TradeFlowItem flow : flows) {
                if (snapshotDate != null && snapshotDate.equals(flow.tradeDate())
                        && flow.tradeTime() == null) {
                    sameDayNullTime = true;
                }
                String key = normalizeSymbol(flow.symbol());
                if (key.isEmpty()) {
                    continue;
                }
                flowsBySymbol.computeIfAbsent(key, k -> new ArrayList<>()).add(flow);
            }
        }

        // 计算 FIFO 理论持仓；记录超卖 symbol（FIFO 异常，绝不能判 MATCHED）
        Map<String, PositionVO> ledgerMap = new LinkedHashMap<>();
        Set<String> oversoldSymbols = new HashSet<>();
        for (Map.Entry<String, List<TradeFlowItem>> entry : flowsBySymbol.entrySet()) {
            FifoCalculatorManager.SymbolCalcResult result =
                    fifoCalculatorManager.calculateSymbol(entry.getValue(), null, snapshotDate);
            if (result.abnormal()) {
                oversoldSymbols.add(entry.getKey());
            }
            if (result.position() != null && result.position().quantity() > 0) {
                ledgerMap.put(entry.getKey(), result.position());
            }
        }

        // 快照明细索引
        Map<String, PositionSnapshotItemDO> snapshotMap = new LinkedHashMap<>();
        if (snapshotItems != null) {
            for (PositionSnapshotItemDO item : snapshotItems) {
                String key = normalizeSymbol(item.getSymbol());
                if (!key.isEmpty()) {
                    snapshotMap.putIfAbsent(key, item);
                }
            }
        }

        // 合并 symbol（oversoldSymbols 必须进入结果集合，否则纯超卖会被遗漏）
        Set<String> symbols = new TreeSet<>();
        symbols.addAll(snapshotMap.keySet());
        symbols.addAll(ledgerMap.keySet());
        symbols.addAll(oversoldSymbols);

        List<String> warnings = new ArrayList<>();
        if (sameDayNullTime) {
            warnings.add(WARNING_SAME_DAY_NO_TIME);
        }

        List<PositionSnapshotReconciliationItemVO> items = new ArrayList<>(symbols.size());
        int matchedCount = 0;
        int mismatchCount = 0;
        for (String symbol : symbols) {
            PositionSnapshotItemDO snapshotItem = snapshotMap.get(symbol);
            PositionVO ledger = ledgerMap.get(symbol);

            long snapshotQty = snapshotItem == null || snapshotItem.getHoldingQuantity() == null
                    ? 0L : snapshotItem.getHoldingQuantity();
            long ledgerQty = ledger == null ? 0L : ledger.quantity();

            String status;
            if (oversoldSymbols.contains(symbol)) {
                status = ReconciliationStatusEnum.QUANTITY_MISMATCH.getCode();
                warnings.add("账本存在超卖/数据不一致：" + symbol + "（卖出超过 FIFO 可用批次）");
            } else {
                status = resolveStatus(snapshotQty, ledgerQty);
            }
            long quantityDifference = snapshotQty - ledgerQty;
            BigDecimal snapshotCostPrice = snapshotItem == null ? null : snapshotItem.getCostPrice();
            BigDecimal ledgerAverageCost = ledger == null ? null : ledger.averageCost();
            String name = snapshotItem != null ? snapshotItem.getName()
                    : (ledger != null ? ledger.name() : null);

            items.add(new PositionSnapshotReconciliationItemVO(
                    symbol, name, status, snapshotQty, ledgerQty,
                    quantityDifference, snapshotCostPrice, ledgerAverageCost));

            if (ReconciliationStatusEnum.MATCHED.getCode().equals(status)) {
                matchedCount++;
            } else {
                mismatchCount++;
            }
        }

        items.sort(Comparator
                .comparingInt(PositionSnapshotReconciliationManager::statusOrder)
                .thenComparing(PositionSnapshotReconciliationItemVO::symbol));

        return new PositionSnapshotReconciliationVO(
                snapshot.getId(), snapshotTime,
                matchedCount, mismatchCount, mismatchCount > 0,
                warnings, items);
    }

    private String resolveStatus(long snapshotQty, long ledgerQty) {
        if (snapshotQty > 0 && ledgerQty > 0) {
            return snapshotQty == ledgerQty
                    ? ReconciliationStatusEnum.MATCHED.getCode()
                    : ReconciliationStatusEnum.QUANTITY_MISMATCH.getCode();
        }
        if (snapshotQty > 0 && ledgerQty == 0) {
            return ReconciliationStatusEnum.SNAPSHOT_ONLY.getCode();
        }
        if (snapshotQty == 0 && ledgerQty > 0) {
            return ReconciliationStatusEnum.LEDGER_ONLY.getCode();
        }
        // 双零防御：理论上不会出现在合并集合中，按一致处理
        return ReconciliationStatusEnum.MATCHED.getCode();
    }

    /** 不一致优先展示：QUANTITY_MISMATCH < SNAPSHOT_ONLY < LEDGER_ONLY < MATCHED。 */
    private static int statusOrder(PositionSnapshotReconciliationItemVO item) {
        return switch (item.status()) {
            case "QUANTITY_MISMATCH" -> 0;
            case "SNAPSHOT_ONLY" -> 1;
            case "LEDGER_ONLY" -> 2;
            default -> 3;
        };
    }

    private String normalizeSymbol(String symbol) {
        return StringUtils.trimToEmpty(symbol).toUpperCase(Locale.ROOT);
    }
}
