package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.convert.StockDataConverter;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.dao.StockDailyBarMapper;
import com.quant.trade.marketdata.dto.CreateStockBasicDTO;
import com.quant.trade.marketdata.dto.DailyBarQueryDTO;
import com.quant.trade.marketdata.dto.UpdateStockBasicDTO;
import com.quant.trade.marketdata.manager.StockDataManager;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import com.quant.trade.marketdata.vo.DailyBarImportResultVO;
import com.quant.trade.marketdata.vo.StockBasicVO;
import com.quant.trade.marketdata.vo.StockDailyBarVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** 行情数据应用服务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDataService {

    private final StockBasicMapper stockBasicMapper;
    private final StockDailyBarMapper stockDailyBarMapper;
    private final StockDataConverter converter;
    private final StockDataManager manager;

    @Transactional
    public StockBasicVO createStock(CreateStockBasicDTO dto) {
        String canonical = manager.buildCanonicalSymbol(dto.market(), dto.symbol());
        if (stockBasicMapper.selectByCanonicalSymbol(canonical) != null) {
            throw new BusinessException(ErrorCodeEnum.STOCK_ALREADY_EXISTS, "证券已存在: " + canonical);
        }
        StockBasicDO record = converter.toDO(dto);
        record.setCanonicalSymbol(canonical);
        record.setDelisted(dto.delisted() != null && dto.delisted());
        stockBasicMapper.insert(record);
        return converter.toVO(record);
    }

    /** 分页查询证券主数据。 */
    public Map<String, Object> listStocks(String market, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        List<StockBasicVO> items = converter.toVOList(
                stockBasicMapper.selectByFilter(market, keyword, size, offset));
        long total = stockBasicMapper.countByFilter(market, keyword);
        return Map.of("items", items, "total", total, "page", page, "size", size);
    }

    public StockBasicVO getStock(String canonicalSymbol) {
        StockBasicDO record = stockBasicMapper.selectByCanonicalSymbol(canonicalSymbol);
        if (record == null) throw new BusinessException(ErrorCodeEnum.STOCK_NOT_FOUND, canonicalSymbol);
        return converter.toVO(record);
    }

    @Transactional
    public StockBasicVO updateStock(Long id, UpdateStockBasicDTO dto) {
        StockBasicDO existing = stockBasicMapper.selectById(id);
        if (existing == null) throw new BusinessException(ErrorCodeEnum.STOCK_NOT_FOUND, "id=" + id);
        if (dto.name() != null) existing.setName(dto.name());
        if (dto.listDate() != null) existing.setListDate(dto.listDate());
        if (dto.delisted() != null) existing.setDelisted(dto.delisted());
        stockBasicMapper.updateById(existing);
        return converter.toVO(stockBasicMapper.selectById(id));
    }

    /** 删除前检查是否有日 K 数据。 */
    @Transactional
    public void deleteStock(String canonicalSymbol) {
        StockBasicDO existing = stockBasicMapper.selectByCanonicalSymbol(canonicalSymbol);
        if (existing == null) throw new BusinessException(ErrorCodeEnum.STOCK_NOT_FOUND, canonicalSymbol);
        manager.ensureNoDailyBars(canonicalSymbol);
        stockBasicMapper.deleteByCanonicalSymbol(canonicalSymbol);
    }

    /** 分页查询日 K。 */
    public Map<String, Object> queryDailyBars(DailyBarQueryDTO query, int page, int size) {
        int offset = (page - 1) * size;
        List<StockDailyBarDO> records = stockDailyBarMapper.selectByFilter(
                query.canonicalSymbol(), query.fromDate(), query.toDate(),
                query.adjustType(), query.dataSource(), size, offset);
        long total = stockDailyBarMapper.countByFilter(
                query.canonicalSymbol(), query.fromDate(), query.toDate(),
                query.adjustType(), query.dataSource());
        List<StockDailyBarVO> items = records.stream().map(converter::toBarVO).toList();
        return Map.of("items", items, "total", total, "page", page, "size", size);
    }

    @Transactional
    public DailyBarImportResultVO importDailyBars(InputStream input, long fileSize) {
        return manager.importDailyBarsCsv(input, fileSize);
    }
}
