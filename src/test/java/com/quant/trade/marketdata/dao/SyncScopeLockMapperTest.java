package com.quant.trade.marketdata.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** SyncScopeLockMapper 测试，覆盖 upsert 和 selectForUpdate。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SyncScopeLockMapperTest {

    @Autowired
    private SyncScopeLockMapper syncScopeLockMapper;

    @Test
    void upsertInsertsAndIsIdempotent() {
        // 首次 insert
        syncScopeLockMapper.upsert("LONGPORT", "DAILY_BAR_SYNC", "test-hash-1");
        // 重复调用不报错（ON DUPLICATE KEY UPDATE）
        syncScopeLockMapper.upsert("LONGPORT", "DAILY_BAR_SYNC", "test-hash-1");
        // 不同 hash 也能插入
        syncScopeLockMapper.upsert("LONGPORT", "DAILY_BAR_SYNC", "test-hash-2");
        // 验证 selectForUpdate 返回 1（行存在）
        int count = syncScopeLockMapper.selectForUpdate("LONGPORT", "DAILY_BAR_SYNC", "test-hash-1");
        assertEquals(1, count);
    }

    @Test
    void selectForUpdateAfterUpsertReturnsRow() {
        syncScopeLockMapper.upsert("LONGPORT", "DAILY_BAR_SYNC", "test-hash-3");
        // 锁内查询必须能返回（行存在）
        int count = syncScopeLockMapper.selectForUpdate("LONGPORT", "DAILY_BAR_SYNC", "test-hash-3");
        assertEquals(1, count, "upsert 后 selectForUpdate 应返回 1（行存在）");
    }
}
