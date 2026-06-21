package com.quant.trade.portfolio.vo;

import com.quant.trade.journal.vo.TradeJournalVO;

import java.util.List;

/**
 * 单股票持仓详情响应 VO（GET /symbol/{symbol}）。
 * <p>
 * 聚合该股票的当前持仓、已结算交易和原始交易流水。
 */
public record SymbolDetailVO(
        /** 当前持仓（无持仓时为 null） */
        PositionVO position,
        /** 已结算交易列表 */
        List<ClosedTradeVO> closedTrades,
        /** 原始交易流水 */
        List<TradeJournalVO> flows,
        /** 告警提示 */
        List<String> warnings
) {}
