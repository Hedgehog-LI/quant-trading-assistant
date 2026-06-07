package com.quant.trade.tradeplan.convert;

import com.quant.trade.tradeplan.dto.CreateTradePlanDTO;
import com.quant.trade.tradeplan.dto.UpdateTradePlanDTO;
import com.quant.trade.tradeplan.model.TradePlanDO;
import com.quant.trade.tradeplan.vo.TradePlanVO;
import org.mapstruct.*;

import java.util.List;

/**
 * 交易计划对象转换器。
 */
@Mapper(componentModel = "spring")
public interface TradePlanConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TradePlanDO toDO(CreateTradePlanDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "planDate", ignore = true)
    @Mapping(target = "symbol", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateDOFromDTO(UpdateTradePlanDTO dto, @MappingTarget TradePlanDO record);

    TradePlanVO toVO(TradePlanDO record);

    List<TradePlanVO> toVOList(List<TradePlanDO> records);
}
