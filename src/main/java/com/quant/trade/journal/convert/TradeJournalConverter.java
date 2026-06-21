package com.quant.trade.journal.convert;

import com.quant.trade.common.util.TagUtils;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.dto.UpdateTradeJournalDTO;
import com.quant.trade.journal.flow.TradeFlowItem;
import com.quant.trade.journal.model.TradeJournalDO;
import com.quant.trade.journal.vo.TradeJournalVO;
import org.mapstruct.*;

import java.util.List;

/**
 * 交易记录对象转换器。
 * <p>
 * 处理 emotionTags/mistakeTags 在 List 和逗号字符串之间的转换。
 * amount 为纯自动计算字段，转换时忽略；
 * totalFee 为条件计算字段（传入则以传入值为准，否则由 Manager 求和），需透传 DTO 值，
 * 其中 update 时 totalFee 为 null 要覆盖旧值（SET_TO_NULL）以触发重算。
 */
@Mapper(componentModel = "spring")
public interface TradeJournalConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "amount", ignore = true)
    @Mapping(target = "reviewStatus", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "emotionTags", qualifiedByName = "tagsToString")
    @Mapping(target = "mistakeTags", qualifiedByName = "tagsToString")
    TradeJournalDO toDO(CreateTradeJournalDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "tradeDate", ignore = true)
    @Mapping(target = "tradeTime", ignore = true)
    @Mapping(target = "symbol", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "amount", ignore = true)
    @Mapping(target = "totalFee", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "emotionTags", qualifiedByName = "tagsToString")
    @Mapping(target = "mistakeTags", qualifiedByName = "tagsToString")
    void updateDOFromDTO(UpdateTradeJournalDTO dto, @MappingTarget TradeJournalDO record);

    @Mapping(target = "emotionTags", qualifiedByName = "stringToTags")
    @Mapping(target = "mistakeTags", qualifiedByName = "stringToTags")
    @Mapping(target = "warnings", ignore = true)
    TradeJournalVO toVO(TradeJournalDO record);

    @Mapping(target = "emotionTags", qualifiedByName = "stringToTags")
    @Mapping(target = "mistakeTags", qualifiedByName = "stringToTags")
    @Mapping(target = "warnings", ignore = true)
    List<TradeJournalVO> toVOList(List<TradeJournalDO> records);

    // ==================== Portfolio 流水契约转换 ====================

    /**
     * 转换为 portfolio FIFO 计算器入参契约（扁平纯数据）。
     */
    TradeFlowItem toFlowItem(TradeJournalDO record);

    /**
     * 批量转换为 portfolio FIFO 计算器入参契约。
     */
    List<TradeFlowItem> toFlowItemList(List<TradeJournalDO> records);

    // ==================== 自定义 tag 转换方法 ====================

    @Named("tagsToString")
    default String tagsToString(List<String> tags) {
        return TagUtils.toString(tags);
    }

    @Named("stringToTags")
    default List<String> stringToTags(String tagStr) {
        return TagUtils.fromString(tagStr);
    }
}
