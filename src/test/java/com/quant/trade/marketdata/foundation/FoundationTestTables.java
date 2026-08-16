package com.quant.trade.marketdata.foundation;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 数据底座测试公共表清理（外键子表先删）。
 * foundation 服务写库走 txRequiresNew 独立提交，@Transactional 回滚不可靠，
 * 因此非事务测试类在 @BeforeEach/@AfterEach 显式清空相关表（H2 为同类测试共享库）。
 */
final class FoundationTestTables {

    private FoundationTestTables() {
    }

    static void cleanAll(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM mdf_backfill_chunk");
        jdbcTemplate.update("DELETE FROM mdf_backfill_task");
        jdbcTemplate.update("DELETE FROM mdf_coverage_watermark");
        jdbcTemplate.update("DELETE FROM mdf_quality_result");
        jdbcTemplate.update("DELETE FROM mdf_dataset_version");
        jdbcTemplate.update("DELETE FROM mdf_dataset");
        jdbcTemplate.update("DELETE FROM mdf_import_batch");
        jdbcTemplate.update("DELETE FROM mdf_universe_snapshot");
        jdbcTemplate.update("DELETE FROM mdf_industry_membership");
        jdbcTemplate.update("DELETE FROM mdf_industry_taxonomy");
        jdbcTemplate.update("DELETE FROM stock_daily_bar");
        jdbcTemplate.update("DELETE FROM market_calendar");
        jdbcTemplate.update("DELETE FROM stock_alias");
        jdbcTemplate.update("DELETE FROM stock_basic");
    }
}
