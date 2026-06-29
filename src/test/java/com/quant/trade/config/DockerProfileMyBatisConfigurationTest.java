package com.quant.trade.config;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docker 运行配置的 MyBatis 映射加载测试。
 * <p>
 * 健康检查不会执行业务 SQL，因此需要显式验证 Docker profile 已加载全部 XML Mapper，
 * 避免应用能够启动但业务接口因 Invalid bound statement 全部返回 500。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:qta_docker_profile_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@ActiveProfiles("docker")
class DockerProfileMyBatisConfigurationTest {

    private static final List<String> REQUIRED_STATEMENTS = List.of(
            "com.quant.trade.watchlist.dao.WatchlistMapper.selectByFilter",
            "com.quant.trade.tradeplan.dao.TradePlanMapper.selectByFilter",
            "com.quant.trade.journal.dao.TradeJournalMapper.selectByFilter",
            "com.quant.trade.review.dao.ReviewNoteMapper.selectByFilter",
            "com.quant.trade.portfolio.dao.PortfolioPriceMapper.selectAll",
            "com.quant.trade.portfolio.dao.PositionSnapshotMapper.selectByFilter",
            "com.quant.trade.portfolio.dao.PositionSnapshotItemMapper.selectBySnapshotId"
    );

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    /**
     * 每个业务 Mapper 至少验证一个 XML 中定义的 statement。
     */
    @Test
    void shouldLoadAllXmlMapperStatementsWithDockerProfile() {
        Configuration configuration = sqlSessionFactory.getConfiguration();

        REQUIRED_STATEMENTS.forEach(statement -> assertTrue(
                configuration.hasStatement(statement),
                () -> "MyBatis statement was not loaded: " + statement
        ));
    }
}
