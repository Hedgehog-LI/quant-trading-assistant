package com.quant.trade.portfolio.calculator;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.constant.RiskConstants;
import com.quant.trade.common.constant.TradingDisclaimerConstants;
import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.common.util.RiskMathUtil;
import com.quant.trade.journal.flow.TradeFlowItem;
import com.quant.trade.portfolio.vo.ClosedTradeVO;
import com.quant.trade.portfolio.vo.PortfolioSummaryVO;
import com.quant.trade.portfolio.vo.PositionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 持仓与盈亏 FIFO 计算器。
 * <p>
 * 纯计算、无状态、不访问数据库，入参为 {@link TradeFlowItem} 流水 + 手工当前价 + 计算日。
 * 按 FIFO（先进先出）规则配对买卖，计算当前持仓、已结算交易、已实现/浮动盈亏、汇总统计。
 * <p>
 * 精度统一 {@link RiskConstants#DECIMAL_SCALE}（6 位，HALF_UP），全部走 {@link RiskMathUtil}。
 */
@Slf4j
@Component
public class FifoCalculatorManager {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    private static final int DECIMAL_SCALE = RiskConstants.DECIMAL_SCALE;
    private static final RoundingMode HALF_UP = RoundingMode.HALF_UP;

    /**
     * 计算单只股票的持仓与已结算交易。
     *
     * @param flows        该股票的交易流水（已按交易时间正序）
     * @param currentPrice 手工当前价（可为 null）
     * @param today        计算日（用于持仓天数）
     * @return 计算结果
     */
    public SymbolCalcResult calculateSymbol(List<TradeFlowItem> flows,
                                            BigDecimal currentPrice,
                                            LocalDate today) {
        Deque<BuyLot> openLots = new ArrayDeque<>();
        List<ClosedTradeVO> closedTrades = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean abnormal = false;
        String symbol = null;
        String name = null;

        for (TradeFlowItem flow : flows) {
            symbol = flow.symbol();
            if (flow.name() != null) {
                name = flow.name();
            }
            BigDecimal totalFee = nullToZero(flow.totalFee());

            if (TradeSideEnum.BUY.getCode().equals(flow.side())) {
                BigDecimal grossCost = RiskMathUtil.multiply(flow.price(), BigDecimal.valueOf(flow.quantity()));
                BigDecimal lotTotalCost = grossCost.add(totalFee).setScale(DECIMAL_SCALE, HALF_UP);
                BigDecimal unitCost = RiskMathUtil.divide(lotTotalCost, BigDecimal.valueOf(flow.quantity()), DECIMAL_SCALE);
                openLots.addLast(new BuyLot(flow.id(), flow.tradeDate(), flow.quantity(), unitCost, totalFee));
            } else if (TradeSideEnum.SELL.getCode().equals(flow.side())) {
                long qtyToMatch = flow.quantity();
                List<Matched> matched = new ArrayList<>();
                while (qtyToMatch > 0 && !openLots.isEmpty()) {
                    BuyLot lot = openLots.peekFirst();
                    long take = Math.min(lot.remainingQty, qtyToMatch);
                    matched.add(new Matched(lot, take));
                    qtyToMatch -= take;
                    lot.remainingQty -= take;
                    if (lot.remainingQty == 0) {
                        openLots.pollFirst();
                    }
                }
                if (qtyToMatch > 0) {
                    // 卖出超过持仓：数据异常，停止该股票后续计算
                    abnormal = true;
                    warnings.add(String.format(MessageConstants.PORTFOLIO_OVERSOLD_ANOMALY, symbol));
                    break;
                }
                closedTrades.add(buildClosedTrade(flow, matched, totalFee, symbol, name));
            }
        }

        // 已结算交易统计
        BigDecimal realizedPnlSum = ZERO;
        BigDecimal sumReturnPoint = ZERO;
        long sumHoldingDays = 0L;
        int winCount = 0;
        int lossCount = 0;
        for (ClosedTradeVO c : closedTrades) {
            realizedPnlSum = realizedPnlSum.add(c.realizedPnl());
            sumReturnPoint = sumReturnPoint.add(c.returnPoint());
            sumHoldingDays += c.holdingDays();
            if (c.profitable()) {
                winCount++;
            } else if (c.realizedPnl().compareTo(ZERO) < 0) {
                lossCount++;
            }
        }

        // 组装当前持仓
        PositionVO position = null;
        BigDecimal unrealizedForSummary = ZERO;
        BigDecimal marketValueForSummary = ZERO;
        BigDecimal currentCostForSummary = ZERO;

        long posQty = 0L;
        for (BuyLot lot : openLots) {
            posQty += lot.remainingQty;
        }
        if (posQty > 0) {
            BigDecimal costAmount = ZERO;
            LocalDate firstBuyDate = null;
            for (BuyLot lot : openLots) {
                costAmount = costAmount.add(RiskMathUtil.multiply(lot.unitCost, BigDecimal.valueOf(lot.remainingQty)));
                if (firstBuyDate == null || lot.buyDate.isBefore(firstBuyDate)) {
                    firstBuyDate = lot.buyDate;
                }
            }
            costAmount = costAmount.setScale(DECIMAL_SCALE, HALF_UP);
            BigDecimal averageCost = RiskMathUtil.divide(costAmount, BigDecimal.valueOf(posQty), DECIMAL_SCALE);
            long holdingDays = ChronoUnit.DAYS.between(firstBuyDate, today);

            if (currentPrice == null) {
                warnings.add(String.format(MessageConstants.PORTFOLIO_NO_CURRENT_PRICE, symbol));
                position = new PositionVO(symbol, name, posQty, averageCost, costAmount,
                        null, null, null, null, firstBuyDate, holdingDays, new ArrayList<>(warnings));
            } else {
                BigDecimal marketValue = RiskMathUtil.multiply(currentPrice, BigDecimal.valueOf(posQty));
                BigDecimal unrealizedPnl = RiskMathUtil.subtract(marketValue, costAmount);
                BigDecimal unrealizedReturnPoint = toPercent(
                        RiskMathUtil.ratio(unrealizedPnl, costAmount, DECIMAL_SCALE));
                position = new PositionVO(symbol, name, posQty, averageCost, costAmount,
                        currentPrice, marketValue, unrealizedPnl, unrealizedReturnPoint,
                        firstBuyDate, holdingDays, new ArrayList<>(warnings));
                unrealizedForSummary = unrealizedPnl;
                marketValueForSummary = marketValue;
            }
            currentCostForSummary = costAmount;
        }

        // 异常时统计字段置零（不污染汇总）
        BigDecimal realizedForSummary = abnormal ? ZERO : realizedPnlSum;
        if (abnormal) {
            unrealizedForSummary = ZERO;
            marketValueForSummary = ZERO;
            currentCostForSummary = ZERO;
        }

        return new SymbolCalcResult(
                position,
                closedTrades,
                warnings,
                abnormal,
                realizedForSummary,
                unrealizedForSummary,
                currentCostForSummary,
                marketValueForSummary,
                abnormal ? 0 : closedTrades.size(),
                abnormal ? 0 : winCount,
                abnormal ? 0 : lossCount,
                abnormal ? ZERO : sumReturnPoint,
                abnormal ? 0L : sumHoldingDays);
    }

    /**
     * 计算全部股票（按 symbol 分组），并汇总。
     *
     * @param flows    全量交易流水（已按交易时间正序）
     * @param priceMap symbol -> 最新手工价
     * @param today    计算日
     * @return 汇总 + 持仓列表 + 全部已结算交易
     */
    public AllSymbolsResult calculateAll(List<TradeFlowItem> flows,
                                         Map<String, BigDecimal> priceMap,
                                         LocalDate today) {
        // 按 symbol 分组，保持流入顺序（已时间正序）
        Map<String, List<TradeFlowItem>> bySymbol = new LinkedHashMap<>();
        for (TradeFlowItem flow : flows) {
            bySymbol.computeIfAbsent(flow.symbol(), k -> new ArrayList<>()).add(flow);
        }

        List<SymbolCalcResult> perSymbol = new ArrayList<>(bySymbol.size());
        for (Map.Entry<String, List<TradeFlowItem>> entry : bySymbol.entrySet()) {
            BigDecimal price = priceMap != null ? priceMap.get(entry.getKey()) : null;
            perSymbol.add(calculateSymbol(entry.getValue(), price, today));
        }

        PortfolioSummaryVO summary = summarize(perSymbol);
        List<PositionVO> positions = perSymbol.stream()
                .map(SymbolCalcResult::position)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<ClosedTradeVO> allClosed = perSymbol.stream()
                .flatMap(r -> r.closedTrades().stream())
                .collect(Collectors.toList());

        return new AllSymbolsResult(summary, positions, allClosed);
    }

    /**
     * 汇总统计（胜率、平均收益率、平均持仓天数按已结算交易笔数等权）。
     */
    public PortfolioSummaryVO summarize(List<SymbolCalcResult> perSymbol) {
        BigDecimal realizedPnl = ZERO;
        BigDecimal unrealizedPnl = ZERO;
        BigDecimal currentCost = ZERO;
        BigDecimal currentMarketValue = ZERO;
        BigDecimal sumReturnPoint = ZERO;
        long sumHoldingDays = 0L;
        int closedCount = 0;
        int winCount = 0;
        int lossCount = 0;
        List<String> warnings = new ArrayList<>();

        for (SymbolCalcResult r : perSymbol) {
            realizedPnl = realizedPnl.add(r.realizedPnlForSummary());
            unrealizedPnl = unrealizedPnl.add(r.unrealizedPnlForSummary());
            currentCost = currentCost.add(r.currentCostForSummary());
            currentMarketValue = currentMarketValue.add(r.currentMarketValueForSummary());
            sumReturnPoint = sumReturnPoint.add(r.sumReturnPointForStats());
            sumHoldingDays += r.sumHoldingDaysForStats();
            closedCount += r.closedTradeCountForStats();
            winCount += r.winCountForStats();
            lossCount += r.lossCountForStats();
            warnings.addAll(r.warnings());
        }

        BigDecimal totalPnl = realizedPnl.add(unrealizedPnl).setScale(DECIMAL_SCALE, HALF_UP);
        BigDecimal winRate = ratioOf(winCount, closedCount);
        BigDecimal averageReturnPoint = closedCount > 0
                ? sumReturnPoint.divide(BigDecimal.valueOf(closedCount), DECIMAL_SCALE, HALF_UP)
                : ZERO;
        BigDecimal averageHoldingDays = closedCount > 0
                ? BigDecimal.valueOf(sumHoldingDays).divide(BigDecimal.valueOf(closedCount), DECIMAL_SCALE, HALF_UP)
                : ZERO;

        if (closedCount == 0) {
            warnings.add(MessageConstants.PORTFOLIO_NO_CLOSED_TRADES);
        }

        return new PortfolioSummaryVO(
                scale(realizedPnl),
                scale(unrealizedPnl),
                totalPnl,
                scale(currentCost),
                scale(currentMarketValue),
                closedCount,
                winCount,
                lossCount,
                winRate,
                averageReturnPoint,
                averageHoldingDays,
                warnings,
                TradingDisclaimerConstants.PORTFOLIO_DISCLAIMER);
    }

    // ==================== 私有方法 ====================

    private ClosedTradeVO buildClosedTrade(TradeFlowItem sellFlow, List<Matched> matched,
                                           BigDecimal sellFee, String symbol, String name) {
        long matchedQty = 0L;
        BigDecimal costSum = ZERO;
        BigDecimal buyFeeAllocated = ZERO;
        LocalDate earliestBuyDate = null;
        List<Long> buyJournalIds = new ArrayList<>();

        for (Matched m : matched) {
            matchedQty += m.take;
            costSum = costSum.add(RiskMathUtil.multiply(m.lot.unitCost, BigDecimal.valueOf(m.take)));
            // 买入费用按数量比例摊销
            BigDecimal allocated = RiskMathUtil.ratio(
                    m.lot.buyFee.multiply(BigDecimal.valueOf(m.take)),
                    BigDecimal.valueOf(m.lot.originalQty),
                    DECIMAL_SCALE);
            buyFeeAllocated = buyFeeAllocated.add(allocated);
            buyJournalIds.add(m.lot.journalId);
            if (earliestBuyDate == null || m.lot.buyDate.isBefore(earliestBuyDate)) {
                earliestBuyDate = m.lot.buyDate;
            }
        }
        costSum = costSum.setScale(DECIMAL_SCALE, HALF_UP);
        buyFeeAllocated = buyFeeAllocated.setScale(DECIMAL_SCALE, HALF_UP);

        BigDecimal sellGross = RiskMathUtil.multiply(sellFlow.price(), BigDecimal.valueOf(sellFlow.quantity()));
        BigDecimal sellNetIncome = sellGross.subtract(sellFee).setScale(DECIMAL_SCALE, HALF_UP);
        BigDecimal totalFee = buyFeeAllocated.add(sellFee).setScale(DECIMAL_SCALE, HALF_UP);
        BigDecimal realizedPnl = RiskMathUtil.subtract(sellNetIncome, costSum);
        BigDecimal returnPoint = toPercent(RiskMathUtil.ratio(realizedPnl, costSum, DECIMAL_SCALE));
        long holdingDays = ChronoUnit.DAYS.between(earliestBuyDate, sellFlow.tradeDate());
        BigDecimal buyAveragePrice = RiskMathUtil.divide(costSum, BigDecimal.valueOf(matchedQty), DECIMAL_SCALE);

        return new ClosedTradeVO(
                symbol, name, earliestBuyDate, sellFlow.tradeDate(), holdingDays,
                matchedQty, buyAveragePrice, sellFlow.price(), costSum, sellGross,
                totalFee, realizedPnl, returnPoint, realizedPnl.compareTo(ZERO) > 0,
                buyJournalIds, sellFlow.id());
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : ZERO;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(DECIMAL_SCALE, HALF_UP);
    }

    /** 小数比率转百分点（×100）。 */
    private BigDecimal toPercent(BigDecimal ratioValue) {
        return ratioValue.multiply(PERCENT).setScale(DECIMAL_SCALE, HALF_UP);
    }

    private BigDecimal ratioOf(int part, int total) {
        if (total == 0) {
            return ZERO;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), DECIMAL_SCALE, HALF_UP);
    }

    // ==================== 内部数据结构 ====================

    /** 买入批次（remainingQty 可变，FIFO 扣减）。 */
    private static final class BuyLot {
        final Long journalId;
        final LocalDate buyDate;
        final long originalQty;
        final BigDecimal unitCost;
        final BigDecimal buyFee;
        long remainingQty;

        BuyLot(Long journalId, LocalDate buyDate, long originalQty, BigDecimal unitCost, BigDecimal buyFee) {
            this.journalId = journalId;
            this.buyDate = buyDate;
            this.originalQty = originalQty;
            this.unitCost = unitCost;
            this.buyFee = buyFee;
            this.remainingQty = originalQty;
        }
    }

    private record Matched(BuyLot lot, long take) {}

    /** 单股票计算结果。统计字段在异常时置零，不参与汇总。 */
    public record SymbolCalcResult(
            PositionVO position,
            List<ClosedTradeVO> closedTrades,
            List<String> warnings,
            boolean abnormal,
            BigDecimal realizedPnlForSummary,
            BigDecimal unrealizedPnlForSummary,
            BigDecimal currentCostForSummary,
            BigDecimal currentMarketValueForSummary,
            int closedTradeCountForStats,
            int winCountForStats,
            int lossCountForStats,
            BigDecimal sumReturnPointForStats,
            long sumHoldingDaysForStats) {}

    /** 全部股票计算结果。 */
    public record AllSymbolsResult(
            PortfolioSummaryVO summary,
            List<PositionVO> positions,
            List<ClosedTradeVO> allClosedTrades) {}
}
