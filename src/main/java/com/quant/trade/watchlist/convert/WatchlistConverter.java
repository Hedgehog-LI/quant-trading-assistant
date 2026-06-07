package com.quant.trade.watchlist.convert;

import com.quant.trade.watchlist.dto.CreateWatchlistDTO;
import com.quant.trade.watchlist.dto.UpdateWatchlistDTO;
import com.quant.trade.watchlist.model.WatchlistDO;
import com.quant.trade.watchlist.vo.WatchlistVO;
import org.mapstruct.*;

import java.util.List;

/**
 * 自选股对象转换器。
 * <p>
 * 负责 DTO ↔ DO ↔ VO 之间的转换。
 */
@Mapper(componentModel = "spring")
public interface WatchlistConverter {

    /**
     * 创建 DTO 转 DO。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "enabled", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    WatchlistDO toDO(CreateWatchlistDTO dto);

    /**
     * 更新 DTO 合并到已有 DO（仅覆盖非 null 字段）。
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "symbol", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateDOFromDTO(UpdateWatchlistDTO dto, @MappingTarget WatchlistDO record);

    /**
     * DO 转 VO。
     */
    WatchlistVO toVO(WatchlistDO record);

    /**
     * DO 列表转 VO 列表。
     */
    List<WatchlistVO> toVOList(List<WatchlistDO> records);
}
