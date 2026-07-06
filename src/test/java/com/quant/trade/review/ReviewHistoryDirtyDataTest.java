package com.quant.trade.review;

import com.quant.trade.review.manager.ReviewManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 复盘历史脏数据兼容测试（v0.1.1 收尾修复）。
 * <p>
 * linked_journal_ids 历史值可能含空段、非法 ID、重复 ID；解析必须容忍且不抛 500，
 * 与 ReviewNoteConverter.stringToIds 口径一致（trim/跳过空段/跳过非法/去重保序）。
 */
class ReviewHistoryDirtyDataTest {

    private final ReviewManager reviewManager = new ReviewManager(null, null);

    @Test
    void nullAndBlankReturnEmpty() {
        assertEquals(List.of(), reviewManager.parseLinkedIds(null));
        assertEquals(List.of(), reviewManager.parseLinkedIds(""));
        assertEquals(List.of(), reviewManager.parseLinkedIds("   "));
    }

    @Test
    void skipsIllegalSegments() {
        assertEquals(List.of(1L, 2L, 3L), reviewManager.parseLinkedIds("1,abc,,2,1,3"));
    }

    @Test
    void dedupesAndKeepsOrder() {
        assertEquals(List.of(5L), reviewManager.parseLinkedIds("5,xyz,5"));
        assertEquals(List.of(7L, 8L), reviewManager.parseLinkedIds("7,8,7,8"));
    }

    @Test
    void singleAndAllIllegal() {
        assertEquals(List.of(1L), reviewManager.parseLinkedIds("1"));
        assertEquals(List.of(), reviewManager.parseLinkedIds("abc,def"));
    }
}
