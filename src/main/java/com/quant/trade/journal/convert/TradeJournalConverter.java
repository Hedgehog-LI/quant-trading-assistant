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
 * 费用字段（commissionFee/stampTax/transferFee/otherFee/totalFee）在 update 时为「全量编辑语义」：
 * DTO 中的 null 表示「清空」，通过 SET_TO_NULL 透传到 DO，再由 Manager.fillFees 统一归一为 0 并重算 totalFee；
 * 非 null 值则原样透传，由 Manager 归一落库（与 MyBatis 非 null 更新配合，支持清空费用）。
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
    // 费用字段为全量编辑语义：DTO null 表示「清空」，用 SET_TO_NULL 透传到 DO，
    // 再由 Manager.fillFees 归一为 0 并写入 DB（MyBatis 非 null 即更新）。
    @Mapping(target = "commissionFee", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "stampTax", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "transferFee", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "otherFee", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
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
