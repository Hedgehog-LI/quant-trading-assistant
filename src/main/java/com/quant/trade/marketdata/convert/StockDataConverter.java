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
    StockBasicDO toDO(CreateStockBasicDTO dto);

    StockBasicVO toVO(StockBasicDO record);

    List<StockBasicVO> toVOList(List<StockBasicDO> records);

    StockDailyBarVO toBarVO(StockDailyBarDO record);
}
