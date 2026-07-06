package com.quant.trade.review.convert;

import com.quant.trade.review.dto.CreateReviewDTO;
import com.quant.trade.review.dto.UpdateReviewDTO;
import com.quant.trade.review.model.ReviewNoteDO;
import com.quant.trade.review.vo.ReviewVO;
import org.mapstruct.*;

import java.util.List;

/**
 * 复盘记录对象转换器。
 * <p>
 * 处理 linkedJournalIds 在 List&lt;Long&gt; 和逗号字符串之间的转换。
 */
@Mapper(componentModel = "spring")
public interface ReviewNoteConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "linkedJournalIds", qualifiedByName = "idsToString")
    ReviewNoteDO toDO(CreateReviewDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "reviewDate", ignore = true)
    @Mapping(target = "symbol", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "linkedJournalIds", qualifiedByName = "idsToString")
    void updateDOFromDTO(UpdateReviewDTO dto, @MappingTarget ReviewNoteDO record);

    @Mapping(target = "linkedJournalIds", qualifiedByName = "stringToIds")
    ReviewVO toVO(ReviewNoteDO record);

    @Mapping(target = "linkedJournalIds", qualifiedByName = "stringToIds")
    List<ReviewVO> toVOList(List<ReviewNoteDO> records);

    // ==================== 自定义 ID 列表转换 ====================

    @Named("idsToString")
    default String idsToString(List<Long> ids) {
        if (ids == null) {
            // null 表示"未提供"，保持 null 以支持 update 的 IGNORE 部分更新语义。
            return null;
        }
        if (ids.isEmpty()) {
            // 空列表序列化为空串，确保 updateDOFromDTO 能把 existing.linkedJournalIds 覆盖为 ""，
            // 进而经 Mapper xml 的 if(#{linkedJournalIds} != null) 写入 DB 完成清空。
            // 若返回 null，会被 IGNORE 与 xml if 双重跳过，导致无法移除关联。
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    @Named("stringToIds")
    default List<Long> stringToIds(String str) {
        if (str == null || str.isBlank()) {
            return List.of();
        }
        // 容忍历史脏数据：trim、跳过空段、跳过非法、去重保序；与 ReviewManager.parseLinkedIds 口径一致
        return List.of(str.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.valueOf(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }
}
