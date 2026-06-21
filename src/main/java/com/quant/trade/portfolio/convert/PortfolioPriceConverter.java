package com.quant.trade.portfolio.convert;

import com.quant.trade.portfolio.dto.PriceSnapshotDTO;
import com.quant.trade.portfolio.model.PortfolioPriceSnapshotDO;
import com.quant.trade.portfolio.vo.PriceSnapshotVO;
import org.mapstruct.*;

import java.util.List;

/**
 * 手工当前价快照对象转换器。
 */
@Mapper(componentModel = "spring")
public interface PortfolioPriceConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PortfolioPriceSnapshotDO toDO(PriceSnapshotDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "symbol", ignore = true)
    @Mapping(target = "priceDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateDOFromDTO(PriceSnapshotDTO dto, @MappingTarget PortfolioPriceSnapshotDO record);

    PriceSnapshotVO toVO(PortfolioPriceSnapshotDO record);

    List<PriceSnapshotVO> toVOList(List<PortfolioPriceSnapshotDO> records);
}
