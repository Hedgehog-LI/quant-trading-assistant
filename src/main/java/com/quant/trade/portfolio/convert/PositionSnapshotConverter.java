package com.quant.trade.portfolio.convert;

import com.quant.trade.portfolio.dto.CreatePositionSnapshotDTO;
import com.quant.trade.portfolio.dto.PositionSnapshotItemDTO;
import com.quant.trade.portfolio.dto.UpdatePositionSnapshotDTO;
import com.quant.trade.portfolio.model.PositionSnapshotDO;
import com.quant.trade.portfolio.model.PositionSnapshotItemDO;
import com.quant.trade.portfolio.vo.PositionSnapshotDetailVO;
import com.quant.trade.portfolio.vo.PositionSnapshotItemVO;
import com.quant.trade.portfolio.vo.PositionSnapshotSummaryVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * 持仓快照 DTO、DO 与 VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface PositionSnapshotConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "totalCostAmount", ignore = true)
    @Mapping(target = "totalMarketValue", ignore = true)
    @Mapping(target = "totalUnrealizedPnl", ignore = true)
    @Mapping(target = "totalPnlRate", ignore = true)
    @Mapping(target = "positionCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PositionSnapshotDO toDO(CreatePositionSnapshotDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sourceType", ignore = true)
    @Mapping(target = "snapshotStatus", ignore = true)
    @Mapping(target = "totalCostAmount", ignore = true)
    @Mapping(target = "totalMarketValue", ignore = true)
    @Mapping(target = "totalUnrealizedPnl", ignore = true)
    @Mapping(target = "totalPnlRate", ignore = true)
    @Mapping(target = "positionCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateDOFromDTO(UpdatePositionSnapshotDTO dto, @MappingTarget PositionSnapshotDO record);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "snapshotId", ignore = true)
    @Mapping(target = "costAmount", ignore = true)
    @Mapping(target = "marketValue", ignore = true)
    @Mapping(target = "unrealizedPnl", ignore = true)
    @Mapping(target = "pnlRate", ignore = true)
    @Mapping(target = "positionRatio", ignore = true)
    @Mapping(target = "sortOrder", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PositionSnapshotItemDO toItemDO(PositionSnapshotItemDTO dto);

    List<PositionSnapshotItemDO> toItemDOList(List<PositionSnapshotItemDTO> dtos);

    PositionSnapshotSummaryVO toSummaryVO(PositionSnapshotDO record);

    List<PositionSnapshotSummaryVO> toSummaryVOList(List<PositionSnapshotDO> records);

    PositionSnapshotItemVO toItemVO(PositionSnapshotItemDO record);

    @Mapping(target = "id", source = "snapshot.id")
    @Mapping(target = "snapshotDate", source = "snapshot.snapshotDate")
    @Mapping(target = "snapshotTime", source = "snapshot.snapshotTime")
    @Mapping(target = "snapshotName", source = "snapshot.snapshotName")
    @Mapping(target = "sourceType", source = "snapshot.sourceType")
    @Mapping(target = "snapshotStatus", source = "snapshot.snapshotStatus")
    @Mapping(target = "totalCostAmount", source = "snapshot.totalCostAmount")
    @Mapping(target = "totalMarketValue", source = "snapshot.totalMarketValue")
    @Mapping(target = "totalUnrealizedPnl", source = "snapshot.totalUnrealizedPnl")
    @Mapping(target = "totalPnlRate", source = "snapshot.totalPnlRate")
    @Mapping(target = "positionCount", source = "snapshot.positionCount")
    @Mapping(target = "remark", source = "snapshot.remark")
    @Mapping(target = "createdAt", source = "snapshot.createdAt")
    @Mapping(target = "updatedAt", source = "snapshot.updatedAt")
    @Mapping(target = "items", source = "items")
    PositionSnapshotDetailVO toDetailVO(PositionSnapshotDO snapshot,
                                        List<PositionSnapshotItemDO> items);
}
