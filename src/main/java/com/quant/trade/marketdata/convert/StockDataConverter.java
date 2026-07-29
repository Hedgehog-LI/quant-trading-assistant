package com.quant.trade.marketdata.convert;

import com.quant.trade.marketdata.dto.CreateStockBasicDTO;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import com.quant.trade.marketdata.vo.StockBasicVO;
import com.quant.trade.marketdata.vo.StockDailyBarVO;
import org.mapstruct.*;

import java.util.List;

/** 行情数据对象转换器。 */
@Mapper(componentModel = "spring")
public interface StockDataConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "canonicalSymbol", ignore = true)
    @Mapping(target = "delisted", ignore = true)
    @Mapping(target = "nameCn", ignore = true)
    @Mapping(target = "nameHk", ignore = true)
    @Mapping(target = "nameEn", ignore = true)
    @Mapping(target = "shortName", ignore = true)
    @Mapping(target = "pinyinFull", ignore = true)
    @Mapping(target = "pinyinAbbr", ignore = true)
    @Mapping(target = "exchange", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "securityType", ignore = true)
    @Mapping(target = "listStatus", ignore = true)
    @Mapping(target = "dataSource", ignore = true)
    @Mapping(target = "sourceUpdatedAt", ignore = true)
    @Mapping(target = "sourceHash", ignore = true)
    StockBasicDO toDO(CreateStockBasicDTO dto);

    StockBasicVO toVO(StockBasicDO record);

    List<StockBasicVO> toVOList(List<StockBasicDO> records);

    StockDailyBarVO toBarVO(StockDailyBarDO record);
}
