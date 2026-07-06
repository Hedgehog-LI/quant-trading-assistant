package com.quant.trade.marketdata.manager;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.dao.StockDailyBarMapper;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import com.quant.trade.marketdata.vo.DailyBarImportResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** 行情数据领域规则层：canonical 规范化、CSV 解析校验、幂等导入。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockDataManager {

    private final StockBasicMapper stockBasicMapper;
    private final StockDailyBarMapper stockDailyBarMapper;

    private static final String[] CSV_HEADERS = {
        "canonical_symbol", "trade_date", "open", "high", "low", "close", "volume", "amount", "adjust_type"
    };
    private static final int MAX_ROWS = 10000;

    /** 规范化 canonical_symbol：SH.600519 / SZ.000001 / BJ.430047 */
    public String buildCanonicalSymbol(String market, String symbol) {
        String m = market.trim().toUpperCase(Locale.ROOT);
        String s = symbol.trim();
        if (!Set.of("SH", "SZ", "BJ").contains(m)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL,
                    "市场必须为 SH/SZ/BJ: " + market);
        }
        if (!s.matches("\\d{4,6}")) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL,
                    "证券代码必须为 4-6 位数字: " + symbol);
        }
        return m + "." + s;
    }

    /** CSV 解析 + 校验 + 幂等导入（原子提交）。 */
    public DailyBarImportResultVO importDailyBarsCsv(InputStream input) {
        List<DailyBarImportResultVO.RowError> errors = new ArrayList<>();
        List<StockDailyBarDO> toInsert = new ArrayList<>();
        List<StockDailyBarDO> toUpdate = new ArrayList<>();
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader(CSV_HEADERS).setSkipHeaderRecord(true).build().parse(reader)) {

            // 预加载所有涉及的 stock_basic（校验股票存在）
            Set<String> seenSymbols = new HashSet<>();
            Map<String, StockBasicDO> stockMap = new HashMap<>();

            int rowNum = 1;
            for (CSVRecord record : parser) {
                rowNum++;
                if (rowNum - 1 > MAX_ROWS) {
                    errors.add(new DailyBarImportResultVO.RowError(rowNum, "超过最大行数限制 " + MAX_ROWS));
                    break;
                }
                try {
                    String canonicalSymbol = record.get("canonical_symbol").trim().toUpperCase();
                    String tradeDateStr = record.get("trade_date").trim();
                    String adjustType = record.get("adjust_type").trim().toUpperCase();
                    BigDecimal open = new BigDecimal(record.get("open").trim());
                    BigDecimal high = new BigDecimal(record.get("high").trim());
                    BigDecimal low = new BigDecimal(record.get("low").trim());
                    BigDecimal close = new BigDecimal(record.get("close").trim());
                    long volume = Long.parseLong(record.get("volume").trim());
                    BigDecimal amount = new BigDecimal(record.get("amount").trim());

                    validateCanonicalSymbol(canonicalSymbol);
                    validateAdjustType(adjustType);
                    validateOhlc(open, high, low, close);
                    if (volume < 0) throw new IllegalArgumentException("volume 不能为负");
                    if (amount.signum() < 0) throw new IllegalArgumentException("amount 不能为负");

                    // 延迟加载 stock_basic
                    if (!stockMap.containsKey(canonicalSymbol)) {
                        StockBasicDO stock = stockBasicMapper.selectByCanonicalSymbol(canonicalSymbol);
                        if (stock == null) {
                            throw new IllegalArgumentException("证券不存在: " + canonicalSymbol);
                        }
                        stockMap.put(canonicalSymbol, stock);
                    }

                    LocalDate tradeDate = LocalDate.parse(tradeDateStr);
                    String dataSource = "CSV";

                    StockDailyBarDO existing = stockDailyBarMapper.selectByUniqueKey(
                            canonicalSymbol, tradeDate, adjustType, dataSource);

                    StockDailyBarDO bar = StockDailyBarDO.builder()
                            .canonicalSymbol(canonicalSymbol).tradeDate(tradeDate)
                            .adjustType(adjustType).dataSource(dataSource)
                            .openPrice(open).highPrice(high).lowPrice(low).closePrice(close)
                            .volume(volume).amount(amount).build();

                    if (existing == null) {
                        toInsert.add(bar);
                    } else if (isSameData(existing, bar)) {
                        skipped++;
                    } else {
                        toUpdate.add(bar);
                    }
                } catch (Exception e) {
                    errors.add(new DailyBarImportResultVO.RowError(rowNum, e.getMessage()));
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.DAILY_BAR_CSV_PARSE_ERROR,
                    "CSV 解析失败: " + e.getMessage());
        }

        // 原子提交
        if (!errors.isEmpty()) {
            return new DailyBarImportResultVO(0, 0, 0, errors.size(), errors);
        }
        if (!toInsert.isEmpty()) stockDailyBarMapper.batchInsert(toInsert);
        for (StockDailyBarDO bar : toUpdate) stockDailyBarMapper.updateByUniqueKey(bar);

        return new DailyBarImportResultVO(toInsert.size(), toUpdate.size(), skipped, 0, errors);
    }

    private void validateCanonicalSymbol(String symbol) {
        if (!symbol.matches("^(SH|SZ|BJ)\\.\\d{4,6}$")) {
            throw new IllegalArgumentException("canonical_symbol 格式不合法: " + symbol);
        }
    }

    private void validateAdjustType(String adjustType) {
        if (!Set.of("NONE", "QF", "HF").contains(adjustType)) {
            throw new IllegalArgumentException("adjust_type 必须为 NONE/QF/HF: " + adjustType);
        }
    }

    private void validateOhlc(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        if (open.signum() <= 0 || high.signum() <= 0 || low.signum() <= 0 || close.signum() <= 0) {
            throw new IllegalArgumentException("OHLC 价格必须大于 0");
        }
        if (high.compareTo(open) < 0 || high.compareTo(close) < 0 || high.compareTo(low) < 0) {
            throw new IllegalArgumentException("high 不能小于其他价格");
        }
        if (low.compareTo(open) > 0 || low.compareTo(close) > 0 || low.compareTo(high) > 0) {
            throw new IllegalArgumentException("low 不能大于其他价格");
        }
    }

    private boolean isSameData(StockDailyBarDO existing, StockDailyBarDO bar) {
        return existing.getOpenPrice().compareTo(bar.getOpenPrice()) == 0
                && existing.getHighPrice().compareTo(bar.getHighPrice()) == 0
                && existing.getLowPrice().compareTo(bar.getLowPrice()) == 0
                && existing.getClosePrice().compareTo(bar.getClosePrice()) == 0
                && existing.getVolume() != null && existing.getVolume().equals(bar.getVolume())
                && existing.getAmount().compareTo(bar.getAmount()) == 0;
    }
}
