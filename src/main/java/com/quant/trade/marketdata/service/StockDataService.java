package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.convert.StockDataConverter;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.dao.StockDailyBarMapper;
import com.quant.trade.marketdata.dto.CreateStockBasicDTO;
import com.quant.trade.marketdata.dto.DailyBarQueryDTO;
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

    public List<StockBasicVO> listStocks(String market, String keyword) {
        return converter.toVOList(stockBasicMapper.selectByFilter(market, keyword));
    }

    public StockBasicVO getStock(String canonicalSymbol) {
        StockBasicDO record = stockBasicMapper.selectByCanonicalSymbol(canonicalSymbol);
        if (record == null) throw new BusinessException(ErrorCodeEnum.STOCK_NOT_FOUND, canonicalSymbol);
        return converter.toVO(record);
    }

    @Transactional
    public StockBasicVO updateStock(Long id, String name, java.time.LocalDate listDate, Boolean delisted) {
        StockBasicDO existing = stockBasicMapper.selectById(id);
        if (existing == null) throw new BusinessException(ErrorCodeEnum.STOCK_NOT_FOUND, "id=" + id);
        if (name != null) existing.setName(name);
        if (listDate != null) existing.setListDate(listDate);
        if (delisted != null) existing.setDelisted(delisted);
        stockBasicMapper.updateById(existing);
        return converter.toVO(stockBasicMapper.selectById(id));
    }

    @Transactional
    public void deleteStock(String canonicalSymbol) {
        stockBasicMapper.deleteByCanonicalSymbol(canonicalSymbol);
    }

    public List<StockDailyBarVO> queryDailyBars(DailyBarQueryDTO query) {
        List<StockDailyBarDO> records = stockDailyBarMapper.selectByFilter(
                query.canonicalSymbol(), query.fromDate(), query.toDate(), query.adjustType());
        return records.stream().map(converter::toBarVO).toList();
    }

    @Transactional
    public DailyBarImportResultVO importDailyBars(InputStream input) {
        return manager.importDailyBarsCsv(input);
    }
}
