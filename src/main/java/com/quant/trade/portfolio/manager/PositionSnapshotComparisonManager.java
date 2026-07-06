package com.quant.trade.portfolio.manager;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.constant.RiskConstants;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.portfolio.enums.SnapshotChangeTypeEnum;
import com.quant.trade.portfolio.enums.SnapshotStatusEnum;
import com.quant.trade.portfolio.model.PositionSnapshotDO;
import com.quant.trade.portfolio.model.PositionSnapshotItemDO;
import com.quant.trade.portfolio.vo.PositionSnapshotComparisonItemVO;
import com.quant.trade.portfolio.vo.PositionSnapshotComparisonVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 持仓快照对比纯计算 Manager。
 * <p>
 * 不访问数据库，入参为基准与目标快照及其明细，输出差异 VO。
 * 校验与计算口径与设计文档 {@code TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md} 第 6 节一致。
 * <p>
 * 排序：变化类型（NEW -> INCREASED -> REDUCED -> CLOSED -> UNCHANGED）-> 目标市值降序 -> symbol 升序。
 */
@Component
public class PositionSnapshotComparisonManager {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = RiskConstants.DECIMAL_SCALE;
    private static final RoundingMode HALF_UP = RoundingMode.HALF_UP;
    /** 变化类型 -> 排序权重 */
    private static final Map<String, Integer> CHANGE_TYPE_ORDER;

    static {
        Map<String, Integer> order = new LinkedHashMap<>();
        order.put(SnapshotChangeTypeEnum.NEW.getCode(), 0);
        order.put(SnapshotChangeTypeEnum.INCREASED.getCode(), 1);
        order.put(SnapshotChangeTypeEnum.REDUCED.getCode(), 2);
        order.put(SnapshotChangeTypeEnum.CLOSED.getCode(), 3);
        order.put(SnapshotChangeTypeEnum.UNCHANGED.getCode(), 4);
        CHANGE_TYPE_ORDER = Collections.unmodifiableMap(order);
    }

    /**
     * 比较两个已确认快照。
     *
     * @param base       基准快照（非空）
     * @param baseItems  基准快照明细（可为空）
     * @param target     目标快照（非空）
     * @param targetItems 目标快照明细（可为空）
     * @return 对比结果
     * @throws BusinessException 当快照非 CONFIRMED 或基准时间不早于目标时间
     */
    public PositionSnapshotComparisonVO compare(PositionSnapshotDO base,
                                                List<PositionSnapshotItemDO> baseItems,
                                                PositionSnapshotDO target,
                                                List<PositionSnapshotItemDO> targetItems) {
        if (base == null || target == null) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_NOT_FOUND,
                    String.format(MessageConstants.POSITION_SNAPSHOT_COMPARISON_NOT_FOUND, 0L));
        }
        validateConfirmed(base);
        validateConfirmed(target);

        // 基准时间必须严格早于目标时间
        if (base.getSnapshotTime() == null || target.getSnapshotTime() == null
                || !base.getSnapshotTime().isBefore(target.getSnapshotTime())) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_COMPARISON_INVALID,
                    String.format(MessageConstants.POSITION_SNAPSHOT_COMPARISON_INVALID,
                            "基准快照时间必须严格早于目标快照时间"));
        }

        Map<String, PositionSnapshotItemDO> baseMap = indexBySymbol(baseItems);
        Map<String, PositionSnapshotItemDO> targetMap = indexBySymbol(targetItems);

        Set<String> symbols = new TreeSet<>();
        symbols.addAll(baseMap.keySet());
        symbols.addAll(targetMap.keySet());

        List<ItemRow> rows = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            PositionSnapshotItemDO baseItem = baseMap.get(symbol);
            PositionSnapshotItemDO targetItem = targetMap.get(symbol);
            rows.add(buildRow(symbol, baseItem, targetItem));
        }
        rows.sort(this::compareRows);

        List<PositionSnapshotComparisonItemVO> items = rows.stream()
                .map(row -> new PositionSnapshotComparisonItemVO(
                        row.symbol, row.name, row.changeType,
                        row.baseQty == 0L ? null : row.baseQty,
                        row.targetQty == 0L ? null : row.targetQty,
                        row.qtyDelta,
                        row.baseCostPrice, row.targetCostPrice,
                        row.marketValueDelta, row.unrealizedPnlDelta))
                .collect(Collectors.toList());

        return new PositionSnapshotComparisonVO(
                base.getId(), target.getId(),
                base.getSnapshotTime(), target.getSnapshotTime(),
                base.getSnapshotStatus(), target.getSnapshotStatus(),
                delta(target.getTotalCostAmount(), base.getTotalCostAmount()),
                delta(target.getTotalMarketValue(), base.getTotalMarketValue()),
                delta(target.getTotalUnrealizedPnl(), base.getTotalUnrealizedPnl()),
                safeInt(target.getPositionCount()) - safeInt(base.getPositionCount()),
                items);
    }

    private void validateConfirmed(PositionSnapshotDO snapshot) {
        if (!SnapshotStatusEnum.CONFIRMED.getCode().equals(snapshot.getSnapshotStatus())) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_COMPARISON_INVALID,
                    String.format(MessageConstants.POSITION_SNAPSHOT_COMPARISON_NOT_CONFIRMED,
                            snapshot.getSnapshotStatus()));
        }
    }

    private Map<String, PositionSnapshotItemDO> indexBySymbol(List<PositionSnapshotItemDO> items) {
        Map<String, PositionSnapshotItemDO> map = new LinkedHashMap<>();
        if (items == null) {
            return map;
        }
        for (PositionSnapshotItemDO item : items) {
            String key = normalizeSymbol(item.getSymbol());
            if (!key.isEmpty()) {
                map.putIfAbsent(key, item);
            }
        }
        return map;
    }

    private String normalizeSymbol(String symbol) {
        return StringUtils.trimToEmpty(symbol).toUpperCase(Locale.ROOT);
    }

    private ItemRow buildRow(String symbol, PositionSnapshotItemDO baseItem, PositionSnapshotItemDO targetItem) {
        long baseQty = baseItem == null || baseItem.getHoldingQuantity() == null ? 0L : baseItem.getHoldingQuantity();
        long targetQty = targetItem == null || targetItem.getHoldingQuantity() == null ? 0L : targetItem.getHoldingQuantity();
        long qtyDelta = targetQty - baseQty;

        String changeType;
        if (baseItem == null && targetItem != null) {
            changeType = SnapshotChangeTypeEnum.NEW.getCode();
        } else if (baseItem != null && targetItem == null) {
            changeType = SnapshotChangeTypeEnum.CLOSED.getCode();
        } else if (targetQty > baseQty) {
            changeType = SnapshotChangeTypeEnum.INCREASED.getCode();
        } else if (targetQty < baseQty) {
            changeType = SnapshotChangeTypeEnum.REDUCED.getCode();
        } else {
            changeType = SnapshotChangeTypeEnum.UNCHANGED.getCode();
        }

        String name = targetItem != null ? targetItem.getName()
                : (baseItem != null ? baseItem.getName() : null);
        BigDecimal baseCostPrice = baseItem == null ? null : baseItem.getCostPrice();
        BigDecimal targetCostPrice = targetItem == null ? null : targetItem.getCostPrice();
        BigDecimal marketValueDelta = delta(
                targetItem == null ? null : targetItem.getMarketValue(),
                baseItem == null ? null : baseItem.getMarketValue());
        BigDecimal unrealizedPnlDelta = delta(
                targetItem == null ? null : targetItem.getUnrealizedPnl(),
                baseItem == null ? null : baseItem.getUnrealizedPnl());
        BigDecimal targetMarketValue = targetItem == null || targetItem.getMarketValue() == null
                ? ZERO : targetItem.getMarketValue();

        return new ItemRow(symbol, name, changeType, baseQty, targetQty, qtyDelta,
                baseCostPrice, targetCostPrice, marketValueDelta, unrealizedPnlDelta, targetMarketValue);
    }

    private int compareRows(ItemRow a, ItemRow b) {
        int byType = Integer.compare(
                CHANGE_TYPE_ORDER.getOrDefault(a.changeType, 99),
                CHANGE_TYPE_ORDER.getOrDefault(b.changeType, 99));
        if (byType != 0) {
            return byType;
        }
        // 目标市值降序
        int byMarketValue = b.targetMarketValue.compareTo(a.targetMarketValue);
        if (byMarketValue != 0) {
            return byMarketValue;
        }
        return a.symbol.compareTo(b.symbol);
    }

    private BigDecimal delta(BigDecimal targetValue, BigDecimal baseValue) {
        BigDecimal t = targetValue == null ? ZERO : targetValue;
        BigDecimal b = baseValue == null ? ZERO : baseValue;
        return t.subtract(b).setScale(SCALE, HALF_UP);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    /** 内部计算行（含用于排序的目标市值）。 */
    private static final record ItemRow(
            String symbol,
            String name,
            String changeType,
            long baseQty,
            long targetQty,
            long qtyDelta,
            BigDecimal baseCostPrice,
            BigDecimal targetCostPrice,
            BigDecimal marketValueDelta,
            BigDecimal unrealizedPnlDelta,
            BigDecimal targetMarketValue) {}
}
