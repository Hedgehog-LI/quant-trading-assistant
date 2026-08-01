package com.quant.trade.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.config.SecurityDirectoryProperties;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.constant.SecurityDirectoryConstants;
import com.quant.trade.marketdata.controller.SecurityDirectorySyncController;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskMapper;
import com.quant.trade.marketdata.dao.SecurityDirectorySyncStateMapper;
import com.quant.trade.marketdata.dao.StockAliasMapper;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.dao.SyncScopeLockMapper;
import com.quant.trade.marketdata.model.SecurityDirectorySyncStateDO;
import com.quant.trade.marketdata.model.StockAliasDO;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.provider.csv.CsvSnapshotSecurityDirectoryProvider;
import com.quant.trade.marketdata.provider.csv.SecurityDirectoryCsvParser;
import com.quant.trade.marketdata.service.SecurityDirectoryService;
import com.quant.trade.marketdata.service.SecurityDirectorySyncService;
import com.quant.trade.marketdata.vo.MarketDataSyncTaskVO;
import com.quant.trade.marketdata.vo.SecurityDirectoryStatusVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * D3 证券目录同步集成测试。启用 CSV 快照 provider（指向 @TempDir 快照文件），覆盖
 * AC-02（五阶段/幂等/失败保留旧目录/质量门禁）、AC-03（status/sync API 语义）、AC-04（retry 链）。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "qta.market-data.security-directory.enabled=true",
        "qta.market-data.security-directory.row-count-swing-threshold=0.30"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityDirectorySyncIntegrationTest {

    private static final String HEADER = "canonical_symbol,name,market,exchange,currency,security_type,"
            + "list_status,data_source,source_updated_at,aliases\n";

    @Autowired
    private SecurityDirectorySyncService injectedSyncService;
    @Autowired
    private SecurityDirectoryService directoryService;
    @Autowired
    private SecurityDirectorySyncStateMapper syncStateMapper;
    @Autowired
    private StockBasicMapper stockBasicMapper;
    @Autowired
    private StockAliasMapper stockAliasMapper;
    @Autowired
    private MarketDataSyncTaskMapper taskMapper;
    @Autowired
    private SyncScopeLockMapper syncScopeLockMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SecurityDirectoryProperties properties;
    @Autowired
    @Qualifier("txRequiresNew")
    private TransactionTemplate txRequiresNew;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @SpyBean
    private StockBasicMapper stockBasicMapperSpy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM stock_alias");
        jdbcTemplate.update("DELETE FROM stock_basic");
        jdbcTemplate.update("DELETE FROM security_directory_sync_state");
        jdbcTemplate.update("DELETE FROM market_data_sync_task WHERE task_type='SECURITY_MASTER_SYNC'");
        jdbcTemplate.update("DELETE FROM market_data_sync_scope_lock WHERE task_type='SECURITY_MASTER_SYNC'");
        reset(stockBasicMapperSpy);
    }

    @AfterEach
    void tearDown() {
        reset(stockBasicMapperSpy);
    }

    private Path writeSnapshot(String suffix, String rows) {
        try {
            Path file = tempDir.resolve("snapshot_" + suffix + ".csv");
            Files.writeString(file, HEADER + rows, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private SecurityDirectorySyncService syncServiceWithSnapshot(Path snapshot) {
        return new SecurityDirectorySyncService(
                new CsvSnapshotSecurityDirectoryProvider(true,
                        SecurityDirectoryConstants.PROVIDER_CODE_CSV_SNAPSHOT_DIR, snapshot,
                        new SecurityDirectoryCsvParser()),
                syncStateMapper, taskMapper, syncScopeLockMapper,
                stockBasicMapper, stockAliasMapper, objectMapper, Clock.systemUTC(), txRequiresNew, 0.30d);
    }

    @Test
    void happyPathFullPublishPublishesStocksAndAliases() {
        String rows = """
                SH.603308,应流股份,SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,SHORT_NAME::应流
                HK.02498,速腾聚创,HK,HKEX,HKD,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,ENGLISH:en:RoboSense
                """;
        Path snapshot = writeSnapshot("valid", rows);
        MarketDataSyncTaskVO task = syncServiceWithSnapshot(snapshot).trigger("FULL");

        assertEquals(MarketDataConstants.TASK_STATUS_SUCCEEDED, task.status());
        assertEquals(2, task.insertedCount());
        assertEquals(0, task.updatedCount());
        List<StockBasicDO> stocks = stockBasicMapper.selectByCanonicalSymbols(
                List.of("SH.603308", "HK.02498"));
        assertEquals(2, stocks.size());
        SecurityDirectorySyncStateDO state = syncStateMapper.selectByProvider(
                SecurityDirectoryConstants.PROVIDER_CODE_CSV_SNAPSHOT_DIR);
        assertNotNull(state);
        assertNotNull(state.getLastSnapshotId());
        assertEquals("FULL", state.getLastMode());
        assertEquals(2, state.getLastInsertedCount());
    }

    @Test
    void idempotentReSyncOfSameSnapshotIsNoOp() {
        String rows = """
                SH.603308,应流股份,SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,
                """;
        Path snapshot = writeSnapshot("dup", rows);
        SecurityDirectorySyncService service = syncServiceWithSnapshot(snapshot);
        MarketDataSyncTaskVO first = service.trigger("FULL");
        assertEquals(1, first.insertedCount());
        MarketDataSyncTaskVO second = service.trigger("FULL");
        // 幂等短路：返回既有 SUCCEEDED task，不重复执行。
        assertEquals(first.id(), second.id());
        assertEquals(1, stockBasicMapper.countAll());
    }

    @Test
    void renameWritesExactlyOneFormerName() {
        jdbcTemplate.update("INSERT INTO stock_basic(canonical_symbol,symbol,name,market,exchange,currency,"
                + "security_type,list_status,data_source,source_updated_at,delisted) "
                + "VALUES('SH.603308','603308','应流科技','SH','SSE','CNY','STOCK','LISTED','SNAPSHOT',"
                + "'2026-07-01 00:00:00',false)");
        String rows = """
                SH.603308,应流股份,SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,
                """;
        Path snapshot = writeSnapshot("rename", rows);
        MarketDataSyncTaskVO task = syncServiceWithSnapshot(snapshot).trigger("FULL");

        assertEquals(MarketDataConstants.TASK_STATUS_SUCCEEDED, task.status());
        assertEquals(1, task.updatedCount());
        StockBasicDO updated = stockBasicMapper.selectByCanonicalSymbol("SH.603308");
        assertEquals("应流股份", updated.getName());
        List<StockAliasDO> aliases = stockAliasMapper.selectByStockBasicId(updated.getId());
        long formerNames = aliases.stream()
                .filter(a -> "FORMER_NAME".equals(a.getAliasType())).count();
        assertEquals(1, formerNames, "rename 应写入恰好一条 FORMER_NAME");
    }

    @Test
    void emptySnapshotRejectedAndCatalogPreserved() {
        jdbcTemplate.update("INSERT INTO stock_basic(canonical_symbol,symbol,name,market,exchange,currency,"
                + "security_type,list_status,data_source,source_updated_at,delisted) "
                + "VALUES('SH.603308','603308','应流股份','SH','SSE','CNY','STOCK','LISTED','SNAPSHOT',"
                + "'2026-07-01 00:00:00',false)");
        Path empty = writeSnapshot("empty", "");
        long beforeCount = stockBasicMapper.countAll();
        assertThrows(BusinessException.class, () -> syncServiceWithSnapshot(empty).trigger("FULL"));
        // 失败保留上一成功目录，list_status 未被修改。
        assertEquals(beforeCount, stockBasicMapper.countAll());
        StockBasicDO preserved = stockBasicMapper.selectByCanonicalSymbol("SH.603308");
        assertEquals("LISTED", preserved.getListStatus());
    }

    @Test
    void rowCountSwingRejectedAndCatalogPreserved() {
        for (int i = 0; i < 100; i++) {
            jdbcTemplate.update("INSERT INTO stock_basic(canonical_symbol,symbol,name,market,exchange,currency,"
                    + "security_type,list_status,data_source,source_updated_at,delisted) "
                    + "VALUES(?,?,?,?,?,?,?,?,'SNAPSHOT','2026-07-01 00:00:00',false)",
                    "SH.6000" + i, "6000" + i, "name" + i, "SH", "SSE", "CNY", "STOCK", "LISTED");
        }
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            rows.append("SH.6000").append(i).append(",name").append(i)
                    .append(",SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,\n");
        }
        Path snapshot = writeSnapshot("swing", rows.toString());
        assertThrows(BusinessException.class, () -> syncServiceWithSnapshot(snapshot).trigger("FULL"));
        assertEquals(100, stockBasicMapper.countAll());
    }

    @Test
    void forcedPublishFailurePreservesPreviousCatalog() {
        String rows = """
                SH.603308,应流股份,SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,
                HK.02498,速腾聚创,HK,HKEX,HKD,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,
                """;
        Path snapshot = writeSnapshot("force", rows);
        syncServiceWithSnapshot(snapshot).trigger("FULL");
        long beforeCount = stockBasicMapper.countAll();
        assertEquals(2, beforeCount);

        doThrow(new RuntimeException("forced publish failure"))
                .when(stockBasicMapperSpy).updateDirectoryById(any());
        String renamedRows = """
                SH.603308,应流新名,SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,
                HK.02498,速腾聚创,HK,HKEX,HKD,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,
                """;
        Path renamedSnapshot = writeSnapshot("renamed_force", renamedRows);
        assertThrows(BusinessException.class,
                () -> syncServiceWithSnapshot(renamedSnapshot).trigger("FULL"));
        assertEquals(beforeCount, stockBasicMapper.countAll());
        StockBasicDO preserved = stockBasicMapper.selectByCanonicalSymbol("SH.603308");
        assertEquals("应流股份", preserved.getName(), "失败不应修改名称");
    }

    @Test
    void failedRetryCreatesParentTaskChain() {
        String rows = """
                SH.603308,应流股份,SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,
                """;
        Path snapshot = writeSnapshot("retry", rows);
        SecurityDirectorySyncService service = syncServiceWithSnapshot(snapshot);
        doThrow(new RuntimeException("forced failure"))
                .when(stockBasicMapperSpy).insertDirectory(any());
        assertThrows(BusinessException.class, () -> service.trigger("FULL"));
        reset(stockBasicMapperSpy);
        MarketDataSyncTaskVO retry = service.trigger("FULL");
        assertEquals(MarketDataConstants.TASK_STATUS_SUCCEEDED, retry.status());
        assertNotNull(retry.parentTaskId(), "retry 应建立 parent_task_id 链");
    }

    @Test
    void statusReportsProviderConfiguredAndCatalogStatus() {
        SecurityDirectorySyncController controller = new SecurityDirectorySyncController(
                injectedSyncService,
                new CsvSnapshotSecurityDirectoryProvider(true,
                        SecurityDirectoryConstants.PROVIDER_CODE_CSV_SNAPSHOT_DIR, tempDir,
                        new SecurityDirectoryCsvParser()),
                syncStateMapper, stockBasicMapper, directoryService, properties, Clock.systemUTC());
        SecurityDirectoryStatusVO empty = controller.status().getData();
        assertEquals("EMPTY", empty.catalogStatus());
        assertTrue(empty.providerEnabled());
        assertTrue(empty.providerConfigured());

        String rows = """
                SH.603308,应流股份,SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,
                """;
        byte[] csv = (HEADER + rows).getBytes(StandardCharsets.UTF_8);
        directoryService.importCsv(new java.io.ByteArrayInputStream(csv), csv.length);
        SecurityDirectoryStatusVO ready = controller.status().getData();
        assertEquals("READY", ready.catalogStatus());
        assertNotNull(ready.catalogUpdatedAt());
        assertNotEquals("EMPTY", ready.catalogStatus());
    }
}
