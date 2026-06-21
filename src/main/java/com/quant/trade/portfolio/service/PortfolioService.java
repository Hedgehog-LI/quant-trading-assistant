package com.quant.trade.portfolio.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.journal.flow.TradeFlowItem;
import com.quant.trade.journal.service.TradeJournalService;
import com.quant.trade.journal.vo.TradeJournalVO;
import com.quant.trade.portfolio.calculator.FifoCalculatorManager;
import com.quant.trade.portfolio.convert.PortfolioPriceConverter;
import com.quant.trade.portfolio.dto.PriceSnapshotDTO;
import com.quant.trade.portfolio.manager.PortfolioPriceManager;
import com.quant.trade.portfolio.model.PortfolioPriceSnapshotDO;
import com.quant.trade.portfolio.vo.ClosedTradeVO;
import com.quant.trade.portfolio.vo.PortfolioSummaryVO;
import com.quant.trade.portfolio.vo.PositionVO;
import com.quant.trade.portfolio.vo.PriceSnapshotVO;
import com.quant.trade.portfolio.vo.SymbolDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 持仓账本应用服务。
 * <p>
 * 负责事务边界和业务流程编排：取交易流水（跨模块调用 journal）、取手工当前价、
 * 委托 {@link FifoCalculatorManager} 计算，组装 VO。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final TradeJournalService tradeJournalService;
    private final PortfolioPriceManager portfolioPriceManager;
    private final FifoCalculatorManager fifoCalculatorManager;
    private final PortfolioPriceConverter portfolioPriceConverter;

    /**
     * 汇总统计。
     */
    public PortfolioSummaryVO getSummary() {
        LocalDate today = LocalDate.now();
        List<TradeFlowItem> flows = tradeJournalService.listFlowItems(null, null);
        Map<String, BigDecimal> prices = portfolioPriceManager.getLatestPriceMap();
        FifoCalculatorManager.AllSymbolsResult result = fifoCalculatorManager.calculateAll(flows, prices, today);
        return result.summary();
    }

    /**
     * 当前持仓列表（已清仓的 symbol 不返回）。
     */
    public List<PositionVO> getPositions() {
        LocalDate today = LocalDate.now();
        List<TradeFlowItem> flows = tradeJournalService.listFlowItems(null, null);
        Map<String, BigDecimal> prices = portfolioPriceManager.getLatestPriceMap();
        FifoCalculatorManager.AllSymbolsResult result = fifoCalculatorManager.calculateAll(flows, prices, today);
        return result.positions();
    }

    /**
     * 已结算交易列表，支持按 symbol 和卖出日期区间过滤。
     * <p>
     * 日期过滤作用于 sellDate（平仓日），不影响 FIFO 配对所依赖的全量流水。
     */
    public List<ClosedTradeVO> getClosedTrades(String symbol, LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now();
        List<TradeFlowItem> flows = tradeJournalService.listFlowItems(null, null);
        Map<String, BigDecimal> prices = portfolioPriceManager.getLatestPriceMap();
        FifoCalculatorManager.AllSymbolsResult result = fifoCalculatorManager.calculateAll(flows, prices, today);

        String normalizedSymbol = StringUtils.isBlank(symbol) ? null : symbol.trim().toUpperCase();
        return result.allClosedTrades().stream()
                .filter(c -> normalizedSymbol == null || normalizedSymbol.equals(c.symbol()))
                .filter(c -> fromDate == null || !c.sellDate().isBefore(fromDate))
                .filter(c -> toDate == null || !c.sellDate().isAfter(toDate))
                .collect(Collectors.toList());
    }

    /**
     * 单股票详情（持仓 + 已结算交易 + 原始流水）。
     * <p>
     * 若该股票存在卖出超过持仓的异常数据，抛 {@link ErrorCodeEnum#INSUFFICIENT_HOLDING} 以精确定位。
     */
    public SymbolDetailVO getSymbolDetail(String symbol) {
        String normalizedSymbol = requireSymbol(symbol);
        LocalDate today = LocalDate.now();
        List<TradeFlowItem> flows = tradeJournalService.listFlowItemsBySymbol(normalizedSymbol);
        BigDecimal currentPrice = portfolioPriceManager.getLatestPrice(normalizedSymbol);
        FifoCalculatorManager.SymbolCalcResult result =
                fifoCalculatorManager.calculateSymbol(flows, currentPrice, today);

        if (result.abnormal()) {
            throw new BusinessException(ErrorCodeEnum.INSUFFICIENT_HOLDING,
                    "Symbol " + normalizedSymbol + " has oversold trades, please check journals");
        }

        List<TradeJournalVO> flowVos = tradeJournalService.list(null, normalizedSymbol, null);
        return new SymbolDetailVO(result.position(), result.closedTrades(), flowVos, result.warnings());
    }

    /**
     * 新增或更新手工当前价（相同 symbol + priceDate 覆盖）。
     */
    @Transactional
    public PriceSnapshotVO upsertPrice(PriceSnapshotDTO dto) {
        PortfolioPriceSnapshotDO saved = portfolioPriceManager.upsert(portfolioPriceConverter.toDO(dto));
        return portfolioPriceConverter.toVO(saved);
    }

    /**
     * 查询全部手工当前价（按价格日期倒序）。
     */
    public List<PriceSnapshotVO> listPrices() {
        return portfolioPriceConverter.toVOList(portfolioPriceManager.list());
    }

    // ==================== 私有方法 ====================

    private String requireSymbol(String symbol) {
        if (StringUtils.isBlank(symbol)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "symbol is required");
        }
        return symbol.trim().toUpperCase();
    }
}
