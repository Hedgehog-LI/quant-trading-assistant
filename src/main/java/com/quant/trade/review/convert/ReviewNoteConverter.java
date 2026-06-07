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
        if (ids == null || ids.isEmpty()) {
            return null;
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
        return List.of(str.split(",")).stream()
                .map(String::trim)
                .map(Long::valueOf)
                .toList();
    }
}
