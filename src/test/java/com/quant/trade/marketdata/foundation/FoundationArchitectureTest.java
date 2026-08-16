package com.quant.trade.marketdata.foundation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T14：架构静态门禁（契约 AC-01/AC-06）。
 * foundation/dao 不得使用注解 SQL（SQL 只在 XML）；Controller 不注入 Mapper、
 * 只 import dto/vo/service/common 包（不接触 dao/model 持久化类型）；
 * 历史 migration 文件名集合只增不改不删（V1..V24 冻结清单）。
 */
class FoundationArchitectureTest {

    private static final Path MAIN_JAVA = Paths.get("src/main/java/com/quant/trade/marketdata/foundation");
    private static final Path MIGRATIONS = Paths.get("src/main/resources/db/migration");

    /** 冻结的 migration 文件清单（R1 已新增 V25；此后只允许新增 V26+，不得改名/删除既有文件）。 */
    private static final Set<String> FROZEN_MIGRATIONS = Set.of(
            "V1__init_schema.sql",
            "V2__create_today_mvp_tables.sql",
            "V3__add_portfolio_ledger.sql",
            "V4__add_position_snapshot.sql",
            "V5__add_market_data_tables.sql",
            "V6__add_fetched_at_to_daily_bar.sql",
            "V7__add_longport_market_data.sql",
            "V8__add_parent_task_id_to_sync_task.sql",
            "V9__add_sync_scope_lock.sql",
            "V10__add_market_data_workbench.sql",
            "V11__add_market_segment.sql",
            "V12__add_sub_task_id_to_task_item.sql",
            "V13__add_sync_plan_run_claim.sql",
            "V14__add_market_sector_watch.sql",
            "V15__add_market_sector_automatic_collection.sql",
            "V16__add_agent_api_audit_log.sql",
            "V17__add_security_directory.sql",
            "V18__add_security_directory_sync_state.sql",
            "V19__add_sector_analytics_identity_and_readiness.sql",
            "V20__add_sector_analytics_run_and_publication.sql",
            "V21__add_sector_relative_strength_and_rotation.sql",
            "V22__strengthen_sector_analytics_publication_scope.sql",
            "V23__add_mr0_poc_tables.sql",
            "V24__add_data_foundation_tables.sql",
            "V25__data_foundation_backfill_scale_and_lineage.sql");

    private static String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("读取源文件失败: " + path, exception);
        }
    }

    private static List<Path> listJavaFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (IOException exception) {
            throw new IllegalStateException("列目录失败: " + dir, exception);
        }
    }

    @Test
    void daoInterfacesHaveNoAnnotationSql() {
        List<Path> daoFiles = listJavaFiles(MAIN_JAVA.resolve("dao"));
        assertTrue(daoFiles.size() >= 10, "foundation/dao 应有 10+ Mapper 接口，实际 " + daoFiles.size());
        for (Path file : daoFiles) {
            String source = readSource(file);
            List<String> forbidden = Arrays.asList("@Select", "@Insert", "@Update", "@Delete");
            for (String annotation : forbidden) {
                assertFalse(source.contains(annotation),
                        file.getFileName() + " 不得使用注解 SQL（SQL 只能写在 mapper XML）: " + annotation);
            }
            assertTrue(source.contains("@Mapper"), file.getFileName() + " 必须是 MyBatis @Mapper 接口");
        }
    }

    @Test
    void controllerInjectsNoMapperAndImportsOnlyPresentationPackages() throws Exception {
        Class<?> controller = Class.forName(
                "com.quant.trade.marketdata.foundation.controller.DataFoundationController");
        Arrays.stream(controller.getDeclaredFields()).forEach(field -> assertFalse(
                field.getType().getName().endsWith("Mapper"),
                "Controller 不得注入 Mapper: " + field.getType().getName()));

        Path sourceFile = MAIN_JAVA.resolve("controller/DataFoundationController.java");
        assertTrue(Files.exists(sourceFile), "Controller 源文件必须存在: " + sourceFile);
        List<String> projectImports = readSource(sourceFile).lines()
                .filter(line -> line.startsWith("import com.quant.trade."))
                .map(String::trim)
                .toList();
        assertTrue(projectImports.size() >= 10, "Controller 应有 dto/vo/service 导入");
        for (String importLine : projectImports) {
            assertFalse(importLine.contains(".dao.") || importLine.contains(".model."),
                    "Controller 不得 import dao/model 持久化类型: " + importLine);
            assertTrue(importLine.startsWith("import com.quant.trade.common.")
                            || importLine.contains(".dto.")
                            || importLine.contains(".vo.")
                            || importLine.contains(".service."),
                    "Controller 只能 import common/dto/vo/service 包: " + importLine);
        }
    }

    @Test
    void historicalMigrationFileNamesAreFrozen() throws IOException {
        assertTrue(Files.isDirectory(MIGRATIONS), "migration 目录必须存在: " + MIGRATIONS);
        Set<String> actual;
        try (Stream<Path> stream = Files.list(MIGRATIONS)) {
            actual = stream.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .collect(Collectors.toSet());
        }
        assertEquals(FROZEN_MIGRATIONS, actual,
                "migration 文件名集合与冻结清单不一致（只允许新增 V25+，不得改名/删除既有文件）");
    }
}
