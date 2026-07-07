package com.quant.trade.marketdata.manager;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.MarketDataConstants;
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
import java.time.LocalDateTime;
import java.util.*;

/** 行情数据领域规则层：canonical 规范化、CSV 解析校验、幂等导入（含文件内重复键检测）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockDataManager {

    private final StockBasicMapper stockBasicMapper;
    private final StockDailyBarMapper stockDailyBarMapper;

    /** 规范化 canonical_symbol：SH.600519 / SZ.000001 / BJ.430047 */
    public String buildCanonicalSymbol(String market, String symbol) {
        String m = market.trim().toUpperCase(Locale.ROOT);
        String s = symbol.trim();
        if (!MarketDataConstants.VALID_MARKETS.contains(m)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL,
                    "市场必须为 SH/SZ/BJ: " + market);
        }
        if (!s.matches("\\d{4,6}")) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL,
                    "证券代码必须为 4-6 位数字: " + symbol);
        }
        return m + "." + s;
    }

    /**
     * CSV 解析 + 校验 + 幂等导入（原子提交）。
     * 处理同一文件内重复幂等键：
     * - 相同键且内容一致 → skipped。
     * - 相同键但内容冲突 → 整批拒绝，返回行错误。
     * - DB 中已存在 → skipped（一致）或 updated（不同）。
     */
    public DailyBarImportResultVO importDailyBarsCsv(InputStream input, long fileSize) {
        // 文件级校验
        if (fileSize <= 0) {
            throw new BusinessException(ErrorCodeEnum.CSV_EMPTY_FILE, "CSV 文件为空");
        }
        if (fileSize > MarketDataConstants.MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodeEnum.CSV_FILE_TOO_LARGE,
                    "文件超过 " + MarketDataConstants.MAX_FILE_SIZE + " 字节限制");
        }

        List<DailyBarImportResultVO.RowError> errors = new ArrayList<>();
        List<StockDailyBarDO> toInsert = new ArrayList<>();
        List<StockDailyBarDO> toUpdate = new ArrayList<>();
        int skipped = 0;
        // 文件内幂等键 → 第一次解析到的 bar（用于检测后续冲突）
        Map<String, StockDailyBarDO> fileSeen = new LinkedHashMap<>();
        // 预加载的 stock_basic 缓存
        Map<String, StockBasicDO> stockMap = new HashMap<>();
        // 预加载的 DB existing bar 缓存
        Map<String, StockDailyBarDO> dbExistingMap = new HashMap<>();

        // 手动读取并校验首行表头（兼容 UTF-8 BOM）
        BufferedReader headerReader;
        try {
            headerReader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            headerReader.mark(8);
            int firstChar = headerReader.read();
            if (firstChar != 0xFEFF) {
                headerReader.reset();
            }
            String headerLine = headerReader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new BusinessException(ErrorCodeEnum.CSV_EMPTY_FILE, "CSV 文件为空或仅有空行");
            }
            // 严格匹配表头：数量、名称、顺序完全一致
            String[] actualHeaders = headerLine.split(",", -1);
            String[] expectedHeaders = MarketDataConstants.CSV_HEADERS;
            if (actualHeaders.length != expectedHeaders.length) {
                throw new BusinessException(ErrorCodeEnum.CSV_WRONG_HEADER,
                        "CSV 表头列数不匹配，期望 " + expectedHeaders.length + " 列: "
                        + String.join(",", expectedHeaders) + "，实际: " + headerLine);
            }
            for (int i = 0; i < expectedHeaders.length; i++) {
                if (!expectedHeaders[i].equals(actualHeaders[i].trim())) {
                    throw new BusinessException(ErrorCodeEnum.CSV_WRONG_HEADER,
                            "CSV 表头第 " + (i + 1) + " 列不匹配，期望: " + expectedHeaders[i]
                            + "，实际: " + actualHeaders[i].trim());
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.DAILY_BAR_CSV_PARSE_ERROR, "CSV 读取失败: " + e.getMessage());
        }

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader(MarketDataConstants.CSV_HEADERS)
                     .build().parse(headerReader)) {

            int rowNum = 1;
            for (CSVRecord record : parser) {
                rowNum++;
                if (rowNum - 1 > MarketDataConstants.MAX_ROWS) {
                    errors.add(new DailyBarImportResultVO.RowError(rowNum,
                            "超过最大行数限制 " + MarketDataConstants.MAX_ROWS));
                    break;
                }
                try {
                    StockDailyBarDO bar = parseAndValidateRow(record, rowNum, stockMap);
                    String uniqueKey = bar.getCanonicalSymbol() + "|" + bar.getTradeDate() + "|"
                            + bar.getAdjustType() + "|" + bar.getDataSource();

                    // 文件内重复键检测
                    if (fileSeen.containsKey(uniqueKey)) {
                        StockDailyBarDO firstSeen = fileSeen.get(uniqueKey);
                        if (isSameData(firstSeen, bar)) {
                            skipped++; // 内容一致，跳过
                        } else {
                            errors.add(new DailyBarImportResultVO.RowError(rowNum,
                                    "同一文件内幂等键冲突: " + uniqueKey
                                    + "（行内容与第 " + firstSeen.getTradeDate() + " 行不一致）"));
                        }
                        continue;
                    }
                    fileSeen.put(uniqueKey, bar);

                    // 查 DB existing（缓存避免重复查询）
                    StockDailyBarDO existing = dbExistingMap.computeIfAbsent(uniqueKey, k ->
                            stockDailyBarMapper.selectByUniqueKey(
                                    bar.getCanonicalSymbol(), bar.getTradeDate(),
                                    bar.getAdjustType(), bar.getDataSource()));

                    if (existing == null) {
                        toInsert.add(bar);
                    } else if (isSameData(existing, bar)) {
                        skipped++;
                    } else {
                        toUpdate.add(bar);
                    }
                } catch (BusinessException e) {
                    throw e; // 表头级错误向上抛
                } catch (Exception e) {
                    errors.add(new DailyBarImportResultVO.RowError(rowNum, e.getMessage()));
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.DAILY_BAR_CSV_PARSE_ERROR,
                    "CSV 解析失败: " + e.getMessage());
        }

        // 原子提交：任意错误整批不写库
        if (!errors.isEmpty()) {
            return new DailyBarImportResultVO(0, 0, 0, errors.size(), errors);
        }
        LocalDateTime now = LocalDateTime.now();
        for (StockDailyBarDO bar : toInsert) {
            bar.setFetchedAt(now);
        }
        for (StockDailyBarDO bar : toUpdate) {
            bar.setFetchedAt(now);
        }
        if (!toInsert.isEmpty()) stockDailyBarMapper.batchInsert(toInsert);
        for (StockDailyBarDO bar : toUpdate) stockDailyBarMapper.updateByUniqueKey(bar);

        return new DailyBarImportResultVO(toInsert.size(), toUpdate.size(), skipped, 0, errors);
    }

    /** 删除前检查是否有关联日 K 数据。 */
    public void ensureNoDailyBars(String canonicalSymbol) {
        long count = stockDailyBarMapper.countByCanonicalSymbol(canonicalSymbol);
        if (count > 0) {
            throw new BusinessException(ErrorCodeEnum.STOCK_HAS_DAILY_BARS,
                    "证券 " + canonicalSymbol + " 存在 " + count + " 条日 K 数据，不可删除");
        }
    }

    // ==================== 私有方法 ====================

    private StockDailyBarDO parseAndValidateRow(CSVRecord record, int rowNum,
                                                  Map<String, StockBasicDO> stockMap) {
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

        return StockDailyBarDO.builder()
                .canonicalSymbol(canonicalSymbol).tradeDate(tradeDate)
                .adjustType(adjustType).dataSource(MarketDataConstants.DATA_SOURCE_CSV)
                .openPrice(open).highPrice(high).lowPrice(low).closePrice(close)
                .volume(volume).amount(amount).build();
    }

    private void validateCanonicalSymbol(String symbol) {
        if (!symbol.matches(MarketDataConstants.CANONICAL_SYMBOL_REGEX)) {
            throw new IllegalArgumentException("canonical_symbol 格式不合法: " + symbol);
        }
    }

    private void validateAdjustType(String adjustType) {
        if (!MarketDataConstants.VALID_ADJUST_TYPES.contains(adjustType)) {
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

    private boolean isSameData(StockDailyBarDO a, StockDailyBarDO b) {
        return a.getOpenPrice().compareTo(b.getOpenPrice()) == 0
                && a.getHighPrice().compareTo(b.getHighPrice()) == 0
                && a.getLowPrice().compareTo(b.getLowPrice()) == 0
                && a.getClosePrice().compareTo(b.getClosePrice()) == 0
                && a.getVolume() != null && a.getVolume().equals(b.getVolume())
                && a.getAmount().compareTo(b.getAmount()) == 0;
    }
}
