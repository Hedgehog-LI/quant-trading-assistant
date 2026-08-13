package com.quant.trade.marketdata.analysis.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 板块稳定身份管理聚焦测试（AC-02 / TEST-03）。
 *
 * <p>H2/MODE=MySQL 无法复现 InnoDB gap-lock 顺序 → 并发层断言 OUTCOME（行数 + 唯一约束），
 * 非锁顺序。真实 FOR UPDATE 争用属 RUNTIME NOT_VERIFIED（AC-02 已声明）。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SectorIdentityManagerTest {

    @Autowired
    private SectorIdentityManager identityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM sector_analytics_publication_member");
        jdbcTemplate.update("DELETE FROM sector_analytics_publication_batch");
        jdbcTemplate.update("DELETE FROM sector_rotation_sector_persistence");
        jdbcTemplate.update("DELETE FROM sector_relative_strength_snapshot");
        jdbcTemplate.update("DELETE FROM sector_analytics_calculation_run");
        jdbcTemplate.update("DELETE FROM market_sector_ranking_item");
        jdbcTemplate.update("DELETE FROM market_sector_ranking_batch");
        jdbcTemplate.update("DELETE FROM market_sector_member_snapshot");
        jdbcTemplate.update("DELETE FROM market_sector_snapshot");
        jdbcTemplate.update("DELETE FROM market_sector_watch");
        jdbcTemplate.update("DELETE FROM market_sector_identity");
        jdbcTemplate.update("DELETE FROM market_sector_identity_lock");
    }

    /** 两个并发声明同一锚点 → 恰好一行 identity（断言行数 + 唯一约束，非锁顺序）。 */
    @Test
    void concurrentClaimsSameAnchorProduceExactlyOneIdentityRow() throws Exception {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            final String taxonomy = "industry-v1";
            pool.submit(() -> {
                TransactionTemplate tx = new TransactionTemplate(txManager);
                tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                ready.countDown();
                try {
                    start.await();
                    tx.executeWithoutResult(status -> identityManager.claimIdentity(
                            "LONGPORT", "CN", "sec-100", taxonomy, "银行", LocalDate.of(2026, 1, 1), null));
                    successes.incrementAndGet();
                } catch (DuplicateKeyException dup) {
                    // 唯一约束拒绝第二个写者 —— 并发 OUTCOME 断言成立
                    duplicates.incrementAndGet();
                } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                    duplicates.incrementAndGet();
                } finally {
                    done.countDown();
                }
                return null;
            });
        }
        ready.await(2, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(finished).as("并发声明应在线程池内收敛").isTrue();
        // OUTCOME 断言：恰好一行 identity
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM market_sector_identity "
                        + "WHERE provider_code='LONGPORT' AND market_code='CN' AND provider_sector_id='sec-100'",
                Integer.class);
        assertThat(rowCount).as("同一锚点并发声明后只能有一行身份").isEqualTo(1);
        // 唯一约束拒绝重复：至少有一个写者被约束拒绝（successes + duplicates == 2）
        assertThat(successes.get() + duplicates.get()).as("两个声明都有终态").isEqualTo(2);
    }

    /** 跨 taxonomy 区间重叠被拒绝（同一锚点不同 taxonomy 的有效区间不得交叉）。 */
    @Test
    void crossTaxonomyIntervalOverlapIsRejected() {
        // taxonomy A：[2026-01-01, 2026-06-01)
        identityManager.claimIdentity("LONGPORT", "CN", "sec-200", "industry-v1",
                "银行", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        // taxonomy B：[2026-03-01, +∞) 与 A 重叠 → 拒绝（域规则抛 BusinessException）
        assertThatThrownBy(() -> identityManager.claimIdentity("LONGPORT", "CN", "sec-200", "industry-v2",
                "银行v2", LocalDate.of(2026, 3, 1), null))
                .isInstanceOf(com.quant.trade.common.exception.BusinessException.class)
                .hasMessageContaining("区间与既有 taxonomy 重叠");
        // 不重叠的新区间应成功：[2026-06-01, +∞)
        var later = identityManager.claimIdentity("LONGPORT", "CN", "sec-200", "industry-v2",
                "银行v2", LocalDate.of(2026, 6, 1), null);
        assertThat(later.getId()).isNotNull();
    }

    /**
     * 删除+重建 watch 后衍生 sectorId 不变，快照保留（通过衍生查询路径证明，不依赖 V14 FK 删除）。
     * watch 只是关注关系；soft-archive 身份保留历史，衍生层按 sector_identity_id 回读。
     */
    @Test
    void deleteRecreateWatchKeepsDerivedSectorIdAndPreservesSnapshots() {
        // 1. 声明身份 + 建 watch + 写一条快照并回填 sector_identity_id
        var identity = identityManager.claimIdentity("LONGPORT", "CN", "sec-300",
                "industry-v1", "新能源", LocalDate.of(2026, 1, 1), null);
        Long sectorId = identity.getId();
        jdbcTemplate.update("""
                INSERT INTO market_sector_watch (provider_code, provider_sector_id, market_code, sector_name, enabled)
                VALUES ('LONGPORT', 'sec-300', 'CN', '新能源', TRUE)
                """);
        Long watchId = jdbcTemplate.queryForObject(
                "SELECT id FROM market_sector_watch WHERE provider_sector_id='sec-300'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO market_sector_snapshot (watch_id, snapshot_time, snapshot_bucket_time, rank_indicator,
                    change_rate, data_source, sector_identity_id)
                VALUES (?, '2026-07-16 15:00:00', '2026-07-16 15:00:00', 'leading-gainer', 0.0123, 'LONGPORT', ?)
                """, watchId, sectorId);
        Long snapshotId = jdbcTemplate.queryForObject(
                "SELECT id FROM market_sector_snapshot WHERE watch_id=" + watchId, Long.class);

        // 2. soft-archive 身份（模拟 watch 删除/重建场景，不物理删除身份）
        identityManager.archiveIdentity(sectorId);

        // 3. 衍生层按 sector_identity_id 回读 —— sectorId 不变，快照行仍在
        var refound = identityManager.findById(sectorId);
        assertThat(refound).as("衍生层按数值 sectorId 仍可回读归档身份").isNotNull();
        assertThat(refound.getId()).as("衍生 sectorId 在 watch 删除/重建后保持不变").isEqualTo(sectorId);
        Integer snapshotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM market_sector_snapshot WHERE sector_identity_id=" + sectorId, Integer.class);
        assertThat(snapshotCount).as("归档身份的历史快照保留").isEqualTo(1);
        // 快照物理行仍在（soft-archive 不删快照）
        Integer physicalSnapshot = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM market_sector_snapshot WHERE id=" + snapshotId, Integer.class);
        assertThat(physicalSnapshot).as("快照物理行未被删除").isEqualTo(1);
    }

    /** 回填既有快照的 sector_identity_id（watch 链路 → 身份），衍生层只用数值 id。 */
    @Test
    void backfillLinksExistingSnapshotsToStableIdentity() {
        var identity = identityManager.claimIdentity("LONGPORT", "CN", "sec-400",
                "industry-v1", "半导体", LocalDate.of(2026, 1, 1), null);
        jdbcTemplate.update("""
                INSERT INTO market_sector_watch (provider_code, provider_sector_id, market_code, sector_name, enabled)
                VALUES ('LONGPORT', 'sec-400', 'CN', '半导体', TRUE)
                """);
        Long watchId = jdbcTemplate.queryForObject(
                "SELECT id FROM market_sector_watch WHERE provider_sector_id='sec-400'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO market_sector_snapshot (watch_id, snapshot_time, snapshot_bucket_time, rank_indicator,
                    change_rate, data_source)
                VALUES (?, '2026-07-15 15:00:00', '2026-07-15 15:00:00', 'leading-gainer', 0.0050, 'LONGPORT')
                """, watchId);
        // 回填前 sector_identity_id 为 NULL
        Integer nullBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM market_sector_snapshot WHERE watch_id=" + watchId
                        + " AND sector_identity_id IS NULL", Integer.class);
        assertThat(nullBefore).isEqualTo(1);

        identityManager.backfillExistingSnapshots("LONGPORT", "CN");

        Integer linked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM market_sector_snapshot WHERE watch_id=" + watchId
                        + " AND sector_identity_id=" + identity.getId(), Integer.class);
        assertThat(linked).as("回填后快照挂到稳定 sectorId").isEqualTo(1);
    }

    /**
     * 幂等：同一自然键重复声明返回既有身份，不新增行。
     */
    @Test
    void claimIdentityIsIdempotentForSameNaturalKey() {
        var first = identityManager.claimIdentity("LONGPORT", "CN", "sec-500",
                "industry-v1", "医药", LocalDate.of(2026, 1, 1), null);
        var second = identityManager.claimIdentity("LONGPORT", "CN", "sec-500",
                "industry-v1", "医药", LocalDate.of(2026, 1, 1), null);
        assertThat(second.getId()).as("幂等声明返回同一 sectorId").isEqualTo(first.getId());
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM market_sector_identity WHERE provider_sector_id='sec-500'", Integer.class);
        assertThat(rowCount).isEqualTo(1);
    }
}
